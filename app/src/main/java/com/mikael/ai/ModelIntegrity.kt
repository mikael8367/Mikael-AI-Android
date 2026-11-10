package com.mikael.ai

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** SHA-256 helpers for downloaded GGUF integrity checks. */
internal object ModelIntegrity {
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun normalizeExpectedSha256(vararg headers: String?): String? =
        headers.asSequence()
            .filterNotNull()
            .map { it.trim().removePrefix("W/").trim('"').lowercase(Locale.ROOT) }
            .firstOrNull { sha256Pattern.matches(it) }

    fun isSha256(value: String): Boolean = sha256Pattern.matches(value.trim())

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun matches(file: File, expected: String): Boolean =
        isSha256(expected) && runCatching {
            sha256(file).equals(expected.trim(), ignoreCase = true)
        }.getOrDefault(false)
}
