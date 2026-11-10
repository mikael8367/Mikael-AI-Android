package com.mikael.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPromptTest {
    @Test
    fun promptNeverExceedsAvailableContextAndKeepsLatestRequest() {
        val messages = listOf(
            ChatMessage(1, "user", "A".repeat(500)),
            ChatMessage(2, "assistant", "B".repeat(500)),
            ChatMessage(3, "user", "Corrija este erro em Kotlin")
        )
        val prompt = ConversationPrompt.build(messages, "", 512)
        assertTrue(prompt.toByteArray(Charsets.UTF_8).size < 512)
        assertTrue(prompt.contains("Corrija este erro em Kotlin"))
        assertTrue(prompt.contains("Você é Mikael AI"))
    }

    @Test
    fun approvedLocalMemoryIsIncludedAsContext() {
        val prompt = ConversationPrompt.build(
            listOf(ChatMessage(1, "user", "Qual idioma prefiro?")),
            "",
            1024,
            userMemory = "- Prefiro português do Brasil\\n"
        )
        assertTrue(prompt.contains("Memórias locais fornecidas pelo usuário"))
        assertTrue(prompt.contains("Prefiro português do Brasil"))
        assertTrue(prompt.contains("Qual idioma prefiro?"))
    }

    @Test
    fun oversizedGithubContextDoesNotPushPromptOverLimit() {
        val prompt = ConversationPrompt.build(
            listOf(ChatMessage(1, "user", "Explique este código")),
            "contexto".repeat(1000),
            1024
        )
        assertTrue(prompt.toByteArray(Charsets.UTF_8).size < 1024)
        assertTrue(prompt.contains("Explique este código"))
    }

    @Test
    fun portugueseCharactersAreNotSplitWhenTruncated() {
        val prompt = ConversationPrompt.build(
            listOf(ChatMessage(1, "user", "ação ".repeat(1000))),
            "",
            512
        )
        assertTrue(prompt.toByteArray(Charsets.UTF_8).size < 512)
        assertTrue(!prompt.endsWith("\uFFFD"))
    }
}
