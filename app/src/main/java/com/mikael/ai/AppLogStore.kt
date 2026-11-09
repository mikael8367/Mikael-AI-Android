package com.mikael.ai

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Diário local de diagnóstico. Nunca registra prompts, conversas ou credenciais. */
object AppLogStore {
    private const val PREFS = "mikael_ai_logs"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 2000
    private const val MAX_MESSAGE_CHARS = 900
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun add(context: Context, level: String, message: String) {
        append(context, listOf(level to message))
    }

    fun addThrowable(context: Context, label: String, error: Throwable) {
        val details = mutableListOf<Pair<String, String>>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            val currentError = current
            details += "ERROR" to "$label • ${currentError.javaClass.name}: ${currentError.message ?: "sem mensagem"}"
            currentError.stackTrace.take(18).forEachIndexed { index, frame ->
                details += "TRACE" to "[$depth] #$index $frame"
            }
            current = currentError.cause
            depth++
        }
        if (details.isEmpty()) details += "ERROR" to "$label • erro desconhecido"
        append(context, details)
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

    private fun append(context: Context, records: List<Pair<String, String>>) {
        if (records.isEmpty()) return
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val entries = prefs.getString(KEY_ENTRIES, "").orEmpty()
                .lineSequence().filter { it.isNotBlank() }.toMutableList()
            records.forEach { (level, message) ->
                val safeLevel = level.uppercase(Locale.ROOT).take(8)
                val safeMessage = redact(message).replace('\n', ' ').replace('\r', ' ').take(MAX_MESSAGE_CHARS)
                entries.add("${formatter.format(Date())}  [${safeLevel}]  ${safeMessage}")
            }
            prefs.edit().putString(KEY_ENTRIES, entries.takeLast(MAX_ENTRIES).joinToString("\n")).apply()
        }
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
