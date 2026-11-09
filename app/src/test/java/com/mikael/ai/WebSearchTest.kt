package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {
    @Test
    fun detectsExplicitWebSearchRequestsInPortuguese() {
        assertTrue(WebSearch.requestsSearch("Pesquise na web as novidades do Kotlin"))
        assertTrue(WebSearch.requestsSearch("Procure na internet a documentação do Android"))
        assertTrue(WebSearch.requestsSearch("Buscar online a versão atual do Java"))
    }

    @Test
    fun doesNotSearchForOrdinaryProgrammingQuestions() {
        assertTrue(!WebSearch.requestsSearch("Como criar uma classe Java?"))
        assertTrue(!WebSearch.requestsSearch("Explique uma função em Kotlin"))
    }

    @Test
    fun searchQueryMustBeWithinSafeLengthBounds() {
        assertTrue(runCatching { WebSearch().search("ab") }.isFailure)
        assertTrue(runCatching { WebSearch().search("x".repeat(201)) }.isFailure)
    }

    @Test
    fun parsesDuckDuckGoResultsAndResolvesRedirects() {
        val html = """
            <div class="result">
              <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fdeveloper.android.com%2Fguide&rut=abc">Android Developers</a>
              <a class="result__snippet">Official Android documentation.</a>
            </div>
            <div class="result">
              <a class="result__a" href="https://kotlinlang.org/docs/home.html">Kotlin Docs</a>
              <a class="result__snippet">Kotlin language documentation.</a>
            </div>
        """.trimIndent()

        val results = WebSearch.parseResults(html)

        assertEquals(2, results.size)
        assertEquals("https://developer.android.com/guide", results[0].url)
        assertEquals("Android Developers", results[0].title)
        assertTrue(results[0].snippet.contains("Official Android"))
    }

    @Test
    fun ignoresUnsafeAndInvalidResultUrls() {
        val html = """
            <div class="result">
              <a class="result__a" href="javascript:alert(1)">Unsafe result</a>
            </div>
            <div class="result">
              <a class="result__a" href="http://example.com/insecure">Insecure result</a>
            </div>
            <div class="result">
              <a class="result__a" href="https://example.com/safe">Safe result</a>
            </div>
        """.trimIndent()

        val results = WebSearch.parseResults(html)

        assertEquals(1, results.size)
        assertEquals("https://example.com/safe", results.single().url)
    }

    @Test
    fun removesDuplicateUrlsAndLimitsResults() {
        val html = (1..7).joinToString("") { index ->
            """<div class="result"><a class="result__a" href="https://example.com/$index">Result $index</a></div>"""
        } + """<div class="result"><a class="result__a" href="https://example.com/1">Duplicate</a></div>"""

        val results = WebSearch.parseResults(html)

        assertEquals(4, results.size)
        assertEquals(4, results.map { it.url }.distinct().size)
    }
}
