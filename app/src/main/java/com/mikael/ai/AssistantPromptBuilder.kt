package com.mikael.ai

enum class AssistantMode(val label: String) {
    GENERAL("Normal"),
    CODING("Programar"),
    DEEP_AUDIT("Auditoria profunda")
}

object AssistantPromptBuilder {
    fun maxTokens(mode: AssistantMode, contextTokens: Int = 512): Int {
        val requested = when (mode) {
            AssistantMode.GENERAL -> 48
            AssistantMode.CODING -> 96
            AssistantMode.DEEP_AUDIT -> 128
        }
        val safeContext = contextTokens.coerceIn(256, 8192)
        return minOf(requested, (safeContext / 2).coerceAtLeast(32))
    }

    fun build(
        mode: AssistantMode,
        history: List<Pair<String, String>>,
        contextTokens: Int = 512
    ): String {
        val systemPrompt = buildString {
            append("<|im_start|>system\n")
            append("Você é Mikael AI, assistente técnico em português do Brasil. Seja direto e preciso; não invente APIs, arquivos, logs ou testes.\n")
            when (mode) {
                AssistantMode.GENERAL -> append(
                    "Explique com clareza. Para código, priorize correção, legibilidade e compatibilidade.\n"
                )
                AssistantMode.CODING -> append(
                    "MODO PROGRAMADOR: aja como engenheiro de software. Entregue códigos completos e compiláveis, com imports e dependências. " +
                        "Considere erros, segurança, desempenho e memória. Inclua testes reproduzíveis e validação quando fizer sentido. " +
                        "Nunca diga que executou algo sem evidência.\n"
                )
                AssistantMode.DEEP_AUDIT -> append(
                    "MODO AUDITORIA PROFUNDA: examine lógica, estados, concorrência, ciclo de vida, entradas inválidas, limites, " +
                        "compatibilidade, segurança, privacidade, persistência, desempenho, memória e regressões. " +
                        "Para cada achado informe severidade, localização, condição de reprodução, causa raiz, impacto e correção. " +
                        "Inclua testes de regressão e separe bugs confirmados de riscos hipotéticos.\n"
                )
            }
            append("<|im_end|>\n")
        }

        val maxBytes = minOf(MAX_PROMPT_BYTES, (contextTokens.coerceIn(256, 8192) * 2))
        val latest = history.lastOrNull()
        val previous = history.dropLast(1).takeLast(1)
        var historyBudget = minOf(240, maxBytes / 8)

        while (true) {
            val previousCandidate = buildString {
                previous.forEach { (role, message) ->
                    val safeRole = if (role == "user") "user" else "assistant"
                    append("<|im_start|>$safeRole\n")
                    append(message.take(160))
                    append("\n<|im_end|>\n")
                }
            }
            // Never cut a ChatML turn in the middle: either keep the whole prior
            // turn or omit it and preserve the current user message.
            val previousPart = if (
                previousCandidate.toByteArray(Charsets.UTF_8).size <= historyBudget
            ) previousCandidate else ""

            val latestRole = if (latest?.first == "user") "user" else "assistant"
            val currentPrefix = if (latest == null) "" else "<|im_start|>$latestRole\n"
            val ending = if (latest == null) "<|im_start|>assistant\n"
                else "\n<|im_end|>\n<|im_start|>assistant\n"
            val fixed = systemPrompt + previousPart + currentPrefix + ending
            val remaining = maxBytes - fixed.toByteArray(Charsets.UTF_8).size

            if (remaining >= 0) {
                val currentText = latest?.second?.let { takeUtf8Prefix(it, remaining) }.orEmpty()
                return systemPrompt + previousPart + currentPrefix + currentText + ending
            }

            if (historyBudget > 0) {
                historyBudget = 0
            } else {
                return takeUtf8Prefix(
                    systemPrompt,
                    maxBytes - "<|im_start|>assistant\n".toByteArray(Charsets.UTF_8).size
                ) + "<|im_start|>assistant\n"
            }
        }
    }

    private fun takeUtf8Prefix(value: String, maxBytes: Int): String {
        if (maxBytes <= 0 || value.isEmpty()) return ""
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        var bytes = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val encodedBytes = when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            if (bytes + encodedBytes > maxBytes) break
            bytes += encodedBytes
            index += Character.charCount(codePoint)
        }
        return value.substring(0, index)
    }

    private const val MAX_PROMPT_BYTES = 22000
}
