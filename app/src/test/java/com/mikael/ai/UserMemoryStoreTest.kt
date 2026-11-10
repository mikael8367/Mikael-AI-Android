package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryStoreTest {
    @Test
    fun rememberCommandAcceptsPortugueseForms() {
        assertEquals("Prefiro português do Brasil", UserMemoryStore.parseRememberCommand("Lembre: Prefiro português do Brasil"))
        assertEquals("uso Kotlin", UserMemoryStore.parseRememberCommand("Guarde que uso Kotlin"))
    }

    @Test
    fun forgetCommandParsesTarget() {
        assertEquals("uso Kotlin", UserMemoryStore.parseForgetCommand("Esqueça: uso Kotlin"))
        assertNull(UserMemoryStore.parseForgetCommand("Olá, tudo bem?"))
    }

    @Test
    fun topicsAreClassifiedInPortuguese() {
        assertEquals("Minecraft", UserMemoryStore.classifyTopic("Estou criando um mod Forge para Minecraft"))
        assertEquals("Programação", UserMemoryStore.classifyTopic("Prefiro desenvolver aplicativos Android em Kotlin"))
        assertEquals("Preferências", UserMemoryStore.classifyTopic("Prefiro respostas em português"))
    }

    @Test
    fun normalizationIgnoresAccentsCaseAndPunctuation() {
        assertEquals(
            UserMemoryStore.normalize("Prefiro Português!"),
            UserMemoryStore.normalize("prefiro portugues")
        )
    }

    @Test
    fun genericModelWordIsNotMisclassifiedAsMinecraftMod() {
        assertEquals("Outros", UserMemoryStore.classifyTopic("Modelo local pequeno"))
    }

    @Test
    fun differentShortTechnicalFactsAreNotNearDuplicates() {
        val first = UserMemoryStore.normalize("Tenho PC")
        val second = UserMemoryStore.normalize("Tenho celular")
        assertTrue(UserMemoryStore.tokenSimilarity(first, second) < 0.75)
    }

    @Test
    fun similarFactsAreDetectedAsDuplicates() {
        val first = UserMemoryStore.normalize("Prefiro respostas em português")
        val second = UserMemoryStore.normalize("Prefiro respostas em português Brasil")
        assertTrue(UserMemoryStore.tokenSimilarity(first, second) >= 0.75)
    }
    @Test
    fun automaticLearningOnlyKeepsExplicitStableNonSensitiveFacts() {
        assertEquals(
            "Prefiro respostas em português do Brasil",
            UserMemoryStore.autoLearningCandidate("Prefiro respostas em português do Brasil")
        )
        assertNull(UserMemoryStore.autoLearningCandidate("Minha senha é exemplo-seguro"))
        assertNull(UserMemoryStore.autoLearningCandidate("Hoje está fazendo calor"))
        assertNull(UserMemoryStore.autoLearningCandidate("Meu diagnóstico é ansiedade"))
        assertNull(UserMemoryStore.autoLearningCandidate("Meu projeto usa contato teste@example.com"))
        assertNull(UserMemoryStore.autoLearningCandidate("Meu aparelho tem identificador 12345678901"))
    }

}
