package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun qwenIsTheOnlyAvailableConversationModel() {
        assertEquals(1, ModelCatalog.models.size)
        val model = ModelCatalog.models.single()
        assertEquals("qwen-0.5b", model.id)
        assertEquals(ModelEngine.LLAMA_CPP, model.engine)
        assertEquals("Qwen2.5-Coder 0.5B Instruct", model.name)
    }

    @Test
    fun qwenHasPinnedApacheLicensedGgufSource() {
        val qwen = ModelCatalog.models.single()
        assertEquals("Apache-2.0", qwen.license)
        assertTrue(qwen.downloadUrl!!.startsWith("https://huggingface.co/"))
        assertTrue(qwen.fileName!!.endsWith(".gguf"))
        assertTrue(qwen.approximateBytes in 450_000_000L..520_000_000L)
        assertTrue(ModelIntegrity.isSha256(qwen.expectedSha256.orEmpty()))
    }

    @Test
    fun catalogDoesNotExposeExperimentalMkaiOrOtherFallbackModels() {
        assertTrue(ModelCatalog.models.none { it.engine == ModelEngine.MKAI })
        assertTrue(ModelCatalog.models.none { it.id == "smollm2-360m" || it.id == "tinyllama-1.1b" })
    }
}
