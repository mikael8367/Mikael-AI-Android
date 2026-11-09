package com.mikael.ai

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class OnlineModel(val id: String, val name: String, val description: String)
data class RemoteConversation(val id: Long, val title: String, val createdAt: String)
data class RemoteMessage(val role: String, val content: String)
data class RemoteChatResult(val conversationId: Long, val reply: String)

class OnlineApi(
    private val baseUrl: String = "https://mikael-ai.onrender.com"
) {
    private val cookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now || incoming.any { fresh -> fresh.name == it.name && fresh.domain == it.domain && fresh.path == it.path } }
            cookies.addAll(incoming.filter { it.expiresAt >= now })
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }
        @Synchronized fun clear() { cookies.clear() }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var authenticated = false
    @Volatile private var activeCall: Call? = null
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Synchronized
    fun login(password: String) {
        require(password.isNotBlank()) { "Digite a senha de acesso." }
        val body = JSONObject().put("password", password).toString().toRequestBody(jsonType)
        try {
            request("/api/login", "POST", body, authRequired = false)
            authenticated = true
            me()
        } catch (error: Exception) {
            authenticated = false
            cookieJar.clear()
            throw error
        }
    }

    @Synchronized
    fun logout() {
        runCatching { request("/api/logout", "POST", JSONObject().toString().toRequestBody(jsonType)) }
        cookieJar.clear()
        authenticated = false
    }

    fun isAuthenticated(): Boolean = authenticated

    fun cancelActive() {
        activeCall?.cancel()
    }

    fun models(): List<OnlineModel> {
        val array = JSONArray(request("/api/models"))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    add(OnlineModel(id, name, item.optString("description")))
                }
            }
        }
    }

    fun conversations(): List<RemoteConversation> {
        val array = JSONArray(request("/api/conversations"))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                if (id > 0L) add(RemoteConversation(id, item.optString("title"), item.optString("created_at")))
            }
        }
    }

    fun createConversation(): RemoteConversation {
        val item = JSONObject(request("/api/conversations", "POST", JSONObject().toString().toRequestBody(jsonType)))
        val id = item.optLong("id")
        if (id <= 0L) throw IOException("O servidor não retornou um ID de conversa válido.")
        return RemoteConversation(id, item.optString("title"), "")
    }

    fun getConversation(id: Long): List<RemoteMessage> {
        require(id > 0L) { "Conversa inválida." }
        val item = JSONObject(request("/api/conversations/$id"))
        val array = item.optJSONArray("messages") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val message = array.optJSONObject(i) ?: continue
                val role = message.optString("role")
                val content = message.optString("content")
                if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                    add(RemoteMessage(role, content))
                }
            }
        }
    }

    fun sendMessage(message: String, conversationId: Long?, modelId: String): RemoteChatResult {
        require(message.isNotBlank()) { "A mensagem está vazia." }
        require(message.length <= 20000) { "A mensagem ultrapassa o limite de 20.000 caracteres." }
        require(modelId in setOf("mikael", "deepseek", "gpt4o_mini", "gemini_flash", "claude_sonnet", "qwen_coder", "gpt5_mini")) { "Modelo online inválido." }
        val payload = JSONObject().put("message", message).put("model_id", modelId)
        if (conversationId != null && conversationId > 0L) payload.put("conversation_id", conversationId)
        val result = JSONObject(request("/api/chat", "POST", payload.toString().toRequestBody(jsonType)))
        val id = result.optLong("conversation_id")
        val reply = result.optString("reply")
        if (id <= 0L || reply.isBlank()) throw IOException("A resposta do servidor veio incompleta.")
        return RemoteChatResult(id, reply)
    }

    private fun me() {
        val item = JSONObject(request("/api/me"))
        if (item.optBoolean("authenticated") != true) throw IOException("O servidor não confirmou o login.")
    }

    private fun request(
        path: String,
        method: String = "GET",
        body: okhttp3.RequestBody? = null,
        authRequired: Boolean = true
    ): String {
        if (authRequired && !authenticated) throw IOException("Entre no modo online para continuar.")
        val url = baseUrl.trimEnd('/') + path
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        when (method) {
            "POST" -> builder.post(body ?: JSONObject().toString().toRequestBody(jsonType))
            "GET" -> builder.get()
            else -> throw IllegalArgumentException("Método HTTP não permitido.")
        }
        val call = client.newCall(builder.build())
        activeCall = call
        try {
            call.execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        authenticated = false
                        cookieJar.clear()
                    }
                    val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull().orEmpty()
                    val message = detail.ifBlank {
                        when (response.code) {
                            401 -> "Senha incorreta ou sessão expirada."
                            429 -> "Muitas solicitações. Aguarde um pouco."
                            502, 503 -> "O serviço de IA está indisponível no momento."
                            else -> "Falha online (HTTP ${response.code})."
                        }
                    }
                    throw IOException(message)
                }
                return text
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }
}
