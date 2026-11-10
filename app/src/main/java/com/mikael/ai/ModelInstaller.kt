package com.mikael.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object ModelInstaller {
    suspend fun install(
        context: Context,
        model: CatalogModel,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(model.engine == ModelEngine.LLAMA_CPP)
        val address = requireNotNull(model.downloadUrl)
        val name = requireNotNull(model.fileName)
        require(address.startsWith("https://")) { "A origem do modelo precisa usar HTTPS." }

        val directory = File(context.filesDir, "models").apply {
            check(mkdirs() || isDirectory) { "Não foi possível criar a pasta de modelos." }
        }
        val destination = File(directory, name)
        val temporary = File(directory, "$name.part")
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MikaelAI-Android")
        }

        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Servidor recusou o download (HTTP ${connection.responseCode})."
            }
            val expected = connection.contentLengthLong
            check(expected <= MAX_MODEL_BYTES) { "O arquivo excede o limite de 1 GB." }
            val usable = directory.usableSpace
            check(usable > (if (expected > 0) expected else model.approximateBytes) + 32L * 1024 * 1024) {
                "Espaço insuficiente. Libere armazenamento antes de instalar."
            }

            var received = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= MAX_MODEL_BYTES) { "Download excedeu o limite permitido." }
                        output.write(buffer, 0, count)
                        if (expected > 0) onProgress(((received * 100) / expected).toInt().coerceIn(0, 99))
                    }
                }
            }
            check(received >= 1024L) { "O download terminou vazio ou incompleto." }
            if (expected > 0) check(received == expected) { "Download incompleto: tamanho recebido diferente do esperado." }
            val magic = temporary.inputStream().use { input ->
                val bytes = ByteArray(4)
                input.read(bytes) == 4 && bytes.toString(Charsets.US_ASCII) == "GGUF"
            }
            check(magic) { "Arquivo inválido: cabeçalho GGUF não encontrado." }
            val backup = File(directory, "$name.previous")
            if (backup.exists()) check(backup.delete()) { "Não foi possível limpar o backup antigo." }
            val hadPrevious = destination.exists()
            if (hadPrevious) check(destination.renameTo(backup)) { "Não foi possível preservar a instalação anterior." }
            if (!temporary.renameTo(destination)) {
                if (hadPrevious) backup.renameTo(destination)
                error("Não foi possível finalizar a instalação.")
            }
            if (hadPrevious) backup.delete()
            onProgress(100)
            destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun installedFile(context: Context, model: CatalogModel): File? {
        val name = model.fileName ?: return null
        val file = File(File(context.filesDir, "models"), name)
        if (!file.isFile || file.length() < 1024L) return null
        val valid = runCatching {
            file.inputStream().use { input ->
                val bytes = ByteArray(4)
                input.read(bytes) == 4 && bytes.toString(Charsets.US_ASCII) == "GGUF"
            }
        }.getOrDefault(false)
        return file.takeIf { valid }
    }

    private const val MAX_MODEL_BYTES = 1_000_000_000L
}
