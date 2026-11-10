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
    private const val KEY_ITEMS_SECURE = "items_secure_v1"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ENABLED_SECURE = "enabled_secure_v1"
    private const val KEY_AUTO_LEARN_SECURE = "auto_learn_secure_v1"
    const val MAX_ITEMS = 100
    const val MAX_ITEM_CHARS = 300
    const val MAX_CONTEXT_CHARS = 2400
    private const val DUPLICATE_THRESHOLD = 0.75

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_ENABLED_SECURE, null)
        val decrypted = encrypted?.let { runCatching { SecureLocalStorage.decrypt(it) }.getOrNull() }
        if (decrypted != null) return runCatching { JSONObject(decrypted).optBoolean("enabled", true) }.getOrDefault(true)
        val legacy = prefs.getBoolean(KEY_ENABLED, true)
        runCatching {
            val payload = SecureLocalStorage.encrypt(JSONObject().put("enabled", legacy).toString())
            val stored = prefs.edit().putString(KEY_ENABLED_SECURE, payload).commit()
            if (stored) prefs.edit().remove(KEY_ENABLED).commit()
        }
        return legacy
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val payload = SecureLocalStorage.encrypt(JSONObject().put("enabled", enabled).toString())
        check(prefs.edit().putString(KEY_ENABLED_SECURE, payload).commit()) {
            "Não foi possível salvar a configuração de memória criptografada."
        }
        prefs.edit().remove(KEY_ENABLED).commit()
    }

    fun load(context: Context): List<UserMemoryItem> = read(context)

    fun isAutoLearningEnabled(context: Context): Boolean {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_AUTO_LEARN_SECURE, null) ?: return false
        return runCatching {
            JSONObject(SecureLocalStorage.decrypt(encrypted)).optBoolean("enabled", false)
        }.getOrDefault(false)
    }

    fun setAutoLearningEnabled(context: Context, enabled: Boolean) {
        val payload = SecureLocalStorage.encrypt(JSONObject().put("enabled", enabled).toString())
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_AUTO_LEARN_SECURE, payload).commit()) {
            "Não foi possível salvar a configuração de aprendizado."
        }
    }

    /** Aprende apenas preferências e fatos técnicos explícitos; nunca envia dados à rede. */
    fun learnFromUserMessage(context: Context, message: String): Boolean {
        if (!isEnabled(context) || !isAutoLearningEnabled(context)) return false
        val fact = autoLearningCandidate(message) ?: return false
        val items = read(context).toMutableList()
        val key = normalize(fact)
        val duplicate = items.indexOfFirst {
            val existing = normalize(it.text)
            existing == key || tokenSimilarity(existing, key) >= DUPLICATE_THRESHOLD
        }
        if (duplicate >= 0) {
            val old = items[duplicate]
            if (fact.length <= old.text.length) return false
            items[duplicate] = old.copy(text = fact, topic = classifyTopic(fact), createdAt = System.currentTimeMillis())
        } else {
            items.add(UserMemoryItem(fact, classifyTopic(fact), System.currentTimeMillis()))
        }
        write(context, items)
        return true
    }


    internal fun autoLearningCandidate(message: String): String? {
        val clean = message.trim().replace(Regex("\\s+"), " ")
        if (clean.length !in 12..MAX_ITEM_CHARS || "```" in clean) return null
        val normalized = normalize(clean)
        val sensitive = listOf("saude", "doenca", "medicamento", "remedio", "diagnostico", "depressao", "ansiedade", "religiao", "igreja", "politica", "partido", "sexualidade", "orientacao sexual", "senha", "password", "token", "cpf", "cartao", "endereco", "telefone", "documento", "raca", "etnia")
        if (sensitive.any { normalized.contains(it) }) return null
        val stableSignals = listOf("prefiro ", "gosto de ", "nao gosto de ", "uso ", "estou desenvolvendo ", "estou criando ", "meu projeto ", "meu aparelho ", "meu celular ", "trabalho com ", "sempre quero ", "nao quero ")
        return clean.take(MAX_ITEM_CHARS).takeIf { stableSignals.any { signal -> normalized.contains(signal) } }
    }

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
                return true
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).remove(KEY_ITEMS_SECURE).apply()
    }

    fun contextForPrompt(context: Context): String {
        if (!isEnabled(context)) return ""
        val output = StringBuilder()
        for ((topic, entries) in read(context).groupBy { it.topic }.toSortedMap()) {
            val heading = "[$topic]\n"
            if (output.length + heading.length > MAX_CONTEXT_CHARS) break
            output.append(heading)
            for (entry in entries) {
                val line = "- " + entry.text + "\n"
                if (output.length + line.length > MAX_CONTEXT_CHARS) break
                output.append(line)
            }
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
        return groups.entries.firstOrNull { entry ->
            entry.value.any { keyword ->
                if (keyword.contains(' ')) value.contains(keyword) else value.split(' ').contains(keyword)
            }
        }?.key ?: "Outros"
    }

    internal fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim().replace(Regex("\\s+"), " ")

    internal fun tokenSimilarity(first: String, second: String): Double {
        val ignored = setOf("que", "para", "com", "uma", "uns", "das", "dos", "em", "de", "do", "da", "eu", "meu", "minha")
        val a = first.split(" ").filter { it.length > 1 && it !in ignored }.toSet()
        val b = second.split(" ").filter { it.length > 1 && it !in ignored }.toSet()
        if (a.isEmpty() || b.isEmpty()) return if (first == second) 1.0 else 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }

    private fun write(context: Context, items: List<UserMemoryItem>) {
        val json = JSONArray()
        items.takeLast(MAX_ITEMS).forEach { item ->
            json.put(JSONObject().put("text", item.text.take(MAX_ITEM_CHARS))
                .put("topic", item.topic).put("createdAt", item.createdAt))
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = SecureLocalStorage.encrypt(json.toString())
        check(prefs.edit().putString(KEY_ITEMS_SECURE, encrypted).commit()) {
            "Não foi possível salvar a memória criptografada."
        }
        prefs.edit().remove(KEY_ITEMS).commit()
    }

    private fun read(context: Context): List<UserMemoryItem> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_ITEMS_SECURE, null)
        val legacyRaw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val decrypted = encrypted?.takeIf { it.isNotBlank() }?.let {
            runCatching { SecureLocalStorage.decrypt(it) }.getOrNull()
        }
        val raw = decrypted ?: legacyRaw
        if (decrypted != null && prefs.contains(KEY_ITEMS)) prefs.edit().remove(KEY_ITEMS).apply()
        if (decrypted == null && prefs.contains(KEY_ITEMS)) {
            runCatching {
                val payload = SecureLocalStorage.encrypt(raw)
                val stored = prefs.edit().putString(KEY_ITEMS_SECURE, payload).commit()
                if (stored) prefs.edit().remove(KEY_ITEMS).commit()
            }
        }
        val json = JSONArray(raw)
        val parsed = buildList {
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
        }
        val deduplicated = mutableListOf<UserMemoryItem>()
        parsed.forEach { item ->
            val normalized = normalize(item.text)
            val duplicateIndex = deduplicated.indexOfFirst { existing ->
                val candidate = normalize(existing.text)
                candidate == normalized || tokenSimilarity(candidate, normalized) >= DUPLICATE_THRESHOLD
            }
            if (duplicateIndex < 0) deduplicated.add(item)
            else if (item.text.length > deduplicated[duplicateIndex].text.length) {
                deduplicated[duplicateIndex] = item
            }
        }
        deduplicated.takeLast(MAX_ITEMS)
    }.getOrDefault(emptyList())
}
