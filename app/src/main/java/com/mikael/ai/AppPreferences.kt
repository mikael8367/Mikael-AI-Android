package com.mikael.ai

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("mikael_ai_settings", Context.MODE_PRIVATE)

    var modelPath: String?
        get() = prefs.getString("model_path", null)
        set(value) { prefs.edit().putString("model_path", value).apply() }

    var memoryLimitMiB: Int
        get() = prefs.getInt("memory_limit_mib", 1024)
        set(value) {
            if (value in MemoryBudget.allowedLimitsMiB) prefs.edit().putInt("memory_limit_mib", value).apply()
        }

    var contextTokens: Int
        get() = prefs.getInt("context_tokens", 1024)
        set(value) {
            if (value in listOf(512, 1024, 2048, 4096)) prefs.edit().putInt("context_tokens", value).apply()
        }

    var onlineMode: Boolean
        get() = prefs.getBoolean("online_mode", false)
        set(value) { prefs.edit().putBoolean("online_mode", value).apply() }

    var onlineModelId: String
        get() = prefs.getString("online_model_id", "mikael") ?: "mikael"
        set(value) {
            if (value.isNotBlank() && value.length <= 128) {
                prefs.edit().putString("online_model_id", value).apply()
            }
        }
}
