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
            append("Você é Mikael AI. Responda em português do Brasil. Seja preciso; não invente fatos nem diga que testou sem evidência.\n")
            when (mode) {
                AssistantMode.GENERAL -> append(
                    "Explique com clareza. Para código, priorize correção e compatibilidade.\n"
                )
                AssistantMode.CODING -> append(
                    "MODO PROGRAMADOR: entregue códigos completos e compiláveis; considere compatibilidade, erros, segurança, desempenho e testes reproduzíveis.\n"
                )
                AssistantMode.DEEP_AUDIT -> append(
                    "MODO AUDITORIA PROFUNDA: audite lógica, estados, concorrência, ciclo de vida, limites, segurança e desempenho. Separe bugs confirmados de riscos hipotéticos; informe severidade, causa raiz, condição de reprodução, impacto e testes de regressão.\n"
                )
            }
            append("<|im_end|>\n")
        }

        val maxBytes = minOf(MAX_PROMPT_BYTES, contextTokens.coerceIn(256, 8192) * 2)
        val normalized = history.filter { it.first == "user" || it.first == "assistant" }
        val latest = normalized.lastOrNull()
        val prior = normalized.dropLast(1)
        val latestRole = latest?.first ?: "assistant"
        val currentPrefix = if (latest == null) {
            "<|im_start|>assistant\n"
        } else {
            "<|im_start|>$latestRole\n"
        }
        val ending = if (latest == null) "" else "\n<|im_end|>\n<|im_start|>assistant\n"

        // Keep a previous turn only as a complete user/assistant pair.
        val lastPair = prior.takeLast(2)
        val previousCandidate = if (
            lastPair.size == 2 &&
            lastPair[0].first == "user" &&
            lastPair[1].first == "assistant"
        ) {
            buildString {
                lastPair.forEach { (role, message) ->
                    append("<|im_start|>$role\n")
                    append(message)
                    append("\n<|im_end|>\n")
                }
            }
        } else {
            ""
        }

        val fixedWithHistory = systemPrompt + previousCandidate + currentPrefix + ending
        val minimumCurrentBytes = minOf(48, latest?.second?.toByteArray(Charsets.UTF_8)?.size ?: 0)
        val previousPart = if (
            previousCandidate.isNotEmpty() &&
            fixedWithHistory.toByteArray(Charsets.UTF_8).size + minimumCurrentBytes <= maxBytes
        ) previousCandidate else ""

        val fixed = systemPrompt + previousPart + currentPrefix + ending
        val remaining = (maxBytes - fixed.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)
        val currentText = latest?.second?.let { takeUtf8Suffix(it, remaining) }.orEmpty()

        // System instructions and ChatML markers are never byte-truncated.
        // The current user message keeps its ending, where the actual request
        // or final instruction commonly appears.
        return systemPrompt + previousPart + currentPrefix + currentText + ending
    }

    private fun takeUtf8Suffix(value: String, maxBytes: Int): String {
        if (maxBytes <= 0 || value.isEmpty()) return ""
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        var bytes = 0
        var index = value.length
        while (index > 0) {
            val codePoint = value.codePointBefore(index)
            val encodedBytes = when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            if (bytes + encodedBytes > maxBytes) break
            bytes += encodedBytes
            index -= Character.charCount(codePoint)
        }
        return value.substring(index)
    }

    private const val MAX_PROMPT_BYTES = 22000
}
