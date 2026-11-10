package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun catalogContainsExactlyTheFourRequestedParticipants() {
        assertEquals(4, ModelCatalog.models.size)
        assertEquals(
            listOf("mikael-base", "qwen-0.5b", "smollm2-360m", "tinyllama-1.1b"),
            ModelCatalog.models.map { it.id }
        )
        assertEquals(4, ModelCatalog.teamParticipants.size)
        assertEquals(ModelCatalog.TEAM_MODEL_ID, "mikael-ai-4")
    }

    @Test
    fun qwenUsesPinnedApacheLicensedGguf() {
        val qwen = ModelCatalog.models.single { it.id == "qwen-0.5b" }
        assertEquals(ModelEngine.LLAMA_CPP, qwen.engine)
        assertEquals("Apache-2.0", qwen.license)
        assertTrue(qwen.fileName!!.endsWith(".gguf"))
        assertTrue(ModelIntegrity.isSha256(qwen.expectedSha256.orEmpty()))
    }

    @Test
    fun smolAndTinyLlamaUseGgufAndApacheLicense() {
        listOf("smollm2-360m", "tinyllama-1.1b").forEach { id ->
            val model = ModelCatalog.models.single { it.id == id }
            assertEquals(ModelEngine.LLAMA_CPP, model.engine)
            assertEquals("Apache-2.0", model.license)
            assertTrue(model.fileName!!.endsWith(".gguf"))
            assertTrue(model.downloadUrl!!.startsWith("https://huggingface.co/"))
            assertTrue(model.approximateBytes > 200_000_000L)
        }
    }

    @Test
    fun mikaelCheckpointIsExplicitlyMarkedAsUnlicensedAndMkai() {
        val model = ModelCatalog.models.single { it.id == "mikael-base" }
        assertEquals(ModelEngine.MKAI, model.engine)
        assertEquals("Licença não declarada", model.license)
        assertTrue(model.fileName!!.endsWith(".mkai"))
        assertTrue(model.downloadUrl!!.startsWith("https://github.com/"))
        assertTrue(ModelIntegrity.isSha256(model.expectedSha256.orEmpty()))
    }

    @Test
    fun teamStorageEstimateIncludesAllFourWeights() {
        assertEquals(ModelCatalog.models.sumOf { it.approximateBytes }, ModelCatalog.teamDownloadBytes)
        assertTrue(ModelCatalog.teamDownloadBytes in 1_300_000_000L..1_500_000_000L)
    }
}
