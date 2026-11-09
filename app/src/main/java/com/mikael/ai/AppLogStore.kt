package com.mikael.ai

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent diagnostic journal. It intentionally stores action names and
 * status messages only; prompts, chat contents, credentials and tokens are excluded.
 */
object AppLogStore {
    private const val PREFS = "mikael_ai_logs"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 2000
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun add(context: Context, level: String, message: String) {
        val safeLevel = level.uppercase(Locale.ROOT).take(8)
        val safeMessage = redact(message).replace('\n', ' ').replace('\r', ' ').take(700)
        synchronized(lock) {
            val line = "${formatter.format(Date())}  [${safeLevel}]  ${safeMessage}"
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val entries = prefs.getString(KEY_ENTRIES, "").orEmpty()
                .lineSequence().filter { it.isNotBlank() }.toMutableList()
            entries.add(line)
            prefs.edit().putString(KEY_ENTRIES, entries.takeLast(MAX_ENTRIES).joinToString("\n")).apply()
        }
    }

    fun read(context: Context): List<String> = synchronized(lock) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toList()
    }

    fun clear(context: Context) = synchronized(lock) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ENTRIES).apply()
    }

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})"), "[CREDENCIAL_OCULTA]")
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*")) { match ->
            match.groupValues[1] + "[OCULTO]"
        }
        .replace(Regex("(?i)(token|api[_-]?key|client[_-]?secret|password)(\\s*[:=]\\s*)[^\\s,;]+")) { match ->
            match.groupValues[1] + match.groupValues[2] + "[OCULTO]"
        }
}
