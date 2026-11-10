package com.mikael.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MikaelModelInstallerTest {
    private fun validHeader(): ByteArray =
        ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("MKAI0001".toByteArray(Charsets.US_ASCII))
            putInt(1)    // version
            putInt(16)   // dim
            putInt(1)    // layers
            putInt(1)    // heads
            putInt(16)   // ffn
            putInt(256)  // vocab
            putInt(16)   // max sequence
            putInt(0)    // reserved
        }.array()

    private fun expectedFileSize(): Long {
        val dim = 16L
        val layers = 1L
        val ffn = 16L
        val maxSeq = 16L
        val vocab = 256L
        val floats = vocab * dim + maxSeq * dim +
            layers * (2L * dim + 4L * dim * dim + 3L * ffn * dim) +
            dim + vocab * dim
        return 40L + floats * 4L
    }

    @Test
    fun acceptsValidMkaiHeaderAndExactWeightSize() {
        assertTrue(MikaelModelInstaller.validateHeader(validHeader(), expectedFileSize()))
    }

    @Test
    fun rejectsWrongMagic() {
        val header = validHeader()
        header[0] = 'X'.code.toByte()
        assertFalse(MikaelModelInstaller.validateHeader(header, expectedFileSize()))
    }

    @Test
    fun rejectsFileWithMissingOrExtraWeights() {
        val size = expectedFileSize()
        assertFalse(MikaelModelInstaller.validateHeader(validHeader(), size - 4L))
        assertFalse(MikaelModelInstaller.validateHeader(validHeader(), size + 4L))
    }

    @Test
    fun rejectsUnsafeDimensions() {
        val header = validHeader()
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(12, 15)
        assertFalse(MikaelModelInstaller.validateHeader(header, expectedFileSize()))
    }
}
