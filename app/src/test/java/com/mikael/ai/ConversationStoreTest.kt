package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationStoreTest {
    @Test
    fun messageModelKeepsRoleAndText() {
        val message = ChatMessage(42L, "user", "Explique Kotlin")
        assertEquals("user", message.role)
        assertEquals("Explique Kotlin", message.text)
    }
    @Test
    fun priorConversationContextIsSharedButSecretsAndActiveChatAreExcluded() {
        val prior = ChatConversation(
            "old", "Projeto", 20L,
            listOf(
                ChatMessage(1L, "user", "Estou desenvolvendo um app Android em Kotlin"),
                ChatMessage(2L, "user", "Minha senha é abc123")
            )
        )
        val active = ChatConversation(
            "active", "Atual", 30L,
            listOf(ChatMessage(3L, "user", "Mensagem da conversa atual"))
        )
        val context = ConversationStore.contextFromOtherConversations(listOf(prior, active), "active")
        org.junit.Assert.assertTrue(context.contains("app Android em Kotlin"))
        org.junit.Assert.assertFalse(context.contains("abc123"))
        org.junit.Assert.assertFalse(context.contains("Mensagem da conversa atual"))
    }

}
