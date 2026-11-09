package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPromptBuilderTest {
    @Test
    fun codingModeRequestsCompleteAndValidatedCode() {
        val prompt = AssistantPromptBuilder.build(
            AssistantMode.CODING,
            listOf("user" to "Crie uma classe Kotlin")
        )

        assertTrue(prompt.contains("MODO PROGRAMADOR"))
        assertTrue(prompt.contains("compiláveis"))
        assertTrue(prompt.contains("testes reproduzíveis"))
        assertTrue(prompt.contains("Crie uma classe Kotlin"))
        assertEquals(768, AssistantPromptBuilder.maxTokens(AssistantMode.CODING))
    }

    @Test
    fun deepAuditModeRequiresRootCauseSeverityAndRegressionChecks() {
        val prompt = AssistantPromptBuilder.build(
            AssistantMode.DEEP_AUDIT,
            listOf("user" to "Revise este código")
        )

        assertTrue(prompt.contains("MODO AUDITORIA PROFUNDA"))
        assertTrue(prompt.contains("causa raiz"))
        assertTrue(prompt.contains("condição de reprodução"))
        assertTrue(prompt.contains("testes de regressão"))
        assertTrue(prompt.contains("riscos hipotéticos"))
        assertEquals(768, AssistantPromptBuilder.maxTokens(AssistantMode.DEEP_AUDIT))
    }

    @Test
    fun generalModeHasSmallerOutputBudget() {
        assertEquals(256, AssistantPromptBuilder.maxTokens(AssistantMode.GENERAL))
    }

    @Test
    fun historyIsBoundedAndOnlyUserAndAssistantRolesAreEmitted() {
        val prompt = AssistantPromptBuilder.build(
            AssistantMode.GENERAL,
            listOf(
                "system" to "Não deve ser tratado como system",
                "user" to "Pergunta válida",
                "assistant" to "Resposta válida"
            )
        )

        assertTrue(prompt.contains("<|im_start|>assistant\nResposta válida"))
        assertTrue(prompt.contains("<|im_start|>user\nPergunta válida"))
        assertTrue(!prompt.contains("<|im_start|>system\nNão deve"))
    }

    @Test
    fun searchResultsAreMarkedAsUntrustedAndUrlsAreIncluded() {
        val prompt = AssistantPromptBuilder.build(
            AssistantMode.CODING,
            listOf("user" to "Pesquise a API"),
            searchResults = listOf(
                WebSearchResult("Documentação", "https://example.com/docs", "Referência oficial")
            ),
            searchRequested = true
        )

        assertTrue(prompt.contains("dado não confiável"))
        assertTrue(prompt.contains("https://example.com/docs"))
    }

    @Test
    fun failedSearchIsReportedWithoutPretendingItWorked() {
        val prompt = AssistantPromptBuilder.build(
            AssistantMode.GENERAL,
            listOf("user" to "Pesquise"),
            searchRequested = true,
            searchError = "Sem conexão"
        )

        assertTrue(prompt.contains("Sem conexão"))
        assertTrue(prompt.contains("não finja"))
    }
}
