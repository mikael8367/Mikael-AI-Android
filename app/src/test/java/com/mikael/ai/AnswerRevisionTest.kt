package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerRevisionTest {
    @Test
    fun onlyExplicitReviewDecisionRequestsRevision() {
        assertTrue(AnswerRevision.shouldRevise("REVISAR: o código não compila"))
        assertTrue(AnswerRevision.shouldRevise("revisar - requisito ignorado"))
        assertFalse(AnswerRevision.shouldRevise("MANTER: resposta correta"))
        assertFalse(AnswerRevision.shouldRevise("Talvez seja melhor revisar"))
        assertFalse(AnswerRevision.shouldRevise(""))
    }

    @Test
    fun keepsDraftWhenSelfReviewApprovesIt() {
        val outputs = ArrayDeque(listOf("rascunho correto", "MANTER"))
        var calls = 0
        val result = AnswerRevision.generate("pedido", "prompt da conversa", 2048) { _, _ ->
            calls++
            Result.success(outputs.removeFirst())
        }
        assertEquals("rascunho correto", result.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun generatesReplacementOnlyWhenReviewRequestsIt() {
        val outputs = ArrayDeque(listOf("rascunho com erro", "REVISAR: faltou validar entrada", "resposta corrigida"))
        var calls = 0
        val result = AnswerRevision.generate("pedido", "prompt da conversa", 2048) { _, _ ->
            calls++
            Result.success(outputs.removeFirst())
        }
        assertEquals("resposta corrigida", result.getOrThrow())
        assertEquals(3, calls)
    }

    @Test
    fun reviewFailurePreservesOriginalDraft() {
        var calls = 0
        val result = AnswerRevision.generate("pedido", "prompt da conversa", 2048) { _, _ ->
            calls++
            if (calls == 1) Result.success("rascunho utilizável")
            else Result.failure(IllegalStateException("falha simulada"))
        }
        assertEquals("rascunho utilizável", result.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun failedRevisionFallsBackToOriginalDraft() {
        var calls = 0
        val result = AnswerRevision.generate("pedido", "prompt da conversa", 2048) { _, _ ->
            calls++
            when (calls) {
                1 -> Result.success("rascunho inicial")
                2 -> Result.success("REVISAR: existe um erro")
                else -> Result.failure(IllegalStateException("falha simulada"))
            }
        }
        assertEquals("rascunho inicial", result.getOrThrow())
        assertEquals(3, calls)
    }

    @Test
    fun smallContextSkipsExtraReviewCalls() {
        var calls = 0
        val result = AnswerRevision.generate("pedido", "prompt", 128) { _, _ ->
            calls++
            Result.success("resposta")
        }
        assertEquals("resposta", result.getOrThrow())
        assertEquals(1, calls)
    }

    @Test
    fun blankReplacementDoesNotEraseDraft() {
        val outputs = ArrayDeque(listOf("rascunho inicial", "REVISAR: corrigir", "   "))
        val result = AnswerRevision.generate("pedido", "prompt", 2048) { _, _ ->
            Result.success(outputs.removeFirst())
        }
        assertEquals("rascunho inicial", result.getOrThrow())
    }
}
