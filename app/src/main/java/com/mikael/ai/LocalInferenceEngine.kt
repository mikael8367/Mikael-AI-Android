package com.mikael.ai

import java.util.concurrent.atomic.AtomicBoolean

/** Motor Transformer autoral. Carrega somente arquivos MKAI versionados; não usa GGUF ou engines externos. */
object LocalInferenceEngine {
    private val libraryLoaded: Boolean = runCatching { System.loadLibrary("mikael_engine") }.isSuccess
    @Volatile private var ready = false
    @Volatile private var loadedContextTokens = 0
    private val cancelRequested = AtomicBoolean(false)

    /** Contexto efetivo em bytes/tokens do MKAI v1 (um byte por ID). */
    fun contextCapacityTokens(): Int = if (ready) loadedContextTokens else 0

    fun isAvailable(): Boolean = libraryLoaded

    @Synchronized
    fun load(modelPath: String, contextTokens: Int): Result<Unit> = runCatching {
        check(libraryLoaded) { "O motor nativo Mikael não foi incluído nesta compilação." }
        check(modelPath.endsWith(".mkai", ignoreCase = true)) { "O motor próprio aceita somente arquivos .mkai. GGUF não é compatível." }
        check(contextTokens in 16..8192) { "O contexto precisa ficar entre 16 e 8192 tokens." }
        val message = nativeLoad(modelPath, contextTokens, Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        if (message != null) error(message)
        loadedContextTokens = nativeContextCapacity().also { capacity ->
            check(capacity >= 16) { "O motor não informou um contexto válido após carregar o modelo." }
        }
        ready = true
    }

    fun generate(prompt: String, maxTokens: Int = 128): Result<String> = runCatching {
        check(ready) { "Carregue um modelo MKAI próprio antes de conversar." }
        require(prompt.isNotBlank()) { "Digite uma mensagem." }
        require(prompt.toByteArray(Charsets.UTF_8).size <= 24000) { "A mensagem está muito longa em UTF-8." }
        cancelRequested.set(false)
        nativeResetCancel()
        check(!cancelRequested.get()) { "Geração cancelada pelo usuário." }
        val answer = nativeGenerate(prompt, maxTokens)
        check(!cancelRequested.get()) { "Geração cancelada pelo usuário." }
        if (answer == null) error("O motor local não retornou uma resposta.")
        answer
    }

    fun cancel() { cancelRequested.set(true); if (libraryLoaded) nativeCancel() }
    @Synchronized fun unload() {
        cancel()
        if (libraryLoaded) nativeUnload()
        ready = false
        loadedContextTokens = 0
    }

    private external fun nativeLoad(modelPath: String, contextTokens: Int, threads: Int): String?
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeResetCancel()
    private external fun nativeContextCapacity(): Int
    private external fun nativeCancel()
    private external fun nativeUnload()
}
