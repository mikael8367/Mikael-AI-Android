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

    fun save(context: Context, task: PendingAiTask) {
        val json = JSONObject().put("conversationId", task.conversationId)
            .put("prompt", task.prompt.take(24000)).put("modelId", task.modelId)
            .put("createdAt", task.createdAt).put("githubContext", task.githubContext.take(50000))
            .put("completedResponse", task.completedResponse)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TASK, json.toString()).commit()) { "Não foi possível salvar a tarefa para recuperação." }
    }

    fun load(context: Context): PendingAiTask? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TASK, null)
            ?: return@runCatching null
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TASK).commit()
    }
}
