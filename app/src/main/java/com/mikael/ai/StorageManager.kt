package com.mikael.ai

import android.content.Context
import java.io.File

object StorageManager {
    private fun directoryBytes(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0L
        if (directory.isFile) return directory.length()
        return directory.listFiles()?.sumOf { directoryBytes(it) } ?: 0L
    }

    fun installedModelBytes(context: Context): Long {
        val models = File(context.filesDir, "models")
        return models.listFiles()?.filter {
            it.isFile && (it.extension.equals("gguf", true) || it.extension.equals("mkai", true))
        }?.sumOf { it.length() } ?: 0L
    }

    fun temporaryBytes(context: Context): Long {
        val models = File(context.filesDir, "models")
        return models.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".part", true) || it.name.endsWith(".previous", true))
        }?.sumOf { it.length() } ?: 0L
    }

    fun cacheBytes(context: Context): Long =
        directoryBytes(context.cacheDir) + directoryBytes(context.codeCacheDir)

    fun cacheAndTemporaryBytes(context: Context): Long =
        cacheBytes(context) + temporaryBytes(context)

    /** Removes only cache and interrupted installer artifacts; installed model files are preserved. */
    fun clearCacheAndTemporaryFiles(context: Context): Boolean {
        val cacheCleared = context.cacheDir.deleteRecursively() || !context.cacheDir.exists()
        val codeCacheCleared = context.codeCacheDir.deleteRecursively() || !context.codeCacheDir.exists()
        val models = File(context.filesDir, "models")
        val temporaryCleared = models.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".part", true) || it.name.endsWith(".previous", true))
        }?.all { it.delete() || !it.exists() } ?: true
        return cacheCleared && codeCacheCleared && temporaryCleared
    }

    fun formatMiB(bytes: Long): String = String.format(
        java.util.Locale.US,
        "%.1f MiB",
        bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
    )
}
