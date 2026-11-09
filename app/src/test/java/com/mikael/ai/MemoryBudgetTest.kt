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
        assertNull(MemoryBudget.estimateRequiredBytes(1000L, 99))
    }

    @Test
    fun rejectsOverflowingModelEstimate() {
        assertNull(MemoryBudget.estimateRequiredBytes(Long.MAX_VALUE, 2048))
    }

    @Test
    fun negativeAvailableMemoryDoesNotOverflowBudget() {
        assertEquals(0L, MemoryBudget.safeBudgetBytes(Long.MIN_VALUE, 768))
    }
}
