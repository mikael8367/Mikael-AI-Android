package com.mikael.ai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Tokenizer BPE byte-level autoral da Mikael AI.
 *
 * IDs 0..255 representam bytes UTF-8. Cada merge cria um novo ID estável,
 * começando em 256. Não usa SentencePiece, Hugging Face Tokenizers ou modelos externos.
 */
class MikaelTokenizer private constructor(
    private val merges: List<Pair<Int, Int>>,
    private val tokenBytes: List<ByteArray>
) {
    val vocabularySize: Int get() = tokenBytes.size
    val mergeCount: Int get() = merges.size

    /** Converte texto UTF-8 em IDs usando a ordem determinística de merges treinada. */
    fun encode(text: String): IntArray {
        require(text.length.toLong() <= MAX_DECODE_BYTES) { "Texto de entrada excede o limite de 1 MiB." }
        val raw = text.toByteArray(Charsets.UTF_8)
        require(raw.size.toLong() <= MAX_DECODE_BYTES) { "Texto UTF-8 excede o limite de 1 MiB." }
        if (raw.isEmpty()) return IntArray(0)
        var ids = IntArray(raw.size) { raw[it].toInt() and 0xFF }
        for ((mergeIndex, pair) in merges.withIndex()) {
            val mergedId = BASE_VOCAB_SIZE + mergeIndex
            if (ids.size < 2) break
            val result = IntArray(ids.size)
            var read = 0
            var write = 0
            while (read < ids.size) {
                if (read + 1 < ids.size && ids[read] == pair.first && ids[read + 1] == pair.second) {
                    result[write++] = mergedId
                    read += 2
                } else {
                    result[write++] = ids[read++]
                }
            }
            ids = result.copyOf(write)
        }
        return ids
    }

    /** Reconstrói exatamente os bytes UTF-8 representados pelos IDs. */
    fun decode(ids: IntArray): String {
        require(ids.size.toLong() <= MAX_DECODE_BYTES) { "Quantidade de tokens excede o limite de segurança." }
        var total = 0L
        for (id in ids) {
            require(id in tokenBytes.indices) { "ID de token fora do vocabulário: $id" }
            total += tokenBytes[id].size
            require(total <= MAX_DECODE_BYTES) { "A saída decodificada excede o limite de segurança." }
        }
        val output = ByteArray(total.toInt())
        var offset = 0
        for (id in ids) {
            val token = tokenBytes[id]
            token.copyInto(output, offset)
            offset += token.size
        }
        return output.toString(Charsets.UTF_8)
    }

    /** Salva o vocabulário em formato binário MKTOK v1, com SHA-256. */
    fun save(file: File) {
        require(merges.size <= MAX_MERGES)
        val body = ByteBuffer.allocate(HEADER_BYTES + merges.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        body.put(MAGIC)
        body.putInt(FORMAT_VERSION)
        body.putInt(BASE_VOCAB_SIZE)
        body.putInt(vocabularySize)
        body.putInt(merges.size)
        for ((left, right) in merges) {
            body.putInt(left)
            body.putInt(right)
        }
        val payload = body.array()
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val parent = file.absoluteFile.parentFile
        require(parent == null || parent.exists() || parent.mkdirs()) { "Não foi possível criar a pasta do tokenizer." }
        val temp = File(parent, file.name + ".tmp")
        try {
            temp.outputStream().use { stream ->
                stream.write(payload)
                stream.write(digest)
                stream.fd.sync()
            }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    companion object {
        const val BASE_VOCAB_SIZE = 256
        private const val FORMAT_VERSION = 1
        private const val HEADER_BYTES = 24
        private const val DIGEST_BYTES = 32
        private const val MAX_MERGES = 4096
        private const val MAX_TRAINING_MERGES = 128
        private const val MAX_CORPUS_BYTES = 256L * 1024L
        private const val MAX_DECODE_BYTES = 1024L * 1024L
        private val MAGIC = byteArrayOf(77, 75, 84, 79, 75, 48, 48, 49) // MKTOK001

        /**
         * Treina BPE do zero a partir de textos UTF-8. O desempate usa o menor par
         * (left,right), garantindo o mesmo resultado para o mesmo corpus ordenado.
         */
        fun train(
            corpus: List<String>,
            targetVocabularySize: Int = 1024,
            minimumPairFrequency: Int = 2
        ): MikaelTokenizer {
            require(targetVocabularySize in BASE_VOCAB_SIZE..(BASE_VOCAB_SIZE + MAX_TRAINING_MERGES)) {
                "O treinamento no celular aceita de 256 a ${BASE_VOCAB_SIZE + MAX_TRAINING_MERGES} tokens; arquivos importados podem ter até ${BASE_VOCAB_SIZE + MAX_MERGES}."
            }
            require(minimumPairFrequency >= 2) { "A frequência mínima precisa ser pelo menos 2." }
            require(corpus.isNotEmpty()) { "O corpus de treinamento está vazio." }
            var totalBytes = 0L
            val sequences = corpus.map { text ->
                require(text.length.toLong() <= MAX_CORPUS_BYTES) { "Uma amostra do corpus excede o limite de 256 KiB." }
                val bytes = text.toByteArray(Charsets.UTF_8)
                totalBytes += bytes.size
                require(totalBytes <= MAX_CORPUS_BYTES) { "Corpus maior que 256 KiB; divida o treinamento em conjuntos menores." }
                IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
            }.filter { it.isNotEmpty() }.toMutableList()
            require(sequences.isNotEmpty()) { "O corpus não contém texto UTF-8." }

            val merges = ArrayList<Pair<Int, Int>>(targetVocabularySize - BASE_VOCAB_SIZE)
            val tokenBytes = ArrayList<ByteArray>(targetVocabularySize)
            repeat(BASE_VOCAB_SIZE) { tokenBytes.add(byteArrayOf(it.toByte())) }
            var totalTokenBytes = BASE_VOCAB_SIZE.toLong()

            while (BASE_VOCAB_SIZE + merges.size < targetVocabularySize) {
                val counts = HashMap<Long, Int>()
                for (sequence in sequences) {
                    for (i in 0 until sequence.size - 1) {
                        val key = pairKey(sequence[i], sequence[i + 1])
                        counts[key] = (counts[key] ?: 0) + 1
                    }
                }
                var bestKey = -1L
                var bestCount = 0
                for ((key, count) in counts) {
                    if (count > bestCount || (count == bestCount && count >= minimumPairFrequency && (bestKey < 0L || key < bestKey))) {
                        bestKey = key
                        bestCount = count
                    }
                }
                if (bestKey < 0L || bestCount < minimumPairFrequency) break

                val left = (bestKey ushr 32).toInt()
                val right = bestKey.toInt()
                val newId = BASE_VOCAB_SIZE + merges.size
                merges.add(left to right)
                val leftBytes = tokenBytes[left]
                val rightBytes = tokenBytes[right]
                val mergedLength = leftBytes.size.toLong() + rightBytes.size.toLong()
                totalTokenBytes += mergedLength
                require(mergedLength <= MAX_DECODE_BYTES && totalTokenBytes <= MAX_DECODE_BYTES) {
                    "O vocabulário treinado excede o limite de memória de 1 MiB."
                }
                tokenBytes.add(leftBytes + rightBytes)
                for (index in sequences.indices) sequences[index] = replacePair(sequences[index], left, right, newId)
            }
            return MikaelTokenizer(merges.toList(), tokenBytes.toList())
        }

        /** Abre e valida rigorosamente um arquivo MKTOK001. */
        fun load(file: File): MikaelTokenizer {
            require(file.isFile) { "Arquivo de tokenizer não encontrado." }
            val fileLength = file.length()
            require(fileLength >= HEADER_BYTES + DIGEST_BYTES) { "Arquivo MKTOK incompleto." }
            require(fileLength <= HEADER_BYTES + MAX_MERGES * 8L + DIGEST_BYTES) { "Arquivo MKTOK excede o limite." }
            val bytes = file.readBytes()
            val payloadSize = bytes.size - DIGEST_BYTES
            val payload = bytes.copyOfRange(0, payloadSize)
            val expectedDigest = MessageDigest.getInstance("SHA-256").digest(payload)
            val actualDigest = bytes.copyOfRange(payloadSize, bytes.size)
            require(MessageDigest.isEqual(expectedDigest, actualDigest)) { "Checksum SHA-256 do tokenizer inválido." }

            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(8)
            buffer.get(magic)
            require(magic.contentEquals(MAGIC)) { "Assinatura MKTOK inválida." }
            require(buffer.int == FORMAT_VERSION) { "Versão MKTOK não suportada." }
            require(buffer.int == BASE_VOCAB_SIZE) { "Vocabulário-base incompatível." }
            val vocabularySize = buffer.int
            val mergeCount = buffer.int
            require(mergeCount in 0..MAX_MERGES && vocabularySize == BASE_VOCAB_SIZE + mergeCount) {
                "Dimensões do vocabulário MKTOK inválidas."
            }
            require(payload.size == HEADER_BYTES + mergeCount * 8) { "Tamanho do arquivo MKTOK inconsistente." }

            val merges = ArrayList<Pair<Int, Int>>(mergeCount)
            val tokenBytes = ArrayList<ByteArray>(vocabularySize)
            repeat(BASE_VOCAB_SIZE) { tokenBytes.add(byteArrayOf(it.toByte())) }
            var totalTokenBytes = BASE_VOCAB_SIZE.toLong()
            repeat(mergeCount) {
                val left = buffer.int
                val right = buffer.int
                val newId = BASE_VOCAB_SIZE + it
                require(left in 0 until newId && right in 0 until newId) { "Referência inválida na tabela de merges." }
                merges.add(left to right)
                val mergedLength = tokenBytes[left].size.toLong() + tokenBytes[right].size.toLong()
                totalTokenBytes += mergedLength
                require(mergedLength <= MAX_DECODE_BYTES && totalTokenBytes <= MAX_DECODE_BYTES) {
                    "O vocabulário MKTOK excede o limite seguro de memória."
                }
                tokenBytes.add(tokenBytes[left] + tokenBytes[right])
            }
            return MikaelTokenizer(merges, tokenBytes)
        }

        private fun pairKey(left: Int, right: Int): Long =
            (left.toLong() shl 32) or (right.toLong() and 0xFFFFFFFFL)

        private fun replacePair(source: IntArray, left: Int, right: Int, newId: Int): IntArray {
            val output = IntArray(source.size)
            var read = 0
            var write = 0
            while (read < source.size) {
                if (read + 1 < source.size && source[read] == left && source[read + 1] == right) {
                    output[write++] = newId
                    read += 2
                } else {
                    output[write++] = source[read++]
                }
            }
            return output.copyOf(write)
        }
    }
}
