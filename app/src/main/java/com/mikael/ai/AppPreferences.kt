package com.mikael.ai

import android.content.Context
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var modelPath: String?
        get() {
            val value = readSettings().opt("model_path")
            return if (value == null || value == JSONObject.NULL) null else value.toString().takeIf { it.isNotBlank() }
        }
        set(value) = updateSettings { put("model_path", value ?: JSONObject.NULL) }

    var selectedCatalogModelId: String
        get() = readSettings().optString("selected_catalog_model_id", "mikael-base")
            .takeIf { it in MODEL_IDS } ?: "mikael-base"
        set(value) {
            if (value in MODEL_IDS) updateSettings { put("selected_catalog_model_id", value) }
        }

    var memoryLimitMiB: Int
        get() = readSettings().optInt("memory_limit_mib", 768)
            .takeIf { it in MemoryBudget.allowedLimitsMiB } ?: 768
        set(value) {
            if (value in MemoryBudget.allowedLimitsMiB) updateSettings { put("memory_limit_mib", value) }
        }

    var contextTokens: Int
        get() = readSettings().optInt("context_tokens", 512)
            .takeIf { it in CONTEXT_OPTIONS } ?: 512
        set(value) {
            if (value in CONTEXT_OPTIONS) updateSettings { put("context_tokens", value) }
        }

    var allowOnlineFeatures: Boolean
        get() = readSettings().optBoolean("allow_online_features", false)
        set(value) = updateSettings { put("allow_online_features", value) }

    var githubClientId: String
        get() = readSettings().optString("github_client_id", "")
        set(value) = updateSettings { put("github_client_id", value.trim()) }

    private fun readSettings(): JSONObject {
        val encrypted = prefs.getString(KEY_SECURE, null)
        if (!encrypted.isNullOrBlank()) {
            val decrypted = runCatching { SecureLocalStorage.decrypt(encrypted) }.getOrNull()
            if (decrypted != null) {
                if (LEGACY_KEYS.any(prefs::contains)) {
                    prefs.edit().removeAllLegacy().apply()
                }
                return runCatching { JSONObject(decrypted) }.getOrDefault(JSONObject())
            }
        }

        val legacy = JSONObject()
        prefs.getString("model_path", null)?.let { legacy.put("model_path", it) }
        prefs.getString("selected_catalog_model_id", null)?.let { legacy.put("selected_catalog_model_id", it) }
        if (prefs.contains("memory_limit_mib")) legacy.put("memory_limit_mib", prefs.getInt("memory_limit_mib", 768))
        if (prefs.contains("context_tokens")) legacy.put("context_tokens", prefs.getInt("context_tokens", 512))
        if (prefs.contains("allow_online_features")) legacy.put("allow_online_features", prefs.getBoolean("allow_online_features", false))
        prefs.getString("github_client_id", null)?.let { legacy.put("github_client_id", it) }
        if (legacy.length() > 0) runCatching { saveSettings(legacy) }
        return legacy
    }

    private fun updateSettings(change: JSONObject.() -> Unit) {
        val settings = readSettings().apply(change)
        saveSettings(settings)
    }

    private fun saveSettings(settings: JSONObject) {
        val encrypted = SecureLocalStorage.encrypt(settings.toString())
        check(prefs.edit().putString(KEY_SECURE, encrypted).commit()) {
            "Não foi possível salvar as configurações criptografadas."
        }
        prefs.edit().removeAllLegacy().commit()
    }

    private fun android.content.SharedPreferences.Editor.removeAllLegacy(): android.content.SharedPreferences.Editor {
        LEGACY_KEYS.forEach { remove(it) }
        return this
    }

    private companion object {
        const val PREFS = "mikael_ai_settings"
        const val KEY_SECURE = "settings_secure_v1"
        val MODEL_IDS = setOf("mikael-base", "qwen-0.5b", "smollm2-360m", "tinyllama-1.1b")
        val CONTEXT_OPTIONS = setOf(512, 1024, 2048, 4096)
        val LEGACY_KEYS = listOf(
            "model_path",
            "selected_catalog_model_id",
            "memory_limit_mib",
            "context_tokens",
            "allow_online_features",
            "github_client_id"
        )
    }
}
