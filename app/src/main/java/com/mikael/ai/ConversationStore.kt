package com.mikael.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val id: Long, val role: String, val text: String)

data class ChatConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>
)

object ConversationStore {
    private const val PREFS = "local_conversation"
    private const val KEY_CONVERSATIONS = "conversations"
    private const val KEY_CONVERSATIONS_SECURE = "conversations_secure_v1"
    private const val LEGACY_KEY_MESSAGES = "messages"
    private const val MAX_CONVERSATIONS = 100
    private const val MAX_MESSAGES_PER_CONVERSATION = 80

    fun loadAll(context: Context): List<ChatConversation> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_CONVERSATIONS_SECURE, null)
        val legacyRaw = prefs.getString(KEY_CONVERSATIONS, null)
        val decrypted = encrypted?.takeIf { it.isNotBlank() }?.let {
            runCatching { SecureLocalStorage.decrypt(it) }.getOrNull()
        }
        val raw = decrypted ?: legacyRaw
        if (decrypted != null) {
            prefs.edit().remove(KEY_CONVERSATIONS).remove(LEGACY_KEY_MESSAGES).apply()
        }
        if (raw.isNullOrBlank()) {
            val migrated = migrateLegacy(prefs.getString(LEGACY_KEY_MESSAGES, "[]") ?: "[]")
            if (migrated.isNotEmpty()) saveAll(context, migrated)
            return@runCatching migrated
        }
        if (decrypted == null && !raw.isNullOrBlank()) {
            // Encrypt existing history before removing the previous plaintext copy.
            runCatching {
                val payload = SecureLocalStorage.encrypt(raw)
                val stored = prefs.edit().putString(KEY_CONVERSATIONS_SECURE, payload).commit()
                if (stored) prefs.edit().remove(KEY_CONVERSATIONS).remove(LEGACY_KEY_MESSAGES).commit()
            }
        }
        val json = JSONArray(raw)
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val messages = readMessages(item.optJSONArray("messages") ?: JSONArray())
                if (messages.isNotEmpty()) {
                    val title = item.optString("title").ifBlank { titleFrom(messages) }
                    add(ChatConversation(id, title, item.optLong("updatedAt", 0L), messages))
                }
            }
        }.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS)
    }.getOrDefault(emptyList())

    private fun migrateLegacy(raw: String): List<ChatConversation> = runCatching {
        val messages = readMessages(JSONArray(raw))
        if (messages.isEmpty()) emptyList()
        else listOf(ChatConversation("legacy-${messages.first().id}", titleFrom(messages), messages.last().id, messages))
    }.getOrDefault(emptyList())

    private fun readMessages(json: JSONArray): List<ChatMessage> = buildList {
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            val role = item.optString("role")
            val text = item.optString("text")
            if ((role == "user" || role == "assistant") && text.isNotBlank()) {
                add(ChatMessage(item.optLong("id", index.toLong()), role, text))
            }
        }
    }.takeLast(MAX_MESSAGES_PER_CONVERSATION)

    fun append(messages: MutableList<ChatMessage>, message: ChatMessage) {
        messages.add(message)
        while (messages.size > MAX_MESSAGES_PER_CONVERSATION) messages.removeAt(0)
    }

    fun saveAll(context: Context, conversations: List<ChatConversation>) {
        val json = JSONArray()
        conversations.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS).forEach { conversation ->
            val messageArray = JSONArray()
            conversation.messages.takeLast(MAX_MESSAGES_PER_CONVERSATION).forEach { message ->
                messageArray.put(JSONObject().put("id", message.id).put("role", message.role).put("text", message.text))
            }
            json.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("title", conversation.title.take(80))
                    .put("updatedAt", conversation.updatedAt)
                    .put("messages", messageArray)
            )
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = SecureLocalStorage.encrypt(json.toString())
        check(prefs.edit().putString(KEY_CONVERSATIONS_SECURE, encrypted).commit()) {
            "Não foi possível salvar o histórico criptografado."
        }
        prefs.edit().remove(KEY_CONVERSATIONS).remove(LEGACY_KEY_MESSAGES).commit()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_CONVERSATIONS).remove(KEY_CONVERSATIONS_SECURE).remove(LEGACY_KEY_MESSAGES).apply()
    }

    private fun titleFrom(messages: List<ChatMessage>): String {
        val first = messages.firstOrNull { it.role == "user" }?.text.orEmpty()
        return first.replace(Regex("\\s+"), " ").trim().let {
            if (it.length > 42) it.take(42).trimEnd() + "…" else it
        }.ifBlank { "Nova conversa" }
    }
}
