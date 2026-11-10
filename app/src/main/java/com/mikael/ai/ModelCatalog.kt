package com.mikael.ai

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class CatalogModel(
    val id: String,
    val name: String,
    val description: String,
    val sizeLabel: String,
    val expectedBytes: Long,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val license: String,
    val category: String
)

object ModelCatalog {
    // Modelos de terceiros foram desativados: o motor autoral não carrega GGUF.
    val models = emptyList<CatalogModel>()
}

object ModelDownloader {
    suspend fun download(
        context: Context,
        model: CatalogModel,
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        require(model.downloadUrl.startsWith("https://huggingface.co/")) {
            "Origem de download não permitida."
        }
        val directory = File(context.filesDir, "models").apply { check(mkdirs() || isDirectory) }
        val destination = File(directory, model.fileName)
        if (destination.exists() && destination.length() > 1_000_000L) {
            if (sha256(destination).equals(model.sha256, ignoreCase = true)) {
                return@withContext destination.absolutePath
            }
            check(destination.delete()) { "Não foi possível substituir o arquivo incompleto." }
        }

        val availableBytes = StatFs(context.filesDir.absolutePath).availableBytes
        val requiredBytes = model.expectedBytes + 64L * 1024L * 1024L
        require(availableBytes >= requiredBytes) {
            "Espaço insuficiente. Libere pelo menos " + (requiredBytes / (1024L * 1024L)) + " MiB."
        }

        val temporary = File(directory, model.fileName + ".part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mikael-AI-Android")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val status = connection.responseCode
            check(status in 200..299) { "Servidor de modelos respondeu HTTP " + status + "." }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: model.expectedBytes
            require(total >= model.expectedBytes * 9L / 10L && total <= model.expectedBytes * 11L / 10L) {
                "O tamanho recebido não corresponde ao modelo esperado."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastUpdate = 0L
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered(256 * 1024).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 400L || downloaded == total) {
                            withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                            lastUpdate = now
                        }
                    }
                    output.flush()
                }
            }
            require(downloaded > 1_000_000L) { "O download terminou vazio ou incompleto." }
            val headerOk = temporary.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && magic.decodeToString() == "GGUF"
            }
            require(headerOk) { "O arquivo recebido não é um modelo GGUF válido." }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            require(actualHash.equals(model.sha256, ignoreCase = true)) {
                "A verificação SHA-256 falhou. O arquivo foi descartado."
            }
            if (destination.exists()) check(destination.delete()) {
                "Não foi possível substituir o modelo anterior."
            }
            check(temporary.renameTo(destination)) { "Não foi possível salvar o modelo instalado." }
            withContext(Dispatchers.Main) { onProgress(downloaded, total) }
            destination.absolutePath
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
