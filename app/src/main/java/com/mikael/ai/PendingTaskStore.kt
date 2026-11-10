package com.mikael.ai

import android.content.Context
import org.json.JSONObject

data class PendingAiTask(
    val conversationId: String,
    val prompt: String,
    val modelId: String,
    val createdAt: Long,
    val githubContext: String = "",
    val completedResponse: String? = null
)

/** Diário durável para oferecer retomada manual após encerramento inesperado do processo. */
object PendingTaskStore {
    private const val PREFS = "mikael_pending_tasks"
    private const val KEY_TASK = "pending_task"
    private const val KEY_TASK_SECURE = "pending_task_secure_v1"

    fun save(context: Context, task: PendingAiTask) {
        val json = JSONObject().put("conversationId", task.conversationId)
            .put("prompt", task.prompt.take(24000)).put("modelId", task.modelId)
            .put("createdAt", task.createdAt).put("githubContext", task.githubContext.take(50000))
            .put("completedResponse", task.completedResponse)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = SecureLocalStorage.encrypt(json.toString())
        check(prefs.edit().putString(KEY_TASK_SECURE, encrypted).commit()) {
            "Não foi possível salvar a tarefa criptografada para recuperação."
        }
        prefs.edit().remove(KEY_TASK).commit()
    }

    fun load(context: Context): PendingAiTask? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_TASK_SECURE, null)
        val legacyRaw = prefs.getString(KEY_TASK, null)
        val decrypted = encrypted?.takeIf { it.isNotBlank() }?.let {
            runCatching { SecureLocalStorage.decrypt(it) }.getOrNull()
        }
        val raw = decrypted ?: legacyRaw
        if (raw.isNullOrBlank()) return@runCatching null
        if (decrypted != null && legacyRaw != null) prefs.edit().remove(KEY_TASK).apply()
        if (decrypted == null && legacyRaw != null) {
            runCatching {
                val payload = SecureLocalStorage.encrypt(legacyRaw)
                val stored = prefs.edit().putString(KEY_TASK_SECURE, payload).commit()
                if (stored) prefs.edit().remove(KEY_TASK).commit()
            }
        }
        val json = JSONObject(raw)
        val id = json.optString("conversationId")
        val prompt = json.optString("prompt")
        val model = json.optString("modelId")
        if (id.isBlank() || prompt.isBlank() || model !in setOf("mikael-base", "qwen-0.5b", "smollm2-360m", "tinyllama-1.1b")) null
        else PendingAiTask(id, prompt, model, json.optLong("createdAt"), json.optString("githubContext"), json.optString("completedResponse").takeIf { it.isNotBlank() })
    }.getOrNull()

    /** Grava a resposta antes de alterar o histórico para permitir recuperação idempotente. */
    fun complete(context: Context, response: String) {
        val task = load(context) ?: return
        save(context, task.copy(completedResponse = response.take(24000)))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TASK).remove(KEY_TASK_SECURE).commit()
    }
}
