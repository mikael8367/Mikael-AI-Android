package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectorRouterTest {
    @Test
    fun detectsExplicitWebSearchRequests() {
        val proposal = ConnectorRouter.detect("Pesquise na web a documentação atual do Kotlin")
        assertEquals(ConnectorKind.WEB_SEARCH, proposal?.kind)
        assertEquals("Pesquise na web a documentação atual do Kotlin", proposal?.query)
    }

    @Test
    fun detectsExplicitGithubRepositoryRequests() {
        val proposal = ConnectorRouter.detect("Analise o README do GitHub mikael8367/Mikael-AI")
        assertEquals(ConnectorKind.GITHUB_README, proposal?.kind)
        assertEquals("mikael8367/Mikael-AI", proposal?.query)
    }

    @Test
    fun doesNotInvokeConnectorsForOrdinaryQuestions() {
        assertNull(ConnectorRouter.detect("Explique o que é uma variável em Kotlin"))
    }

    @Test
    fun doesNotAcceptRepositoryWithoutGithubIntent() {
        assertNull(ConnectorRouter.detect("Crie um projeto com nome mikael8367/Mikael-AI"))
    }
}
