package com.mikael.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val id: Long, val role: String, val text: String)

object ConversationStore {
    private const val PREFS = "local_conversation"
    private const val KEY_MESSAGES = "messages"
    private const val MAX_MESSAGES = 80

    fun load(context: Context): List<ChatMessage> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MESSAGES, "[]") ?: "[]"
        val json = JSONArray(raw)
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val role = item.optString("role")
                val text = item.optString("text")
                if ((role == "user" || role == "assistant") && text.isNotBlank()) {
                    add(ChatMessage(item.optLong("id", index.toLong()), role, text))
                }
            }
        }
    }.getOrDefault(emptyList())

    /** Mantém o histórico em memória limitado, evitando crescimento ilimitado em conversas longas. */
    fun append(messages: MutableList<ChatMessage>, message: ChatMessage) {
        messages.add(message)
        while (messages.size > MAX_MESSAGES) messages.removeAt(0)
    }

    fun save(context: Context, messages: List<ChatMessage>) {
        val json = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            json.put(JSONObject().put("id", message.id).put("role", message.role).put("text", message.text))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MESSAGES, json.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_MESSAGES).apply()
    }
}
