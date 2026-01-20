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

data class CpuCore(
    val id: Int,
    val currentFreq: String = "0 MHz",
    val usagePercent: Float = 0f,
    val governor: String = "unknown",
    val minFreq: String = "0 MHz",
    val maxFreq: String = "0 MHz",
    val rawMinFreq: String = "",
    val rawMaxFreq: String = "",
    val availableGovernors: List<String> = emptyList(),
    val availableFrequencies: List<String> = emptyList()
)

data class CpuCluster(
    val id: Int,
    val coreRange: IntRange,
    val governor: String = "unknown",
    val currentFreq: String = "0 MHz",
    val minFreq: String = "0 MHz",
    val maxFreq: String = "0 MHz",
    val rawMinFreq: String = "",
    val rawMaxFreq: String = "",
    val availableGovernors: List<String> = emptyList(),
    val availableFrequencies: List<String> = emptyList(),
    val cores: List<CpuCore> = emptyList()
)

data class CpuStatus(
    val overallUsage: Float = 0f,
    val clusters: List<CpuCluster> = emptyList(),
    val totalCores: Int = 0
)

data class GpuStatus(
    val loadPercent: Float = 0f,
    val currentFreq: String = "0 MHz",
    val minFreq: String = "0 MHz",
    val maxFreq: String = "0 MHz",
    val rawMinFreq: String = "",
    val rawMaxFreq: String = "",
    val governor: String = "unknown",
    val availableGovernors: List<String> = emptyList(),
    val availableFrequencies: List<String> = emptyList(),
    val model: String = "Unknown"
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
