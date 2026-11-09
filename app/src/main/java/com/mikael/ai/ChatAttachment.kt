package com.mikael.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val path: String,
    val promptContext: String
)

fun importChatAttachments(context: Context, uris: List<Uri>): List<ChatAttachment> {
    require(uris.size <= 5) { "Selecione no máximo 5 arquivos por vez." }
    val directory = File(context.filesDir, "chat_attachments")
    check(directory.exists() || directory.mkdirs()) { "Não foi possível criar a pasta de anexos." }
    return uris.map { uri ->
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: "anexo"
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
                        require(total <= MAX_ATTACHMENT_BYTES) { "$name ultrapassa o limite de 20 MiB." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(temporary.renameTo(destination)) { "Não foi possível salvar $name." }
            val contextText = when {
                mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS -> {
                    require(destination.length() <= MAX_TEXT_BYTES) {
                        "$name foi anexado, mas é grande demais para extrair texto (limite 256 KiB)."
                    }
                    val content = destination.readText(Charsets.UTF_8)
                    "Arquivo de texto $name ($mime):\n" + content
                }
                mime.startsWith("image/") ->
                    "Imagem anexada: $name ($mime). O motor GGUF atual é somente de texto e não consegue visualizar pixels. Não descreva nem invente o conteúdo da imagem."
                mime.startsWith("video/") ->
                    "Vídeo anexado: $name ($mime). O motor GGUF atual não consegue assistir ao vídeo nem extrair quadros/áudio. Não invente o conteúdo."
                mime == "application/pdf" ->
                    "PDF anexado: $name. O arquivo foi guardado localmente, mas esta versão ainda não extrai texto de PDF."
                else ->
                    "Arquivo anexado: $name ($mime). O conteúdo binário não foi interpretado por esta versão."
            }
            ChatAttachment(name, mime, destination.absolutePath, contextText)
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            throw error
        }
    }
}

private const val MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L
private const val MAX_TEXT_BYTES = 256L * 1024L
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "kt", "java", "kts", "gradle", "xml", "json", "yaml", "yml",
    "properties", "toml", "csv", "log", "py", "js", "ts", "html", "css", "cpp", "h"
)
