package com.mikael.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class UserMemoryItem(val text: String, val topic: String, val createdAt: Long)

object UserMemoryStore {
    private const val PREFS = "mikael_user_memory"
    private const val KEY_ITEMS = "items"
    private const val KEY_ENABLED = "enabled"
    const val MAX_ITEMS = 100
    const val MAX_ITEM_CHARS = 300
    const val MAX_CONTEXT_CHARS = 2400
    private const val DUPLICATE_THRESHOLD = 0.82

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun load(context: Context): List<UserMemoryItem> = read(context)

    fun rememberFromUserMessage(context: Context, message: String): Boolean {
        if (!isEnabled(context)) return false
        val fact = parseRememberCommand(message)?.take(MAX_ITEM_CHARS) ?: return false
        val items = read(context).toMutableList()
        val normalized = normalize(fact)
        if (normalized.isBlank()) return false
        val duplicateIndex = items.indexOfFirst { existing ->
            val old = normalize(existing.text)
            old == normalized || tokenSimilarity(old, normalized) >= DUPLICATE_THRESHOLD
        }
        if (duplicateIndex >= 0) {
            val old = items[duplicateIndex]
            if (fact.length > old.text.length &&
                tokenSimilarity(normalize(old.text), normalized) >= DUPLICATE_THRESHOLD) {
                items[duplicateIndex] = old.copy(text = fact, topic = classifyTopic(fact))
                write(context, items)
            }
            return false
        }
        items.add(UserMemoryItem(fact, classifyTopic(fact), System.currentTimeMillis()))
        write(context, items)
        return true
    }

    fun forgetFromUserMessage(context: Context, message: String): Boolean {
        val target = parseForgetCommand(message) ?: return false
        val normalized = normalize(target)
        val before = read(context)
        val after = before.filterNot {
            val candidate = normalize(it.text)
            candidate == normalized || tokenSimilarity(candidate, normalized) >= DUPLICATE_THRESHOLD
        }
        if (after.size == before.size) return false
        write(context, after)
        return true
    }

    fun remove(context: Context, item: UserMemoryItem) {
        write(context, read(context).filterNot { it.createdAt == item.createdAt && it.text == item.text })
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).apply()
    }

    fun contextForPrompt(context: Context): String {
        if (!isEnabled(context)) return ""
        val output = StringBuilder()
        for ((topic, entries) in read(context).groupBy { it.topic }.toSortedMap()) {
            val block = "[$topic]\n" + entries.joinToString("") { "- " + it.text + "\n" }
            if (output.length + block.length <= MAX_CONTEXT_CHARS) output.append(block)
        }
        return output.toString()
    }

    internal fun parseRememberCommand(message: String): String? =
        Regex("^\\s*(?:lembre(?:-se)?(?:\\s+que)?|guarde(?:\\s+que)?)\\s*[:,-]?\\s*(.+)$",
            RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    internal fun parseForgetCommand(message: String): String? =
        Regex("^\\s*(?:esqueça|esquece|apague|remova)\\s*[:,-]?\\s*(.+)$",
            RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    internal fun classifyTopic(fact: String): String {
        val value = normalize(fact)
        val groups = linkedMapOf(
            "Programação" to listOf("programacao", "codigo", "kotlin", "java", "python", "bug", "software", "app", "android", "github", "api"),
            "Minecraft" to listOf("minecraft", "mod", "forge", "fabric", "modpack", "launcher", "aternos", "bedrock", "servidor"),
            "Projetos" to listOf("projeto", "repositorio", "mikael ai", "desenvolver", "construir", "funcionalidade"),
            "Preferências" to listOf("prefiro", "gosto", "quero", "nao quero", "sempre", "nunca", "portugues", "portugues brasil")
        )
        return groups.entries.firstOrNull { entry -> entry.value.any { value.contains(it) } }?.key ?: "Outros"
    }

    internal fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim().replace(Regex("\\s+"), " ")

    internal fun tokenSimilarity(first: String, second: String): Double {
        val a = first.split(" ").filter { it.length > 2 }.toSet()
        val b = second.split(" ").filter { it.length > 2 }.toSet()
        if (a.isEmpty() || b.isEmpty()) return if (first == second) 1.0 else 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }

    private fun write(context: Context, items: List<UserMemoryItem>) {
        val json = JSONArray()
        items.takeLast(MAX_ITEMS).forEach { item ->
            json.put(JSONObject().put("text", item.text.take(MAX_ITEM_CHARS))
                .put("topic", item.topic).put("createdAt", item.createdAt))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, json.toString()).apply()
    }

    private fun read(context: Context): List<UserMemoryItem> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        val json = JSONArray(raw)
        buildList {
            for (i in 0 until json.length()) {
                val value = json.opt(i)
                val item = if (value is JSONObject) {
                    val text = value.optString("text").trim()
                    if (text.isBlank()) null else UserMemoryItem(text.take(MAX_ITEM_CHARS),
                        value.optString("topic").ifBlank { classifyTopic(text) }, value.optLong("createdAt", i.toLong()))
                } else {
                    val text = value?.toString()?.trim().orEmpty()
                    if (text.isBlank() || text == "null") null
                    else UserMemoryItem(text.take(MAX_ITEM_CHARS), classifyTopic(text), i.toLong())
                }
                if (item != null) add(item)
            }
        }.distinctBy { normalize(it.text) }.takeLast(MAX_ITEMS)
    }.getOrDefault(emptyList())
}
