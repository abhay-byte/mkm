package com.ivarna.mkm.data.model

/**
 * Raw battery snapshot at a point in time.
 * Decoupled from session logic — pure data.
 */
data class BatterySnapshot(
    val percent: Int,
    val temperatureC: Float,
    val voltageMv: Int,
    val currentMa: Int,
    val isCharging: Boolean,
    val wattageW: Float = 0f,
    val calibratedWattageW: Float = 0f,
    val calibrationMultiplier: Float = 1.0f,
    val ratedCapacityMah: Int = 0,
    val estimatedCapacityMah: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * A single screen-on or screen-off interval with battery readings.
 * Used to compute per-interval drain rates.
 */
data class BatteryInterval(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startPercent: Int,
    val endPercent: Int,
    val isScreenOn: Boolean
) {
    val durationMs: Long get() = endTimeMs - startTimeMs
    val durationHours: Float get() = durationMs / 3_600_000f
    val percentDrop: Int get() = startPercent - endPercent
    val drainPerHour: Float get() = if (durationHours > 0) percentDrop / durationHours else 0f
}

/**
 * Aggregated battery statistics exposed to the UI.
 * Immutable — produced by the session tracker.
 */
data class BatteryStats(
    val percent: Int,
    val temperatureC: Float,
    val currentMa: Int,
    val isCharging: Boolean,
    val voltageMv: Int,
    val wattageW: Float = 0f,
    val calibratedWattageW: Float = 0f,
    val ratedCapacityMah: Int = 0,
    val estimatedCapacityMah: Int = 0,
    val activeDrainPerHr: Float,
    val idleDrainPerHr: Float,
    val screenOnTimeMs: Long,
    val screenOffTimeMs: Long,
    val deepSleepTimeMs: Long,
    val awakeTimeMs: Long,
    val sessionStartTimeMs: Long,
    val totalSessionTimeMs: Long,
    val screenOnPercent: Float,
    val screenOffPercent: Float,
    val deepSleepPercent: Float,
    val awakePercent: Float,
    val isSessionActive: Boolean,
    val intervalCount: Int = 0,
    val wattageHistory: List<Float> = emptyList(),
    val drainHistory: List<Float> = emptyList()
)
