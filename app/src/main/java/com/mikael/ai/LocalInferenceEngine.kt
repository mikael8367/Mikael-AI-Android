package com.mikael.ai

object LocalInferenceEngine {
    private val libraryLoaded: Boolean = runCatching {
        System.loadLibrary("mikael_llama")
    }.isSuccess

    @Volatile
    private var ready = false

    fun isAvailable(): Boolean = libraryLoaded

    @Synchronized
    fun load(modelPath: String, contextTokens: Int): Result<Unit> = runCatching {
        check(libraryLoaded) { "O motor nativo não foi incluído nesta compilação." }
        check(modelPath.isNotBlank()) { "Selecione um modelo GGUF." }
        check(contextTokens in 256..8192) { "O contexto precisa ficar entre 256 e 8192 tokens." }
        val nativeError = nativeLoad(
            modelPath,
            contextTokens,
            Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        )
        if (nativeError != null) error(nativeError)
        ready = true
    }

    fun generate(prompt: String, maxTokens: Int = 256): Result<String> = runCatching {
        check(ready) { "Carregue um modelo antes de conversar." }
        require(prompt.isNotBlank()) { "Digite uma mensagem." }
        require(prompt.toByteArray(Charsets.UTF_8).size <= 24000) { "A mensagem está muito longa para o motor local." }
        val result = nativeGenerate(prompt, maxTokens)
        if (result == null) error("O motor local não retornou uma resposta.")
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

    private external fun nativeLoad(modelPath: String, contextTokens: Int, threads: Int): String?
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeCancel()
    private external fun nativeUnload()
}
