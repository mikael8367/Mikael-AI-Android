package com.mikael.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelIntegrityTest {
    @Test
    fun parsesSha256FromHuggingFaceHeaders() {
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(expected, ModelIntegrity.normalizeExpectedSha256("\"$expected\""))
        assertEquals(expected, ModelIntegrity.normalizeExpectedSha256("W/\"$expected\""))
        assertNull(ModelIntegrity.normalizeExpectedSha256("multipart-etag-123"))
    }

    @Test
    fun verifiesFileDigestAndRejectsTampering() {
        val file = File.createTempFile("mikael-model-", ".bin")
        try {
            file.writeText("hello")
            val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
            assertTrue(ModelIntegrity.matches(file, expected))
            file.writeText("changed")
            assertFalse(ModelIntegrity.matches(file, expected))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsMalformedChecksumValues() {
        assertFalse(ModelIntegrity.isSha256("not-a-hash"))
        assertFalse(ModelIntegrity.isSha256("0".repeat(63)))
        assertTrue(ModelIntegrity.isSha256("A".repeat(64)))
    }
}
