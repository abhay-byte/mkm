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
    val screenOnDrainPercent: Float = 0f,
    val screenOffDrainPercent: Float = 0f,
    val deepSleepDrainPercent: Float = 0f,
    val awakeDrainPercent: Float = 0f,
    val isSessionActive: Boolean,
    val intervalCount: Int = 0,
    val wattageHistory: List<Float> = emptyList(),
    val drainHistory: List<Float> = emptyList(),
    val estimatedTimeRemainingMin: Long = 0L,
    // Charging-session specific fields
    val chargingSessionStartPercent: Int = 0,
    val chargingGainedPercent: Int = 0,
    val chargingAvgCurrentMa: Int = 0
)

/**
 * A persisted snapshot of a finished battery session.
 *
 * Captured by [com.ivarna.mkm.service.BatterySessionTracker] when a session
 * ends (e.g. charger plug/unplug) so the UI can render a history of past
 * charging and discharging sessions even after a reboot.
 */
data class BatterySessionRecord(
    val id: Long,
    val sessionType: SessionType,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startPercent: Int,
    val endPercent: Int,
    val screenOnTimeMs: Long,
    val screenOffTimeMs: Long,
    val deepSleepTimeMs: Long,
    val awakeTimeMs: Long,
    val screenOnDrainPercent: Float,
    val screenOffDrainPercent: Float,
    val deepSleepDrainPercent: Float,
    val awakeDrainPercent: Float,
    val activeDrainPerHr: Float,
    val idleDrainPerHr: Float,
    val avgCurrentMa: Int,
    val avgWattageW: Float,
    val avgTemperatureC: Float
) {
    val totalDurationMs: Long get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)
    val percentChange: Int
        get() = when (sessionType) {
            SessionType.CHARGING -> endPercent - startPercent
            SessionType.DISCHARGING -> startPercent - endPercent
        }
}

enum class SessionType { CHARGING, DISCHARGING }
