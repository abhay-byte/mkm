package com.ivarna.mkm.data.model

data class DevfreqStatus(
    val isSupported: Boolean = false,
    val controllerPath: String = "",
    val currentGovernor: String = "unknown",
    val availableGovernors: List<String> = emptyList(),
    val currentFreq: String = "0",
    val availableFrequencies: List<String> = emptyList(),
    val debugInfo: String = ""
)
