package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun catalogHasUniqueIdsAndFileNames() {
        val models = ModelCatalog.models
        assertTrue(models.isNotEmpty())
        assertEquals(models.size, models.map { it.id }.toSet().size)
        assertEquals(models.size, models.map { it.fileName }.toSet().size)
    }

    @Test
    fun everyCatalogEntryHasVerifiedHttpsGgufMetadata() {
        ModelCatalog.models.forEach { model ->
            assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
            assertTrue(model.fileName.endsWith(".gguf"))
            assertEquals(64, model.sha256.length)
            assertTrue(model.sha256.all { it in "0123456789abcdef" })
            assertTrue(model.expectedBytes > 100L * 1024L * 1024L)
        }
    }

    @Test
    fun codingModelIsIncluded() {
        assertTrue(ModelCatalog.models.any { it.category == "Programação" })
    }
}
