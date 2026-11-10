package com.mikael.ai

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("mikael_ai_settings", Context.MODE_PRIVATE)

    var modelPath: String?
        get() = prefs.getString("model_path", null)
        set(value) { prefs.edit().putString("model_path", value).apply() }

    var selectedCatalogModelId: String
        get() = prefs.getString("selected_catalog_model_id", "mikael-base") ?: "mikael-base"
        set(value) {
            if (value in setOf("mikael-base", "qwen-0.5b", "smollm2-360m", "tinyllama-1.1b")) {
                prefs.edit().putString("selected_catalog_model_id", value).apply()
            }
        }

    var memoryLimitMiB: Int
        get() = prefs.getInt("memory_limit_mib", 768)
        set(value) {
            if (value in MemoryBudget.allowedLimitsMiB) prefs.edit().putInt("memory_limit_mib", value).apply()
        }

    var contextTokens: Int
        get() = prefs.getInt("context_tokens", 512)
        set(value) {
            if (value in listOf(512, 1024, 2048, 4096)) prefs.edit().putInt("context_tokens", value).apply()
        }

    var allowOnlineFeatures: Boolean
        get() = prefs.getBoolean("allow_online_features", false)
        set(value) { prefs.edit().putBoolean("allow_online_features", value).apply() }

    var githubClientId: String
        get() = prefs.getString("github_client_id", "") ?: ""
        set(value) { prefs.edit().putString("github_client_id", value.trim()).apply() }
}
