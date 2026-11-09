package com.mikael.ai

import android.app.ActivityManager

data class MemoryCheck(
    val allowed: Boolean,
    val availableBytes: Long,
    val safeBudgetBytes: Long,
    val estimatedBytes: Long,
    val reason: String? = null
)

object MemoryBudget {
    private const val MIB = 1024L * 1024L
    private const val SYSTEM_RESERVE_MIB = 640L
    private const val ENGINE_OVERHEAD_MIB = 192L
    val allowedLimitsMiB = listOf(512, 768, 1024, 1536, 2048)

    fun availableBytes(manager: ActivityManager): Long {
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.availMem.coerceAtLeast(0L)
    }

    fun safeBudgetBytes(availableBytes: Long, selectedLimitMiB: Int): Long {
        if (selectedLimitMiB !in allowedLimitsMiB) return 0L
        val afterReserve = (availableBytes - SYSTEM_RESERVE_MIB * MIB).coerceAtLeast(0L)
        return minOf(afterReserve, selectedLimitMiB.toLong() * MIB)
    }

    fun estimateRequiredBytes(modelBytes: Long, contextTokens: Int): Long? {
        if (modelBytes <= 0L || contextTokens !in 256..8192) return null
        val modelWithRuntimeMargin = (modelBytes.toDouble() * 1.25).toLong()
        val contextEstimate = contextTokens.toLong() * 64L * 1024L
        return modelWithRuntimeMargin + contextEstimate + ENGINE_OVERHEAD_MIB * MIB
    }

    fun check(availableBytes: Long, selectedLimitMiB: Int, modelBytes: Long, contextTokens: Int): MemoryCheck {
        val budget = safeBudgetBytes(availableBytes, selectedLimitMiB)
        val estimate = estimateRequiredBytes(modelBytes, contextTokens)
            ?: return MemoryCheck(false, availableBytes, budget, 0L, "Arquivo ou contexto inválido.")
        val allowed = budget > 0L && estimate <= budget
        return MemoryCheck(
            allowed = allowed,
            availableBytes = availableBytes,
            safeBudgetBytes = budget,
            estimatedBytes = estimate,
            reason = if (allowed) null else "A estimativa do modelo ultrapassa o orçamento seguro disponível. Tente um GGUF menor ou reduza o contexto."
        )
    }

    fun mib(bytes: Long): Long = bytes / MIB
}
