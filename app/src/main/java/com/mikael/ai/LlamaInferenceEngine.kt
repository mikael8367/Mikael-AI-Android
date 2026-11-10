package com.mikael.ai

import java.util.concurrent.atomic.AtomicBoolean

/** Runtime GGUF baseado no llama.cpp. Mantido separado do motor autoral MKAI. */
object LlamaInferenceEngine {
    private val libraryLoaded = runCatching { System.loadLibrary("mikael_llama") }.isSuccess
    @Volatile private var ready = false
    @Volatile private var loadedContext = 0
    private val cancelled = AtomicBoolean(false)

    fun isAvailable(): Boolean = libraryLoaded
    fun contextCapacityTokens(): Int = if (ready) loadedContext else 0

    @Synchronized
    fun load(path: String, contextTokens: Int): Result<Unit> = runCatching {
        check(libraryLoaded) { "O runtime llama.cpp não foi incluído nesta compilação." }
        check(path.endsWith(".gguf", ignoreCase = true)) { "Selecione um arquivo GGUF." }
        check(contextTokens in 128..2048) { "O contexto GGUF deve ficar entre 128 e 2048." }
        unload()
        val error = nativeLoad(path, contextTokens, Runtime.getRuntime().availableProcessors().coerceIn(1, 3))
        if (error != null) throw IllegalStateException(error)
        loadedContext = nativeContextCapacity()
        check(loadedContext >= 128) { "O runtime não retornou um contexto válido." }
        ready = true
    }

    fun generate(prompt: String, maxTokens: Int = 128): Result<String> = runCatching {
        check(ready) { "Carregue um modelo GGUF antes de conversar." }
        require(prompt.isNotBlank()) { "Digite uma mensagem." }
        require(prompt.toByteArray(Charsets.UTF_8).size <= 24000) { "A mensagem está muito longa." }
        cancelled.set(false)
        nativeResetCancel()
        check(!cancelled.get()) { "Geração cancelada pelo usuário." }
        val answer = nativeGenerate(prompt, maxTokens)
        check(!cancelled.get()) { "Geração cancelada pelo usuário." }
        answer ?: error("O llama.cpp não retornou uma resposta.")
    }

    fun cancel() { cancelled.set(true); if (libraryLoaded) nativeCancel() }

    @Synchronized
    fun unload() {
        cancel()
        if (libraryLoaded) nativeUnload()
        ready = false
        loadedContext = 0
    }

    private external fun nativeLoad(path: String, contextTokens: Int, threads: Int): String?
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeContextCapacity(): Int
    private external fun nativeResetCancel()
    private external fun nativeCancel()
    private external fun nativeUnload()
}
