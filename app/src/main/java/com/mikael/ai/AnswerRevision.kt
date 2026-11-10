package com.mikael.ai

/**
 * Revisão autônoma e invisível da resposta.
 *
 * O modelo primeiro prepara um rascunho, depois avalia se existe um problema concreto.
 * Só solicita uma segunda versão quando a avaliação escolhe REVISAR. A etapa de revisão
 * nunca é adicionada ao histórico nem exibida na interface.
 *
 * Em caso de falha/ambiguidade na revisão, preserva o rascunho original.
 */
internal object AnswerRevision {
    private const val MIN_REVIEW_CONTEXT_BYTES = 512
    private const val REVIEW_MAX_TOKENS = 32
    private const val REVISION_MAX_TOKENS = 128

    fun generate(
        originalRequest: String,
        conversationPrompt: String,
        contextCapacityBytes: Int,
        maxAnswerTokens: Int = 256,
        generate: (String, Int) -> Result<String>
    ): Result<String> {
        val draft = generate(conversationPrompt, maxAnswerTokens).getOrThrow()
        if (draft.isBlank() || contextCapacityBytes < MIN_REVIEW_CONTEXT_BYTES) {
            return Result.success(draft)
        }

        val reviewPrompt = buildReviewPrompt(
            originalRequest = originalRequest,
            draft = draft,
            contextCapacityBytes = contextCapacityBytes
        ) ?: return Result.success(draft)

        // Falha ou resposta ambígua na autocrítica não deve apagar uma resposta já gerada.
        val review = generate(reviewPrompt, REVIEW_MAX_TOKENS).getOrNull()
            ?: return Result.success(draft)
        if (!shouldRevise(review)) return Result.success(draft)

        val revisionPrompt = buildRevisionPrompt(
            originalRequest = originalRequest,
            draft = draft,
            review = review,
            contextCapacityBytes = contextCapacityBytes
        ) ?: return Result.success(draft)

        return generate(revisionPrompt, REVISION_MAX_TOKENS)
            .map { revised -> if (revised.isBlank()) draft else revised }
            .recoverCatching { draft }
    }

    internal fun shouldRevise(review: String): Boolean {
        val firstLine = review.lineSequence().firstOrNull()?.trim().orEmpty()
            .removePrefix("\uFEFF")
            .removePrefix("**")
            .trim()
        return Regex("^REVISAR(?:\\b|\\s*[:：-])", RegexOption.IGNORE_CASE)
            .containsMatchIn(firstLine)
    }

    internal fun buildReviewPrompt(
        originalRequest: String,
        draft: String,
        contextCapacityBytes: Int
    ): String? {
        val header = """
            Verifique o pedido e o rascunho. Só escolha REVISAR se encontrar erro concreto,
            código incorreto, contradição ou requisito ignorado. Na dúvida, escolha MANTER.
            Comece a resposta com MANTER ou REVISAR. Não fale com o usuário nesta etapa.
            PEDIDO:
        """.trimIndent()
        val tail = "\n\nRASCUNHO A VERIFICAR:\n\n"
        val budget = contextCapacityBytes - REVIEW_MAX_TOKENS - 1
        val available = budget - header.toByteArray(Charsets.UTF_8).size -
            tail.toByteArray(Charsets.UTF_8).size
        if (available < 128) return null
        val requestBudget = (available / 3).coerceAtLeast(64)
        val draftBudget = available - requestBudget
        return header + fitUtf8(originalRequest, requestBudget) + tail + fitUtf8(draft, draftBudget)
    }

    internal fun buildRevisionPrompt(
        originalRequest: String,
        draft: String,
        review: String,
        contextCapacityBytes: Int
    ): String? {
        val header = """
            Corrija os problemas indicados, preserve o que estiver correto e cumpra o pedido.
            Entregue somente a resposta final, sem mencionar esta revisão.
            PEDIDO:
        """.trimIndent()
        val draftLabel = "\n\nRASCUNHO:\n\n"
        val reviewLabel = "\n\nPROBLEMAS IDENTIFICADOS:\n\n"
        val fixedBytes = header.toByteArray(Charsets.UTF_8).size +
            draftLabel.toByteArray(Charsets.UTF_8).size +
            reviewLabel.toByteArray(Charsets.UTF_8).size
        val available = contextCapacityBytes - REVISION_MAX_TOKENS - 1 - fixedBytes
        if (available < 128) return null
        val requestBudget = (available / 4).coerceAtLeast(48)
        val reviewBudget = (available / 4).coerceAtLeast(48)
        val draftBudget = available - requestBudget - reviewBudget
        if (draftBudget < 32) return null
        return header + fitUtf8(originalRequest, requestBudget) +
            draftLabel + fitUtf8(draft, draftBudget) +
            reviewLabel + fitUtf8(review, reviewBudget)
    }

    /** Limita bytes UTF-8 sem cortar pares substitutos ou sequências multibyte. */
    private fun fitUtf8(value: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        val result = StringBuilder()
        var used = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val piece = String(Character.toChars(codePoint))
            val size = piece.toByteArray(Charsets.UTF_8).size
            if (used + size > maxBytes) break
            result.append(piece)
            used += size
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }
}
