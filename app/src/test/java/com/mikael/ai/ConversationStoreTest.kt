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
}
