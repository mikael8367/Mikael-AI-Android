package com.mikael.ai

import kotlinx.coroutines.CancellationException

object LocalInferenceEngine {
    private val libraryLoaded: Boolean = runCatching {
        System.loadLibrary("mikael_llama")
    }.isSuccess

    @Volatile
    private var ready = false

    @Volatile
    private var diagnosticSink: ((String, String) -> Unit)? = null

    fun setDiagnosticSink(sink: ((String, String) -> Unit)?) {
        diagnosticSink = sink
    }

    /** JNI callback for native engine stages and error codes. */
    @JvmStatic
    fun onNativeLog(level: String, message: String) {
        runCatching { diagnosticSink?.invoke(level, message) }
    }

    fun isAvailable(): Boolean = libraryLoaded

    @Synchronized
    fun load(modelPath: String, contextTokens: Int): Result<Unit> = runCatching {
        check(libraryLoaded) { "O motor nativo não foi incluído nesta compilação." }
        check(modelPath.isNotBlank()) { "Selecione um modelo GGUF." }
        check(contextTokens in 256..8192) { "O contexto precisa ficar entre 256 e 8192 tokens." }
        ready = false
        val nativeError = nativeLoad(
            modelPath,
            contextTokens,
            Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
        )
        if (nativeError != null) error(unwrapNativeError(nativeError))
        ready = true
    }

    @Synchronized
    fun generate(prompt: String, maxTokens: Int = 256): Result<String> = runCatching {
        check(ready) { "Carregue um modelo antes de conversar." }
        require(prompt.isNotBlank()) { "Digite uma mensagem." }
        require(prompt.toByteArray(Charsets.UTF_8).size <= 24000) { "A mensagem está muito longa para o motor local." }
        val result = nativeGenerate(prompt, maxTokens.coerceIn(16, 256))
        if (result == null) error("E_NATIVE_NULL • O motor local não retornou uma resposta.")
        if (result.startsWith(NATIVE_CANCELLED_PREFIX)) throw CancellationException("Geração interrompida pelo usuário.")
        if (result.startsWith(NATIVE_ERROR_PREFIX)) error(result.removePrefix(NATIVE_ERROR_PREFIX))
        result
    }

    fun cancel() {
        if (libraryLoaded) nativeCancel()
    }

    @Synchronized
    fun unload() {
        if (libraryLoaded) nativeUnload()
        ready = false
    }

    private fun unwrapNativeError(value: String): String =
        if (value.startsWith(NATIVE_ERROR_PREFIX)) value.removePrefix(NATIVE_ERROR_PREFIX) else value

    private const val NATIVE_ERROR_PREFIX = "MIKAEL_NATIVE_ERROR:"
    private const val NATIVE_CANCELLED_PREFIX = "MIKAEL_NATIVE_CANCELLED:"

    private external fun nativeLoad(modelPath: String, contextTokens: Int, threads: Int): String?
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeCancel()
    private external fun nativeUnload()
}
