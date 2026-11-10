package com.mikael.ai

import android.content.Context
import org.json.JSONArray

/**
 * Memória explícita e local. Só guarda fatos quando o usuário usa "Lembre:".
 * Não tenta inferir nem persistir dados pessoais automaticamente.
 */
object UserMemoryStore {
    private const val PREFS = "mikael_user_memory"
    private const val KEY_ITEMS = "items"
    private const val KEY_ENABLED = "enabled"
    const val MAX_ITEMS = 100
    const val MAX_ITEM_CHARS = 300
    const val MAX_CONTEXT_CHARS = 2400

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Retorna itens salvos mesmo quando desativada, para permitir revisão e exclusão. */
    fun load(context: Context): List<String> = read(context)

    fun rememberFromUserMessage(context: Context, message: String): Boolean {
        if (!isEnabled(context)) return false
        val match = Regex("^\\s*(?:lembre(?:-se)?(?:\\s+que)?|guarde(?:\\s+que)?)\\s*[:,-]?\\s*(.+)$",
            RegexOption.IGNORE_CASE).find(message) ?: return false
        val fact = match.groupValues[1].trim().take(MAX_ITEM_CHARS)
        if (fact.isBlank()) return false
        val items = read(context).filterNot { it.equals(fact, ignoreCase = true) }.toMutableList()
        items.add(fact)
        write(context, items)
        return true
    }

    fun forgetFromUserMessage(context: Context, message: String): Boolean {
        val match = Regex("^\\s*(?:esqueça|esquece|apague|remova)\\s*[:,-]?\\s*(.+)$",
            RegexOption.IGNORE_CASE).find(message) ?: return false
        val target = match.groupValues[1].trim()
        if (target.isBlank()) return false
        val before = read(context)
        val after = before.filterNot { it.equals(target, ignoreCase = true) }
        if (after.size == before.size) return false
        write(context, after)
        return true
    }

    fun remove(context: Context, fact: String) {
        write(context, read(context).filterNot { it == fact })
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).apply()
    }

    private fun write(context: Context, items: List<String>) {
        val json = JSONArray()
        items.takeLast(MAX_ITEMS).forEach { json.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, json.toString()).apply()
    }

    fun contextForPrompt(context: Context): String {
        if (!isEnabled(context)) return ""
        val output = StringBuilder()
        for (item in read(context).asReversed()) {
            val line = "- " + item + "\n"
            if (output.length + line.length > MAX_CONTEXT_CHARS) break
            output.insert(0, line)
        }
        return output.toString()
    }

    private fun read(context: Context): List<String> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        val json = JSONArray(raw)
        buildList {
            for (i in 0 until json.length()) {
                val value = json.optString(i).trim()
                if (value.isNotBlank()) add(value.take(MAX_ITEM_CHARS))
            }
        }.takeLast(MAX_ITEMS)
    }.getOrDefault(emptyList())
}
