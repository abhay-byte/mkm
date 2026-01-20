package com.ivarna.mkm.data.model

data class MemoryStatus(
    val totalUi: String = "0 B",
    val usedUi: String = "0 B",
    val freeUi: String = "0 B",
    val usagePercent: Float = 0f,
    val rawTotal: Long = 0,
    val rawUsed: Long = 0,
    val availableUi: String = "0 B",
    val cachedUi: String = "0 B",
    val activeUi: String = "0 B",
    val inactiveUi: String = "0 B",
    val buffersUi: String = "0 B"
)

data class CpuStatus(
    val usagePercent: Float = 0f,
    val currentFreq: String = "0 MHz",
    val maxFreq: String = "0 MHz",
    val governor: String = "unknown"
)

data class GpuStatus(
    val loadPercent: Float = 0f,
    val currentFreq: String = "0 MHz",
    val maxFreq: String = "0 MHz"
)

data class SwapStatus(
    val isActive: Boolean = false,
    val totalUi: String = "0 B",
    val usedUi: String = "0 B",
    val usagePercent: Float = 0f,
    val path: String = "None"
)

data class SystemOverview(
    val deviceName: String = "",
    val kernelVersion: String = "",
    val isShizukuActive: Boolean = false
)
