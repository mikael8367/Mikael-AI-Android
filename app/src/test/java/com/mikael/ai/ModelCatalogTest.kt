package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun catalogContainsFiveUniqueModels() {
        assertEquals(5, ModelCatalog.models.size)
        assertEquals(5, ModelCatalog.models.map { it.id }.toSet().size)
    }

    @Test
    fun mikaelModelUsesOnlyItsOwnEngineAndHasNoThirdPartyDownload() {
        val model = ModelCatalog.models.single { it.id == "mikael-base" }
        assertEquals(ModelEngine.MKAI, model.engine)
        assertEquals(null, model.downloadUrl)
        assertTrue(model.description.contains("pesos treinados"))
    }

    @Test
    fun mikaelAssistantCanBeInstalledAndUsesQwenBaseTransparently() {
        val model = ModelCatalog.models.single { it.id == "mikael-assistant" }
        assertEquals(ModelEngine.LLAMA_CPP, model.engine)
        assertTrue(model.downloadUrl!!.startsWith("https://"))
        assertTrue(model.fileName!!.endsWith(".gguf"))
        assertTrue(model.description.contains("Qwen 2.5 0.5B"))
        assertTrue(model.description.contains("não é um checkpoint treinado do zero"))
    }

    @Test
    fun externalModelsHaveHttpsGgufSourcesAndLlamaEngine() {
        val external = ModelCatalog.models.filter { it.id != "mikael-base" }
        assertEquals(4, external.size)
        external.forEach { model ->
            assertEquals(ModelEngine.LLAMA_CPP, model.engine)
            assertTrue(model.downloadUrl!!.startsWith("https://"))
            assertTrue(model.fileName!!.endsWith(".gguf"))
            assertNotNull(model.sourceUrl)
            assertTrue(model.approximateBytes > 0L)
        }
    }
}
