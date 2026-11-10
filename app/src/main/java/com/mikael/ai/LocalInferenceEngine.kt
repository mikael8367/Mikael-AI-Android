package com.mikael.ai

/** Motor Transformer autoral. Carrega somente arquivos MKAI versionados; não usa GGUF ou engines externos. */
object LocalInferenceEngine {
    private val libraryLoaded: Boolean = runCatching { System.loadLibrary("mikael_engine") }.isSuccess
    @Volatile private var ready = false

    fun isAvailable(): Boolean = libraryLoaded

    @Synchronized
    fun load(modelPath: String, contextTokens: Int): Result<Unit> = runCatching {
        check(libraryLoaded) { "O motor nativo Mikael não foi incluído nesta compilação." }
        check(modelPath.endsWith(".mkai", ignoreCase = true)) { "O motor próprio aceita somente arquivos .mkai. GGUF não é compatível." }
        check(contextTokens in 16..8192) { "O contexto precisa ficar entre 16 e 8192 tokens." }
        val message = nativeLoad(modelPath, contextTokens, Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        if (message != null) error(message)
        ready = true
    }

    fun generate(prompt: String, maxTokens: Int = 128): Result<String> = runCatching {
        check(ready) { "Carregue um modelo MKAI próprio antes de conversar." }
        require(prompt.isNotBlank()) { "Digite uma mensagem." }
        require(prompt.length <= 24000) { "A mensagem está muito longa." }
        val answer = nativeGenerate(prompt, maxTokens)
        if (answer == null) error("O motor local não retornou uma resposta.")
        answer
    }

    fun cancel() { if (libraryLoaded) nativeCancel() }
    @Synchronized fun unload() { if (libraryLoaded) nativeUnload(); ready = false }

    private external fun nativeLoad(modelPath: String, contextTokens: Int, threads: Int): String?
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeCancel()
    private external fun nativeUnload()
}
