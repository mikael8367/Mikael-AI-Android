package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun catalogContainsFourUniqueModels() {
        assertEquals(4, ModelCatalog.models.size)
        assertEquals(4, ModelCatalog.models.map { it.id }.toSet().size)
    }

    @Test
    fun mikaelModelUsesOnlyItsOwnEngineAndHasNoThirdPartyDownload() {
        val model = ModelCatalog.models.single { it.id == "mikael-base" }
        assertEquals(ModelEngine.MKAI, model.engine)
        assertTrue(model.downloadUrl!!.startsWith("https://github.com/"))
        assertEquals("mikael-base.mkai", model.fileName)
        assertTrue(model.description.contains("Checkpoint autoral"))
    }

    @Test
    fun atLeastOneRealModelDownloadExceedsTwoHundredMegabytes() {
        val downloadable = ModelCatalog.models.filter { it.engine == ModelEngine.LLAMA_CPP }
        assertTrue(downloadable.all { it.approximateBytes > 200_000_000L })
        val qwen = downloadable.single { it.id == "qwen-0.5b" }
        assertEquals("Apache-2.0", qwen.license)
        assertTrue(qwen.approximateBytes in 450_000_000L..520_000_000L)
    }

    @Test
    fun externalModelsHaveHttpsGgufSourcesAndLlamaEngine() {
        val external = ModelCatalog.models.filter { it.id != "mikael-base" }
        assertEquals(3, external.size)
        external.forEach { model ->
            assertEquals(ModelEngine.LLAMA_CPP, model.engine)
            assertTrue(model.downloadUrl!!.startsWith("https://"))
            assertTrue(model.fileName!!.endsWith(".gguf"))
            assertNotNull(model.sourceUrl)
            assertTrue(model.approximateBytes > 0L)
        }
    }
}
