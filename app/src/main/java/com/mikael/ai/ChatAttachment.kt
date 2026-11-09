package com.mikael.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.concurrent.TimeUnit

data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val path: String,
    val promptContext: String
)

fun importChatAttachments(context: Context, uris: List<Uri>): List<ChatAttachment> {
    require(uris.isNotEmpty()) { "Selecione pelo menos um arquivo." }
    require(uris.size <= 5) { "Selecione no máximo 5 arquivos por vez." }
    val directory = File(context.filesDir, "chat_attachments")
    check(directory.exists() || directory.mkdirs()) { "Não foi possível criar a pasta de anexos." }

    var remainingAnalysisBytes = MAX_TOTAL_ANALYSIS_BYTES
    return uris.map { uri ->
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val metadata = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                (if (nameIndex >= 0) cursor.getString(nameIndex) else null) to
                    (if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L)
            } else null
        }
        val name = metadata?.first ?: "anexo"
        val declaredSize = metadata?.second ?: -1L
        require(declaredSize <= MAX_ATTACHMENT_BYTES) {
            "$name ultrapassa o limite de 1 GB por arquivo."
        }
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        val destination = File(directory, "${System.currentTimeMillis()}_$safeName")
        val temporary = File(directory, "${destination.name}.partial")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Não foi possível abrir $name." }
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_ATTACHMENT_BYTES) {
                            "$name ultrapassa o limite de 1 GB por arquivo."
                        }
                        if (total % SPACE_CHECK_INTERVAL < read) {
                            ensureStorageSpace(context, total)
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(temporary.renameTo(destination)) { "Não foi possível salvar $name." }
            val extracted = extractAttachmentText(context, destination, mime, name)
            val availableForThisFile = remainingAnalysisBytes.coerceAtLeast(0)
            val safeExtracted = takeUtf8Prefix(extracted, availableForThisFile)
            remainingAnalysisBytes -= safeExtracted.toByteArray(Charsets.UTF_8).size
            val contextText = if (safeExtracted.isBlank()) {
                "Anexo: $name ($mime). Nenhum texto legível foi extraído."
            } else {
                "Conteúdo extraído de $name ($mime):\n$safeExtracted"
            }
            ChatAttachment(name, mime, destination.absolutePath, contextText)
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            throw error
        }
    }
}

private fun ensureStorageSpace(context: Context, bytesBeingCopied: Long) {
    val stats = StatFs(context.filesDir.absolutePath)
    val freeBytes = stats.availableBytes
    require(freeBytes > MIN_FREE_SPACE_BYTES) {
        "Espaço insuficiente para continuar anexando. Libere armazenamento e tente novamente."
    }
}

private fun extractAttachmentText(context: Context, file: File, mime: String, name: String): String {
    return when {
        mime.startsWith("image/") -> extractImageText(file, name)
        mime.startsWith("video/") -> extractVideoText(context, file, name)
        mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) ->
            extractPdfText(context, file, name)
        mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS -> {
            if (file.length() > MAX_TEXT_BYTES) {
                file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText().take(MAX_ANALYSIS_CHARS_PER_FILE)
                }
            } else file.readText(Charsets.UTF_8).take(MAX_ANALYSIS_CHARS_PER_FILE)
        }
        else -> "O arquivo foi anexado, mas este formato não possui extrator de texto nesta versão."
    }
}

private fun extractImageText(file: File, name: String): String {
    val bitmap = decodeSampledBitmap(file) ?: error("Não foi possível decodificar a imagem $name.")
    return try {
        recognizeText(bitmap).ifBlank {
            "Nenhum texto legível foi encontrado na imagem. Esta versão extrai texto (OCR), mas não faz descrição visual geral de objetos ou cenas."
        }.let { "Texto reconhecido na imagem (OCR):\n$it\nObservação: o modelo local atual é textual; não interprete pixels nem invente objetos ou cenas." }
    } finally {
        bitmap.recycle()
    }
}

private fun extractVideoText(context: Context, file: File, name: String): String {
    val retriever = MediaMetadataRetriever()
    val recognized = StringBuilder()
    try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        require(durationMs > 0L) { "Não foi possível ler a duração do vídeo $name." }
        val frameCount = minOf(MAX_VIDEO_FRAMES, maxOf(1, (durationMs / VIDEO_FRAME_INTERVAL_MS).toInt() + 1))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            for (index in 0 until frameCount) {
                val timeMs = if (frameCount == 1) 0L else
                    (durationMs * index / (frameCount - 1)).coerceIn(0L, durationMs)
                val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                try {
                    val text = recognizeText(frame, recognizer)
                    if (text.isNotBlank()) {
                        recognized.append("\n[Quadro em ").append(timeMs / 1000).append("s]\n")
                            .append(text).append('\n')
                    }
                } finally {
                    frame.recycle()
                }
                if (recognized.length >= MAX_ANALYSIS_CHARS_PER_FILE) break
            }
        } finally {
            recognizer.close()
        }
    } finally {
        retriever.release()
    }
    val result = recognized.toString().take(MAX_ANALYSIS_CHARS_PER_FILE)
    return if (result.isBlank()) {
        "Vídeo anexado. Foram examinados até $MAX_VIDEO_FRAMES quadros, mas nenhum texto foi reconhecido. Não há transcrição de áudio nem compreensão visual geral nesta versão."
    } else {
        "Texto reconhecido em quadros do vídeo (OCR):\n$result\nObservação: não há transcrição de áudio nem descrição visual geral nesta versão."
    }
}

private fun extractPdfText(context: Context, file: File, name: String): String {
    synchronized(PDF_INIT_LOCK) {
        if (!pdfInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfInitialized = true
        }
    }
    PDDocument.load(file).use { document ->
        require(!document.isEncrypted) { "O PDF $name está protegido por senha e não pode ser lido." }
        val pageCount = document.numberOfPages
        val stripper = PDFTextStripper().apply {
            startPage = 1
            endPage = minOf(pageCount, MAX_PDF_PAGES)
        }
        val text = stripper.getText(document).take(MAX_ANALYSIS_CHARS_PER_FILE)
        val note = if (pageCount > MAX_PDF_PAGES) "\n[Leitura limitada às primeiras $MAX_PDF_PAGES páginas de $pageCount.]" else ""
        return if (text.isBlank()) {
            "O PDF não contém texto selecionável. PDFs digitalizados como imagem precisam de OCR por página, não incluído nesta versão."
        } else "Texto extraído do PDF ($pageCount páginas):\n$text$note"
    }
}

private fun decodeSampledBitmap(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > MAX_IMAGE_DIMENSION ||
        bounds.outHeight / sample > MAX_IMAGE_DIMENSION ||
        (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MAX_IMAGE_PIXELS
    ) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    })
}

private fun recognizeText(bitmap: Bitmap): String {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        recognizeText(bitmap, recognizer)
    } finally {
        recognizer.close()
    }
}

private fun recognizeText(bitmap: Bitmap, recognizer: com.google.mlkit.vision.text.TextRecognizer): String {
    val result = Tasks.await(
        recognizer.process(InputImage.fromBitmap(bitmap, 0)),
        OCR_TIMEOUT_SECONDS,
        TimeUnit.SECONDS
    )
    return result.text.take(MAX_ANALYSIS_CHARS_PER_FILE)
}

private fun takeUtf8Prefix(value: String, maxBytes: Int): String {
    if (maxBytes <= 0 || value.isEmpty()) return ""
    var bytes = 0
    var index = 0
    while (index < value.length) {
        val point = value.codePointAt(index)
        val encoded = when {
            point <= 0x7F -> 1
            point <= 0x7FF -> 2
            point <= 0xFFFF -> 3
            else -> 4
        }
        if (bytes + encoded > maxBytes) break
        bytes += encoded
        index += Character.charCount(point)
    }
    return value.substring(0, index)
}

private const val MAX_ATTACHMENT_BYTES = 1024L * 1024L * 1024L
private const val MAX_TEXT_BYTES = 256L * 1024L
private const val MAX_TOTAL_ANALYSIS_BYTES = 12 * 1024
private const val MAX_ANALYSIS_CHARS_PER_FILE = 5000
private const val MAX_PDF_PAGES = 40
private const val MAX_VIDEO_FRAMES = 6
private const val VIDEO_FRAME_INTERVAL_MS = 5000L
private const val MAX_IMAGE_DIMENSION = 1600
private const val MAX_IMAGE_PIXELS = 2_500_000L
private const val OCR_TIMEOUT_SECONDS = 30L
private const val MIN_FREE_SPACE_BYTES = 128L * 1024L * 1024L
private const val SPACE_CHECK_INTERVAL = 16L * 1024L * 1024L
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "kt", "java", "kts", "gradle", "xml", "json", "yaml", "yml",
    "properties", "toml", "csv", "log", "py", "js", "ts", "html", "css", "cpp", "h"
)
private val PDF_INIT_LOCK = Any()
@Volatile private var pdfInitialized = false
