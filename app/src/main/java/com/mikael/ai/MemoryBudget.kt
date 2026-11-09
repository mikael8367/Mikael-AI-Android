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
    private const val SYSTEM_RESERVE_MIB = 448L
    private const val ENGINE_OVERHEAD_MIB = 128L
    val allowedLimitsMiB = listOf(512, 768, 1024, 1536, 2048)

    fun availableBytes(manager: ActivityManager): Long {
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        if (info.lowMemory) return 0L
        return info.availMem.coerceAtLeast(0L)
    }

    fun safeBudgetBytes(availableBytes: Long, selectedLimitMiB: Int): Long {
        if (selectedLimitMiB !in allowedLimitsMiB) return 0L
        val nonNegativeAvailable = availableBytes.coerceAtLeast(0L)
        val afterReserve = (nonNegativeAvailable - SYSTEM_RESERVE_MIB * MIB).coerceAtLeast(0L)
        return minOf(afterReserve, selectedLimitMiB.toLong() * MIB)
    }

    fun estimateRequiredBytes(modelBytes: Long, contextTokens: Int): Long? {
        if (modelBytes <= 0L || contextTokens !in 256..8192) return null
        val modelMargin = modelBytes / 4L
        val contextEstimate = contextTokens.toLong() * 64L * 1024L
        val overhead = ENGINE_OVERHEAD_MIB * MIB
        if (modelBytes > Long.MAX_VALUE - modelMargin) return null
        val withMargin = modelBytes + modelMargin
        if (withMargin > Long.MAX_VALUE - contextEstimate) return null
        val withContext = withMargin + contextEstimate
        if (withContext > Long.MAX_VALUE - overhead) return null
        return withContext + overhead
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
            reason = if (allowed) null else "Memória insuficiente para carregar com segurança: estimativa de ${mib(estimate)} MiB, orçamento disponível de ${mib(budget)} MiB (RAM livre: ${mib(availableBytes)} MiB). Feche outros aplicativos ou reduza o contexto."
        )
    }

    fun mib(bytes: Long): Long = bytes / MIB
}
