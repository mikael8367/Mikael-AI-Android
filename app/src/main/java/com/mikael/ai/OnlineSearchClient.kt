package com.mikael.ai

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class WebSearchSource(val title: String, val url: String)
data class WebSearchResult(
    val query: String,
    val summary: String,
    val sources: List<WebSearchSource>,
    val provider: String
)

object OnlineSearchClient {
    private const val BASE_URL = "https://mikael-ai.onrender.com"
    private const val PREFS = "mikael_online_search_session"
    private const val KEY_COOKIE = "encrypted_session_cookie"

    fun hasSession(context: Context): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COOKIE, null)
            ?: return false
        return runCatching { SecureLocalStorage.decrypt(stored).startsWith("mikael_session=") }.getOrDefault(false)
    }

    suspend fun login(context: Context, password: String) = withContext(Dispatchers.IO) {
        require(password.isNotBlank() && password.length <= 256) { "Informe a senha do serviço Mikael AI." }
        val result = request(
            path = "/api/login",
            method = "POST",
            body = JSONObject().put("password", password).toString()
        )
        if (result.status !in 200..299) throw IllegalStateException(errorMessage(result.body, result.status))
        val cookie = result.setCookie?.substringBefore(";")?.trim()
        require(!cookie.isNullOrBlank() && cookie.startsWith("mikael_session=")) {
            "O servidor não retornou uma sessão válida."
        }
        val encrypted = SecureLocalStorage.encrypt(cookie)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_COOKIE, encrypted).commit()) {
            "Não foi possível salvar a sessão criptografada."
        }
        true
    }

    suspend fun search(context: Context, query: String): WebSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        require(cleanQuery.length in 2..500) { "A pesquisa deve ter entre 2 e 500 caracteres." }
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null) ?: throw IllegalStateException("Conecte-se ao serviço Mikael AI primeiro.")
        val cookie = SecureLocalStorage.decrypt(encrypted)
        val result = request(
            path = "/api/search",
            method = "POST",
            body = JSONObject().put("query", cleanQuery).toString(),
            cookie = cookie
        )
        if (result.status == 401) {
            clearSession(context)
            throw IllegalStateException("A sessão expirou. Conecte-se novamente.")
        }
        if (result.status !in 200..299) throw IllegalStateException(errorMessage(result.body, result.status))
        val json = JSONObject(result.body)
        val sourcesJson = json.optJSONArray("sources") ?: throw IllegalStateException("A resposta não contém fontes.")
        val sources = buildList {
            for (index in 0 until sourcesJson.length()) {
                val item = sourcesJson.optJSONObject(index) ?: continue
                val title = item.optString("title").ifBlank { item.optString("url") }
                val url = item.optString("url")
                val uri = runCatching { Uri.parse(url) }.getOrNull()
                val scheme = uri?.scheme
                if (title.isNotBlank() && (scheme == "http" || scheme == "https") && !uri?.host.isNullOrBlank()) {
                    add(WebSearchSource(title.take(180), url))
                }
            }
        }.distinctBy { it.url }.take(10)
        if (sources.isEmpty()) throw IllegalStateException("A pesquisa não retornou links verificáveis.")
        WebSearchResult(
            query = json.optString("query", cleanQuery),
            summary = json.optString("summary").ifBlank { "A pesquisa retornou fontes, mas não gerou um resumo." },
            sources = sources,
            provider = json.optString("provider", "OpenRouter + Exa")
        )
    }

    suspend fun logout(context: Context) = withContext(Dispatchers.IO) {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COOKIE, null)
        if (!encrypted.isNullOrBlank()) {
            runCatching {
                request("/api/logout", "POST", body = "{}", cookie = SecureLocalStorage.decrypt(encrypted))
            }
        }
        clearSession(context)
    }

    private fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_COOKIE).apply()
    }

    private data class HttpResult(val status: Int, val body: String, val setCookie: String?)

    private fun request(
        path: String,
        method: String,
        body: String,
        cookie: String? = null
    ): HttpResult {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "Mikael-AI-Android")
            if (cookie != null) connection.setRequestProperty("Cookie", cookie)
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResult(status, response, connection.getHeaderField("Set-Cookie"))
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: String, status: Int): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
        return detail?.takeIf { it.isNotBlank() } ?: "O serviço Mikael AI respondeu HTTP $status."
    }
}
