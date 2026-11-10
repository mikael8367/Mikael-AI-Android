package com.mikael.ai

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MikaelTokenizerTest {
    @Test
    fun byteFallbackRoundTripsPortugueseAndCode() {
        val tokenizer = MikaelTokenizer.train(listOf("Olá, mundo!\nfunção() { println(\"ação\") }"), 256)
        val input = "Olá, mundo!\nfunção() { println(\"ação\") }"
        assertEquals(input, tokenizer.decode(tokenizer.encode(input)))
    }

    @Test
    fun trainingLearnsMergesAndCompressesRepeatedText() {
        val corpus = listOf("programacao programacao programacao", "programacao em kotlin")
        val tokenizer = MikaelTokenizer.train(corpus, targetVocabularySize = 320)
        val encoded = tokenizer.encode("programacao programacao")
        assertTrue(tokenizer.vocabularySize > MikaelTokenizer.BASE_VOCAB_SIZE)
        assertTrue(encoded.size < "programacao programacao".toByteArray(Charsets.UTF_8).size)
        assertEquals("programacao programacao", tokenizer.decode(encoded))
    }

    @Test
    fun sameCorpusProducesSameTokenIds() {
        val corpus = listOf("mikael ai", "mikael ai", "programação")
        val first = MikaelTokenizer.train(corpus, 300)
        val second = MikaelTokenizer.train(corpus, 300)
        assertArrayEquals(first.encode("mikael ai programação"), second.encode("mikael ai programação"))
    }

    @Test
    fun saveAndLoadPreserveVocabularyAndEncoding() {
        val tokenizer = MikaelTokenizer.train(listOf("texto repetido texto repetido", "texto"), 300)
        val file = File.createTempFile("mktok-test", ".mktok")
        try {
            tokenizer.save(file)
            val restored = MikaelTokenizer.load(file)
            assertEquals(tokenizer.vocabularySize, restored.vocabularySize)
            assertArrayEquals(tokenizer.encode("texto repetido"), restored.encode("texto repetido"))
            assertEquals("texto repetido", restored.decode(restored.encode("texto repetido")))
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptedChecksumIsRejected() {
        val tokenizer = MikaelTokenizer.train(listOf("abc abc abc"), 270)
        val file = File.createTempFile("mktok-corrupt", ".mktok")
        try {
            tokenizer.save(file)
            val bytes = file.readBytes()
            bytes[10] = (bytes[10].toInt() xor 0x01).toByte()
            file.writeBytes(bytes)
            try {
                MikaelTokenizer.load(file)
                fail("Tokenizer com checksum inválido deveria ser rejeitado.")
            } catch (_: IllegalArgumentException) {
                // Esperado: validação SHA-256.
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun invalidTokenIdIsRejected() {
        val tokenizer = MikaelTokenizer.train(listOf("abc"), 256)
        try {
            tokenizer.decode(intArrayOf(256))
            fail("ID fora do vocabulário deveria ser rejeitado.")
        } catch (_: IllegalArgumentException) {
            // Esperado.
        }
    }
}
