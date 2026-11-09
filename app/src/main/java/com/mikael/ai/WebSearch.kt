package com.mikael.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

class WebSearch(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    fun search(query: String): List<WebSearchResult> {
        val normalized = query.trim()
        require(normalized.length in 3..200) { "Digite uma pesquisa entre 3 e 200 caracteres." }

        val url = "https://html.duckduckgo.com/html/?q=" +
            java.net.URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Pesquisa web falhou (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("A pesquisa web retornou uma resposta vazia.")
            if (body.contentLength() > MAX_RESPONSE_BYTES) {
                throw IOException("A resposta da pesquisa ultrapassou o limite de segurança.")
            }
            val html = body.byteStream().use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) throw IOException("A resposta da pesquisa ultrapassou o limite de segurança.")
                    output.write(buffer, 0, count)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
            return parseResults(html)
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

        internal fun parseResults(html: String): List<WebSearchResult> {
            val document = Jsoup.parse(html)
            return document.select(".result").mapNotNull { result ->
                val anchor = result.selectFirst("a.result__a") ?: return@mapNotNull null
                val title = anchor.text().trim().take(180)
                val target = resolveTarget(anchor.attr("href"))
                if (title.isBlank() || target == null) return@mapNotNull null
                val snippet = result.selectFirst(".result__snippet")?.text()?.trim().orEmpty().take(320)
                WebSearchResult(title, target, snippet)
            }.distinctBy { it.url }.take(4)
        }

        private fun resolveTarget(href: String): String? {
            if (href.isBlank()) return null
            val candidate = runCatching {
                val parsed = java.net.URI(href)
                val raw = parsed.rawQuery.orEmpty()
                val uddg = raw.split("&").firstOrNull { it.startsWith("uddg=") }
                    ?.substringAfter("uddg=")
                if (uddg != null) URLDecoder.decode(uddg, StandardCharsets.UTF_8.name()) else href
            }.getOrDefault(href)
            return runCatching {
                val uri = java.net.URI(candidate)
                candidate.takeIf { uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() }
            }.getOrNull()
        }
    }
}
