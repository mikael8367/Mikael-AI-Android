package com.mikael.ai

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

object RecommendedModelDownloader {
    const val MODEL_NAME = "mikael-ai-qwen2.5-0.5b-q4_k_m.gguf"
    const val EXPECTED_BYTES = 491_400_032L
    const val EXPECTED_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/12145bd1d629190a4d44254073650877954d02c9/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true"

    private val cancellationGeneration = AtomicLong(0L)
    private val downloadMutex = Mutex()

    fun cancel() {
        cancellationGeneration.incrementAndGet()
    }

    suspend fun download(directory: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        val requestGeneration = cancellationGeneration.get()
        downloadMutex.lock()
        try {
            val coroutineContext = currentCoroutineContext()
            coroutineContext.ensureActive()
            check(cancellationGeneration.get() == requestGeneration) { "Download cancelado." }

            check(directory.exists() || directory.mkdirs()) {
                "Não foi possível criar a pasta de modelos."
            }
            val destination = File(directory, MODEL_NAME)
            if (destination.isFile && destination.length() == EXPECTED_BYTES &&
                sha256(destination) == EXPECTED_SHA256
            ) {
                coroutineContext.ensureActive()
                return destination
            }
            check(directory.usableSpace >= EXPECTED_BYTES + 32L * 1024L * 1024L) {
                "Espaço insuficiente. Libere pelo menos 501 MiB para baixar o modelo."
            }

            val partial = File(directory, "$MODEL_NAME.part")
            partial.delete()
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MikaelAI-Android")
            }

            try {
                coroutineContext.ensureActive()
                connection.connect()
                check(connection.responseCode in 200..299) {
                    "O servidor do modelo respondeu HTTP ${connection.responseCode}."
                }
                val declaredLength = connection.contentLengthLong
                check(declaredLength == -1L || declaredLength == EXPECTED_BYTES) {
                    "O tamanho informado pelo servidor não corresponde ao modelo esperado."
                }

                var downloaded = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            check(cancellationGeneration.get() == requestGeneration) { "Download cancelado." }
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            check(downloaded <= EXPECTED_BYTES) {
                                "O arquivo recebido é maior que o modelo esperado."
                            }
                            output.write(buffer, 0, count)
                            if (downloaded % (1024L * 1024L) < count) {
                                onProgress(downloaded, EXPECTED_BYTES)
                            }
                        }
                        output.fd.sync()
                    }
                }

                coroutineContext.ensureActive()
                check(cancellationGeneration.get() == requestGeneration) { "Download cancelado." }
                check(downloaded == EXPECTED_BYTES) {
                    "Download incompleto: recebidos ${downloaded / (1024 * 1024)} MiB de ${EXPECTED_BYTES / (1024 * 1024)} MiB."
                }
                check(sha256(partial) == EXPECTED_SHA256) {
                    "A verificação de integridade SHA-256 falhou. O arquivo foi descartado."
                }
                coroutineContext.ensureActive()
                check(cancellationGeneration.get() == requestGeneration) { "Download cancelado." }
                if (destination.exists()) destination.delete()
                check(partial.renameTo(destination)) {
                    "Não foi possível finalizar o arquivo do modelo."
                }
                coroutineContext.ensureActive()
                onProgress(EXPECTED_BYTES, EXPECTED_BYTES)
                return destination
            } catch (error: Exception) {
                partial.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        } finally {
            downloadMutex.unlock()
        }
    }

    private suspend fun sha256(file: File): String {
        val coroutineContext = currentCoroutineContext()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(Locale.ROOT, it.toInt() and 0xff)
        }
    }
}
