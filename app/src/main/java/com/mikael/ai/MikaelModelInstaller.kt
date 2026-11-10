package com.mikael.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Downloads and validates the original MKAI checkpoint published by the training workflow. */
object MikaelModelInstaller {
    private const val HEADER_BYTES = 40L
    private const val MAX_MODEL_BYTES = 240_000_040L
    private const val RELEASE_NOT_FOUND =
        "O primeiro checkpoint Mikael AI ainda não foi publicado. Treine o modelo pelo botão Treinar modelo e tente novamente."

    suspend fun install(
        context: Context,
        model: CatalogModel,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(model.engine == ModelEngine.MKAI) { "Este instalador aceita somente modelos MKAI." }
        val address = requireNotNull(model.downloadUrl)
        val name = requireNotNull(model.fileName)
        require(address.startsWith("https://") && URL(address).host.equals("github.com", true)) {
            "A origem do checkpoint Mikael AI não é confiável."
        }
        require(name.matches(Regex("^[A-Za-z0-9._-]+\\.mkai$", RegexOption.IGNORE_CASE))) {
            "Nome de arquivo MKAI inválido."
        }

        val directory = File(context.filesDir, "models").apply {
            check(mkdirs() || isDirectory) { "Não foi possível criar a pasta de modelos." }
        }
        val destination = File(directory, name)
        val temporary = File(directory, "$name.part")
        val checksum = File(directory, "$name.sha256")
        val checksumTemporary = File(directory, "$name.sha256.part")
        val backup = File(directory, "$name.previous")
        val checksumBackup = File(directory, "$name.sha256.previous")
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MikaelAI-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) error(RELEASE_NOT_FOUND)
            check(connection.responseCode in 200..299) {
                "O servidor recusou o checkpoint (HTTP ${connection.responseCode})."
            }
            val expectedLength = connection.contentLengthLong
            check(expectedLength <= MAX_MODEL_BYTES) { "O checkpoint excede o limite de 240 MB." }
            val requiredSpace = (if (expectedLength > 0L) expectedLength else 8L * 1024 * 1024) +
                8L * 1024 * 1024
            check(directory.usableSpace > requiredSpace) {
                "Espaço insuficiente para instalar o modelo Mikael AI."
            }

            var received = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= MAX_MODEL_BYTES) { "Download do MKAI excedeu o limite permitido." }
                        output.write(buffer, 0, count)
                        if (expectedLength > 0L) {
                            onProgress(((received * 100L) / expectedLength).toInt().coerceIn(0, 99))
                        }
                    }
                }
            }
            check(received >= HEADER_BYTES) { "O download MKAI terminou vazio ou incompleto." }
            if (expectedLength > 0L) {
                check(received == expectedLength) { "Download MKAI incompleto: tamanho recebido diferente do esperado." }
            }
            check(isValidMkai(temporary)) {
                "O arquivo baixado não é um checkpoint MKAI válido ou está incompleto."
            }

            val digest = ModelIntegrity.sha256(temporary)
            checksumTemporary.writeText(digest)

            if (backup.exists()) check(backup.delete()) { "Não foi possível limpar o backup MKAI antigo." }
            if (checksumBackup.exists()) check(checksumBackup.delete()) { "Não foi possível limpar o backup de integridade antigo." }
            val hadPrevious = destination.exists()
            val hadChecksum = checksum.exists()
            if (hadPrevious) check(destination.renameTo(backup)) {
                "Não foi possível preservar o modelo Mikael AI anterior."
            }
            if (hadChecksum && !checksum.renameTo(checksumBackup)) {
                if (hadPrevious) backup.renameTo(destination)
                error("Não foi possível preservar o checksum anterior.")
            }

            var promotedModel = false
            try {
                check(temporary.renameTo(destination)) { "Não foi possível finalizar a instalação MKAI." }
                promotedModel = true
                check(checksumTemporary.renameTo(checksum)) { "Não foi possível salvar o checksum MKAI." }
            } catch (error: Exception) {
                if (promotedModel) destination.delete()
                if (hadPrevious && backup.exists()) backup.renameTo(destination)
                if (hadChecksum && checksumBackup.exists()) checksumBackup.renameTo(checksum)
                throw error
            }
            backup.delete()
            checksumBackup.delete()
            onProgress(100)
            destination
        } catch (error: Exception) {
            temporary.delete()
            checksumTemporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun isValidMkai(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() !in HEADER_BYTES..MAX_MODEL_BYTES) return@runCatching false
        val header = ByteArray(HEADER_BYTES.toInt())
        file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) return@runCatching false
                offset += count
            }
        }
        validateHeader(header, file.length())
    }.getOrDefault(false)

    suspend fun verifyInstalledFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!isValidMkai(file)) return@withContext false
        val checksum = File(file.parentFile, "${file.name}.sha256")
        if (!checksum.exists()) {
            // Migra modelos importados por versões antigas que ainda não guardavam checksum.
            return@withContext runCatching {
                checksum.writeText(ModelIntegrity.sha256(file))
                true
            }.getOrDefault(false)
        }
        val expected = runCatching { checksum.readText().trim() }.getOrNull() ?: return@withContext false
        ModelIntegrity.matches(file, expected)
    }

    internal fun validateHeader(header: ByteArray, fileSize: Long): Boolean {
        if (header.size != HEADER_BYTES.toInt() || fileSize !in HEADER_BYTES..MAX_MODEL_BYTES) return false
        val magic = "MKAI0001".toByteArray(Charsets.US_ASCII)
        if (!header.copyOfRange(0, 8).contentEquals(magic)) return false
        val data = ByteBuffer.wrap(header, 8, 32).order(ByteOrder.LITTLE_ENDIAN)
        val version = data.int
        val dim = data.int
        val layers = data.int
        val heads = data.int
        val ffn = data.int
        val vocab = data.int
        val maxSeq = data.int
        val reserved = data.int
        if (version != 1 || reserved != 0 || dim !in 16..1024 || layers !in 1..16 ||
            heads !in 1..32 || dim % heads != 0 || ffn !in dim..4096 ||
            vocab != 256 || maxSeq !in 16..8192
        ) return false

        val d = dim.toLong()
        val f = ffn.toLong()
        val s = maxSeq.toLong()
        val l = layers.toLong()
        val v = vocab.toLong()
        val floatCount = v * d + s * d +
            l * (2L * d + 4L * d * d + 3L * f * d) + d + v * d
        val expectedBytes = HEADER_BYTES + floatCount * 4L
        return fileSize == expectedBytes
    }
}
