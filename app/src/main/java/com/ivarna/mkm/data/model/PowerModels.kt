package com.ivarna.mkm.data.model

data class PowerStatus(
    val voltageUv: Long = 0, // Microvolts
    val currentUa: Long = 0, // Microamperes
    val powerUw: Long = 0,   // Microwatts
    val powerW: Float = 0f,  // Raw Watts
    val calibratedPowerW: Float = 0f, // Calibrated Watts
    val multiplier: Float = 1.0f      // Current multiplier
)

data class CpuEfficiencyResult(
    val policy: String,
    val frequencyKHz: Long,
    val durationSec: Float,
    val score: Float,
    val powerW: Float,
    val efficiency: Float, // Score per Watt
    val clusterFrequencies: Map<Int, Long> = emptyMap() // Policy ID -> Frequency KHz
)

data class GpuEfficiencyResult(
    val frequencyHz: Long,
    val utilization: Float,
    val powerW: Float,
    val efficiency: Float // Score per Watt
)

sealed class BenchmarkStatus {
    object Idle : BenchmarkStatus()
    object Running : BenchmarkStatus()
    data class Completed(val message: String, val logs: String = "") : BenchmarkStatus()
    data class Error(val error: String, val logs: String = "") : BenchmarkStatus()
}
