package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ResumeModelResolutionTest {

    private fun installerOf(installed: Set<String>): (String) -> File? {
        val files = installed.associateWith { id ->
            File.createTempFile("mikael-test-" + id.replace("-", "_") + "-", ".gguf").apply { deleteOnExit() }
        }
        return { id -> files[id] }
    }

    @Test
    fun teamTaskAlwaysResumesWithTheTeamModel() {
        assertEquals(
            ModelCatalog.TEAM_MODEL_ID,
            resolveResumableModelId(ModelCatalog.TEAM_MODEL_ID, installerOf(emptySet()), mikaelInstalled = false)
        )
    }

    @Test
    fun savedModelIsKeptWhenItIsInstalled() {
        assertEquals(
            "qwen-0.5b",
            resolveResumableModelId("qwen-0.5b", installerOf(setOf("qwen-0.5b")), mikaelInstalled = false)
        )
        assertEquals(
            "mikael-base",
            resolveResumableModelId("mikael-base", installerOf(emptySet()), mikaelInstalled = true)
        )
    }

    @Test
    fun unknownSavedModelFallsBackToAnyInstalledCatalogModel() {
        assertEquals(
            "smollm2-360m",
            resolveResumableModelId("modelo-inexistente", installerOf(setOf("smollm2-360m")), mikaelInstalled = false)
        )
    }

    @Test
    fun uninstalledSavedModelFallsBackToAnotherInstalledGguf() {
        assertEquals(
            "tinyllama-1.1b",
            resolveResumableModelId("qwen-0.5b", installerOf(setOf("tinyllama-1.1b")), mikaelInstalled = false)
        )
    }

    @Test
    fun fallbackPrefersGgufModelsAndUsesMikaelOnlyAsLastOption() {
        assertEquals(
            "smollm2-360m",
            resolveResumableModelId("qwen-0.5b", installerOf(setOf("smollm2-360m")), mikaelInstalled = true)
        )
        assertEquals(
            "mikael-base",
            resolveResumableModelId("qwen-0.5b", installerOf(emptySet()), mikaelInstalled = true)
        )
    }

    @Test
    fun returnsNullWhenNothingUsableIsInstalled() {
        assertNull(resolveResumableModelId("qwen-0.5b", installerOf(emptySet()), mikaelInstalled = false))
        assertNull(resolveResumableModelId(null, installerOf(setOf("qwen-0.5b")), mikaelInstalled = true))
    }

}