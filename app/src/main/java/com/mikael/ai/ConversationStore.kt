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
    private const val LEGACY_KEY_MESSAGES = "messages"
    private const val MAX_CONVERSATIONS = 100
    private const val MAX_MESSAGES_PER_CONVERSATION = 80

    fun loadAll(context: Context): List<ChatConversation> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONVERSATIONS, null)
        if (raw.isNullOrBlank()) return@runCatching migrateLegacy(prefs.getString(LEGACY_KEY_MESSAGES, "[]") ?: "[]")
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONVERSATIONS, json.toString()).remove(LEGACY_KEY_MESSAGES).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_CONVERSATIONS).remove(LEGACY_KEY_MESSAGES).apply()
    }

    private fun titleFrom(messages: List<ChatMessage>): String {
        val first = messages.firstOrNull { it.role == "user" }?.text.orEmpty()
        return first.replace(Regex("\\s+"), " ").trim().let {
            if (it.length > 42) it.take(42).trimEnd() + "…" else it
        }.ifBlank { "Nova conversa" }
    }
}
