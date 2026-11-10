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
    @Test
    fun detectsLocalCalculatorAndDateTools() {
        assertEquals(ConnectorKind.CALCULATOR, ConnectorRouter.detect("Calcule: 12 * (3 + 2)")?.kind)
        assertEquals(ConnectorKind.DATE_TIME, ConnectorRouter.detect("Que horas são agora?")?.kind)
    }

    @Test
    fun detectsGithubFileCodeIssuesAndRepositoryTools() {
        assertEquals(
            ConnectorKind.GITHUB_FILE,
            ConnectorRouter.detect("Leia o arquivo app/src/main/AndroidManifest.xml do GitHub mikael8367/Mikael-AI")?.kind
        )
        assertEquals(
            ConnectorKind.GITHUB_CODE_SEARCH,
            ConnectorRouter.detect("Procure no código do repositório mikael8367/Mikael-AI por ConnectorRouter")?.kind
        )
        assertEquals(
            ConnectorKind.GITHUB_ISSUES,
            ConnectorRouter.detect("Mostre issues abertas do GitHub mikael8367/Mikael-AI")?.kind
        )
        assertEquals(
            ConnectorKind.GITHUB_REPOSITORIES,
            ConnectorRouter.detect("Liste meus repositórios no GitHub")?.kind
        )
    }

    @Test
    fun calculatorRejectsCodeAndDivisionByZero() {
        org.junit.Assert.assertEquals("14", SafeCalculator.evaluate("2 + 3 * 4"))
        org.junit.Assert.assertEquals("2", SafeCalculator.evaluate("(8 / 2) - 2"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SafeCalculator.evaluate("1 / 0")
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SafeCalculator.evaluate("1 + Runtime.getRuntime()")
        }
    }

}
