package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedModelDownloaderTest {
    @Test
    fun recommendedModelUsesSecureOfficialSource() {
        assertTrue(RecommendedModelDownloader.MODEL_URL.startsWith("https://huggingface.co/"))
        assertTrue(RecommendedModelDownloader.MODEL_URL.contains("Qwen2.5-0.5B-Instruct-GGUF"))
        assertTrue(RecommendedModelDownloader.MODEL_NAME.endsWith(".gguf"))
    }

    @Test
    fun recommendedModelHasPinnedSizeAndSha256() {
        assertEquals(491_400_032L, RecommendedModelDownloader.EXPECTED_BYTES)
        assertEquals(64, RecommendedModelDownloader.EXPECTED_SHA256.length)
        assertTrue(RecommendedModelDownloader.EXPECTED_SHA256.matches(Regex("[0-9a-f]{64}")))
    }
}
