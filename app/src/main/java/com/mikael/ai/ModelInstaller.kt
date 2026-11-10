package com.mikael.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
        require(URL(address).host.equals("huggingface.co", ignoreCase = true)) {
            "A origem do modelo não está na lista de servidores permitidos."
        }
        require(name.matches(Regex("^[A-Za-z0-9._-]+\\.gguf$", RegexOption.IGNORE_CASE))) {
            "Nome de arquivo GGUF inválido."
        }

        val directory = File(context.filesDir, "models").apply {
            check(mkdirs() || isDirectory) { "Não foi possível criar a pasta de modelos." }
        }
        val destination = File(directory, name)
        val temporary = File(directory, "$name.part")
        val checksumFile = File(directory, "$name.sha256")
        val checksumTemporary = File(directory, "$name.sha256.part")
        val backup = File(directory, "$name.previous")
        val checksumBackup = File(directory, "$name.sha256.previous")
        var activeConnection: HttpURLConnection? = null
        val downloadJob = currentCoroutineContext()[Job]
        downloadJob?.invokeOnCompletion { cause -> if (cause != null) activeConnection?.disconnect() }

        try {
            var currentUrl = URL(address)
            var redirects = 0
            var remoteSha256: String? = null
            while (true) {
                val next = (currentUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 15_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MikaelAI-Android")
                }
                activeConnection = next
                next.connect()
                val status = next.responseCode
                if (status in 300..399) {
                    remoteSha256 = remoteSha256 ?: ModelIntegrity.normalizeExpectedSha256(
                        next.getHeaderField("X-Linked-Etag")
                    )
                    val location = next.getHeaderField("Location")
                        ?: error("Redirecionamento Hugging Face sem destino.")
                    val target = URL(currentUrl, location)
                    check(target.protocol.equals("https", ignoreCase = true) &&
                        target.userInfo == null && (target.port == -1 || target.port == 443) &&
                        isAllowedRedirectHost(target.host)) {
                        "O servidor tentou redirecionar o download para uma origem não permitida."
                    }
                    redirects++
                    check(redirects <= 5) { "O download excedeu o limite de redirecionamentos." }
                    next.disconnect()
                    currentUrl = target
                    continue
                }
                check(status in 200..299) {
                    "Servidor recusou o download (HTTP $status)."
                }
                remoteSha256 = remoteSha256 ?: ModelIntegrity.normalizeExpectedSha256(
                    next.getHeaderField("X-Linked-Etag")
                )
                break
            }
            val connection = requireNotNull(activeConnection)
            val expected = connection.contentLengthLong
            check(expected <= MAX_MODEL_BYTES) { "O arquivo excede o limite de 1 GB." }
            val pinnedSha256 = model.expectedSha256?.lowercase()
            check(pinnedSha256 == null || ModelIntegrity.isSha256(pinnedSha256)) {
                "O SHA-256 fixado no catálogo é inválido."
            }
            if (pinnedSha256 != null && remoteSha256 != null) {
                check(pinnedSha256 == remoteSha256) {
                    "O hash publicado pela origem mudou. O download foi cancelado."
                }
            }
            val expectedSha256 = pinnedSha256 ?: remoteSha256
            check(expectedSha256 != null) {
                "O servidor não forneceu um SHA-256 verificável. O download foi cancelado por segurança."
            }
            val requiredSpace = (if (expected > 0) expected else model.approximateBytes) +
                32L * 1024 * 1024
            check(directory.usableSpace > requiredSpace) {
                "Espaço insuficiente. Libere armazenamento antes de instalar."
            }

            var received = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        currentCoroutineContext().ensureActive()
                        if (count < 0) break
                        received += count
                        check(received <= MAX_MODEL_BYTES) { "Download excedeu o limite permitido." }
                        output.write(buffer, 0, count)
                        if (expected > 0) {
                            val percent = ((received * 100) / expected).toInt().coerceIn(0, 99)
                            if (percent != lastProgress) {
                                lastProgress = percent
                                withContext(Dispatchers.Main.immediate) { onProgress(percent) }
                            }
                        }
                    }
                }
            }
            check(received >= 1024L) { "O download terminou vazio ou incompleto." }
            if (expected > 0) {
                check(received == expected) { "Download incompleto: tamanho recebido diferente do esperado." }
            }
            val magic = temporary.inputStream().use { input ->
                val bytes = ByteArray(4)
                input.read(bytes) == 4 && bytes.toString(Charsets.US_ASCII) == "GGUF"
            }
            check(magic) { "Arquivo inválido: cabeçalho GGUF não encontrado." }

            val actualSha256 = ModelIntegrity.sha256(temporary)
            check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "A verificação SHA-256 do modelo falhou. O arquivo foi descartado."
            }
            checksumTemporary.writeText(actualSha256)

            if (backup.exists()) check(backup.delete()) { "Não foi possível limpar o backup antigo." }
            if (checksumBackup.exists()) check(checksumBackup.delete()) { "Não foi possível limpar o backup de integridade." }
            val hadPrevious = destination.exists()
            val hadChecksum = checksumFile.exists()
            if (hadPrevious) check(destination.renameTo(backup)) {
                "Não foi possível preservar a instalação anterior."
            }
            if (hadChecksum && !checksumFile.renameTo(checksumBackup)) {
                if (hadPrevious) backup.renameTo(destination)
                error("Não foi possível preservar o checksum anterior.")
            }

            var promotedModel = false
            var promotedChecksum = false
            try {
                check(temporary.renameTo(destination)) { "Não foi possível finalizar a instalação do modelo." }
                promotedModel = true
                check(checksumTemporary.renameTo(checksumFile)) {
                    "Não foi possível salvar o checksum do modelo."
                }
                promotedChecksum = true
            } catch (error: Exception) {
                if (promotedModel) destination.delete()
                if (promotedChecksum) checksumFile.delete()
                if (hadPrevious && backup.exists()) backup.renameTo(destination)
                if (hadChecksum && checksumBackup.exists()) checksumBackup.renameTo(checksumFile)
                throw error
            }
            backup.delete()
            checksumBackup.delete()
            withContext(Dispatchers.Main.immediate) { onProgress(100) }
            destination
        } catch (error: Exception) {
            temporary.delete()
            checksumTemporary.delete()
            throw error
        } finally {
            activeConnection?.disconnect()
        }
    }

    internal fun isAllowedRedirectHost(host: String): Boolean {
        val normalized = host.lowercase(java.util.Locale.ROOT)
        return normalized == "huggingface.co" ||
            normalized.endsWith(".huggingface.co") ||
            normalized.endsWith(".xethub.hf.co")
    }

    fun installedFile(context: Context, model: CatalogModel): File? {
        if (model.engine != ModelEngine.LLAMA_CPP) return null
        val name = model.fileName ?: return null
        val directory = File(context.filesDir, "models")
        val file = File(directory, name)
        val checksum = File(directory, "$name.sha256")
        if (!file.isFile || file.length() < 1024L || !checksum.isFile) return null
        val checksumText = runCatching { checksum.readText().trim() }.getOrNull() ?: return null
        if (!ModelIntegrity.isSha256(checksumText)) return null
        val validHeader = runCatching {
            file.inputStream().use { input ->
                val bytes = ByteArray(4)
                input.read(bytes) == 4 && bytes.toString(Charsets.US_ASCII) == "GGUF"
            }
        }.getOrDefault(false)
        return file.takeIf { validHeader }
    }

    suspend fun verifyInstalledFile(file: File): Boolean = withContext(Dispatchers.IO) {
        val checksum = File(file.parentFile, "${file.name}.sha256")
        val expected = runCatching { checksum.readText().trim() }.getOrNull() ?: return@withContext false
        file.isFile && checksum.isFile && ModelIntegrity.matches(file, expected)
    }

    private const val MAX_MODEL_BYTES = 1_000_000_000L
}
