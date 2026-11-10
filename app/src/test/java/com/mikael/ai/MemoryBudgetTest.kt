package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBudgetTest {
    @Test
    fun rejectsUnknownMemoryLimit() {
        assertEquals(0L, MemoryBudget.safeBudgetBytes(2L * 1024 * 1024 * 1024, 900))
    }

    @Test
    fun reservesMemoryForAndroidAndApp() {
        val available = 2L * 1024 * 1024 * 1024
        assertEquals(768L * 1024 * 1024, MemoryBudget.safeBudgetBytes(available, 768))
    }

    @Test
    fun refusesModelWhenFreeMemoryIsTooLow() {
        val result = MemoryBudget.check(
            availableBytes = 700L * 1024 * 1024,
            selectedLimitMiB = 768,
            modelBytes = 400L * 1024 * 1024,
            contextTokens = 2048
        )
        assertFalse(result.allowed)
        assertNotNull(result.reason)
    }

    @Test
    fun qwenCoderEstimateIncludesConservativeRuntimeBuffersAtShortContext() {
        val estimate = MemoryBudget.estimateRequiredBytes(491_000_000L, 512)!!
        val mib = MemoryBudget.mib(estimate)
        assertTrue(mib in 690L..710L)
    }

    @Test
    fun acceptsSmallModelWithinBudget() {
        val result = MemoryBudget.check(
            availableBytes = 2L * 1024 * 1024 * 1024,
            selectedLimitMiB = 1024,
            modelBytes = 250L * 1024 * 1024,
            contextTokens = 1024
        )
        assertTrue(result.allowed)
    }

    @Test
    fun rejectsInvalidContext() {
        assertNull(MemoryBudget.estimateRequiredBytes(1000L, 9000))
    }

    @Test
    fun acceptsSmallMikaelModelOnMidRangeAndroidWithShortGreeting() {
        val result = MemoryBudget.check(
            availableBytes = 1_500L * 1024 * 1024,
            selectedLimitMiB = 768,
            modelBytes = 20L * 1024 * 1024,
            contextTokens = 512
        )
        assertTrue(result.allowed)
        assertTrue(result.estimatedBytes < result.safeBudgetBytes)
    }

    @Test
    fun reportsMemoryEstimateInsteadOfBlamingMessageLength() {
        val result = MemoryBudget.check(
            availableBytes = 500L * 1024 * 1024,
            selectedLimitMiB = 768,
            modelBytes = 300L * 1024 * 1024,
            contextTokens = 512
        )
        assertFalse(result.allowed)
        assertTrue(result.reason!!.contains("Memória estimada"))
        assertTrue(result.reason!!.contains("Orçamento seguro"))
    }

    @Test
    fun rejectsOverflowingModelEstimate() {
        assertNull(MemoryBudget.estimateRequiredBytes(Long.MAX_VALUE, 2048))
    }

    @Test
    fun negativeAvailableMemoryDoesNotOverflowBudget() {
        assertEquals(0L, MemoryBudget.safeBudgetBytes(Long.MIN_VALUE, 768))
    }
    @Test
    fun modelSpecificMinimumMemoryCannotBeBypassedBySmallFileSize() {
        val result = MemoryBudget.check(
            availableBytes = 4L * 1024L * 1024L * 1024L,
            selectedLimitMiB = 2_048,
            modelBytes = 622_000_000L,
            contextTokens = 1_024,
            minimumEstimatedBytes = 3_000_000_000L
        )
        assertFalse(result.allowed)
        assertEquals(3_000_000_000L, result.estimatedBytes)
    }

}
