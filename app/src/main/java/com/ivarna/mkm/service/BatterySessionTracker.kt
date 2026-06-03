package com.ivarna.mkm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.ivarna.mkm.data.model.BatteryInterval
import com.ivarna.mkm.data.model.BatterySnapshot
import com.ivarna.mkm.data.model.BatteryStats
import com.ivarna.mkm.data.model.SessionType
import com.ivarna.mkm.data.provider.BatteryProvider
import com.ivarna.mkm.data.provider.PowerCalibrationManager
import com.ivarna.mkm.data.provider.PowerProvider
import com.ivarna.mkm.utils.BatteryHistoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Tracks battery sessions (both discharging and charging).
 *
 * Responsibilities:
 * - Listen to system broadcasts (screen on/off, power connect/disconnect, battery change).
 * - Accumulate time spent in screen-on, screen-off, and deep-sleep states.
 * - Record battery intervals at state transitions to compute active/idle drain rates.
 * - When charger connects: finalize discharging session, start a new CHARGING session.
 * - When charger disconnects: finalize charging session, start a new DISCHARGING session.
 *
 * Cohesion: knows **only** about session lifecycle and metric aggregation.
 * Decoupling: emits immutable [BatteryStats] via Flow; has no Compose or View knowledge.
 */
class BatterySessionTracker(context: Context) {

    companion object {
        const val MAX_HISTORY = 60
        private const val PREFS_NAME = BatteryMonitorService.PREFS_NAME
        private const val PREF_UPDATE_INTERVAL = "battery_update_interval_ms"
        const val DEFAULT_UPDATE_INTERVAL_MS = 30_000L
        const val SCREEN_OFF_INTERVAL_MS = 300_000L
        private const val DEEP_SLEEP_CACHE_TTL_MS = 60_000L
    }

    private val appContext = context.applicationContext
    private val provider = BatteryProvider(appContext)
    // Use the same PowerProvider as Settings/Overlay so wattage values are consistent
    private val powerProvider = PowerProvider(appContext)
    private val calibrationManager = PowerCalibrationManager(appContext)
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val historyManager = BatteryHistoryManager(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    // --- Mutable session state (guarded by synchronized) ---
    private val lock = Any()
    private var sessionStartTimeMs = 0L
    private var sessionStartPercent = 0
    private var isScreenOn = false
    private var lastScreenChangeTimeMs = 0L
    private var accumulatedScreenOnMs = 0L
    private var accumulatedScreenOffMs = 0L
    private var lastDeepSleepNs = 0L
    private var accumulatedDeepSleepMs = 0L
    private var lastSnapshot: BatterySnapshot? = null
    private val intervals = mutableListOf<BatteryInterval>()
    private var isSessionActive = false
    private var isChargingSession = false
    private var lastDeepSleepReadTimeMs = 0L
    private var cachedDeepSleepMs = 0L

    // Interval bookkeeping
    private var pendingIntervalStartMs = 0L
    private var pendingIntervalStartPercent = 0

    // Charging session tracking
    private var chargingSessionStartPercent = 0
    private val chargingCurrentSamples = mutableListOf<Int>()

    // Session running averages — collected per tick so a finished session
    // can be persisted as a single record with summary metrics.
    private val currentSamples = mutableListOf<Int>()
    private val wattageSamples = mutableListOf<Float>()
    private val temperatureSamples = mutableListOf<Float>()

    // Rolling history for sparklines (normalized 0..1)
    private val wattageHistory = mutableListOf<Float>()
    private val drainHistory = mutableListOf<Float>()
    private var lastWattageW = 0f
    private var lastDrain = 0f
    // Dynamic peak for sparkline normalisation — tracks the highest wattage magnitude
    // seen in the current session window so the graph never clips regardless of calibration.
    private var peakWattageW = 1f // floor of 1 W avoids division-by-zero on idle

    private val _stats = MutableStateFlow<BatteryStats?>(null)
    val stats: StateFlow<BatteryStats?> = _stats.asStateFlow()

    val history = historyManager.records
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun clearHistory() {
        historyScope.launch { historyManager.clear() }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_POWER_CONNECTED -> onPowerConnected()
                Intent.ACTION_POWER_DISCONNECTED -> onPowerDisconnected()
            }
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        appContext.registerReceiver(receiver, filter)

        synchronized(lock) {
            isScreenOn = powerManager.isInteractive
            lastScreenChangeTimeMs = System.currentTimeMillis()

            val snap = provider.getSnapshot(appContext)
            lastSnapshot = snap
            isSessionActive = true
            isChargingSession = snap.isCharging

            startNewSessionLocked(snap)
            beginIntervalLocked(snap.percent, System.currentTimeMillis())
        }

        pollJob = scope.launch {
            while (isActive) {
                tick()
                val interval = if (isScreenOn) {
                    getUpdateInterval()
                } else {
                    maxOf(getUpdateInterval(), SCREEN_OFF_INTERVAL_MS)
                }
                delay(interval)
            }
        }
    }

    fun getUpdateInterval(): Long {
        return prefs.getLong(PREF_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL_MS)
            .coerceIn(1_000L, 600_000L)
    }

    fun setUpdateInterval(intervalMs: Long) {
        prefs.edit().putLong(PREF_UPDATE_INTERVAL, intervalMs).apply()
    }

    fun stop() {
        runCatching { appContext.unregisterReceiver(receiver) }
        pollJob?.cancel()
        scope.cancel()
        historyScope.cancel()
    }

    // ------------------------------------------------------------------
    // Broadcast handlers
    // ------------------------------------------------------------------

    private fun onScreenOn() {
        synchronized(lock) {
            if (!isScreenOn) {
                val now = System.currentTimeMillis()
                val snap = lastSnapshot
                accumulateScreenOffLocked(now)
                endIntervalLocked(isScreenOnState = false, now, snap?.percent ?: sessionStartPercent)
                isScreenOn = true
                lastScreenChangeTimeMs = now
                beginIntervalLocked(snap?.percent ?: sessionStartPercent, now)
            }
        }
        scope.launch {
            tick()
        }
    }

    private fun onScreenOff() {
        synchronized(lock) {
            if (isScreenOn) {
                val now = System.currentTimeMillis()
                val snap = lastSnapshot
                accumulateScreenOnLocked(now)
                endIntervalLocked(isScreenOnState = true, now, snap?.percent ?: sessionStartPercent)
                isScreenOn = false
                lastScreenChangeTimeMs = now
                beginIntervalLocked(snap?.percent ?: sessionStartPercent, now)
            }
        }
        scope.launch {
            tick()
        }
    }

    private fun onPowerConnected() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val snap = provider.getSnapshot(appContext)
            lastSnapshot = snap
            // Finalize the discharging session with final stats
            if (isScreenOn) accumulateScreenOnLocked(now) else accumulateScreenOffLocked(now)
            endIntervalLocked(isScreenOn, now, snap?.percent ?: sessionStartPercent)
            computeAndEmitLocked(now)
            // Persist the discharging session that just ended
            persistSessionLocked(SessionType.DISCHARGING, now)
            // Start a new charging session immediately
            startNewSessionLocked(snap)
            isSessionActive = true
            isChargingSession = true
            isScreenOn = powerManager.isInteractive
            lastScreenChangeTimeMs = now
            beginIntervalLocked(snap.percent, now)
        }
        scope.launch { tick() }
    }

    private fun onPowerDisconnected() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val snap = provider.getSnapshot(appContext)
            lastSnapshot = snap
            // Finalize the charging session
            if (isScreenOn) accumulateScreenOnLocked(now) else accumulateScreenOffLocked(now)
            endIntervalLocked(isScreenOn, now, snap?.percent ?: sessionStartPercent)
            computeAndEmitLocked(now)
            // Persist the charging session that just ended
            persistSessionLocked(SessionType.CHARGING, now)
            // Start a new discharging session immediately
            startNewSessionLocked(snap)
            isSessionActive = true
            isChargingSession = false
            isScreenOn = powerManager.isInteractive
            lastScreenChangeTimeMs = now
            beginIntervalLocked(snap.percent, now)
        }
        scope.launch { tick() }
    }

    // ------------------------------------------------------------------
    // Periodic tick
    // ------------------------------------------------------------------

    private suspend fun tick() {
        val rawSnap = provider.getSnapshot(appContext)

        // Read power from the same PowerProvider used by Settings/Overlay.
        // This eliminates divergence between the two independent sysfs read paths.
        val multiplier = calibrationManager.getMultiplier()
        val powerStatus = powerProvider.getPowerStatus(multiplier)

        // Derive signed wattage from PowerProvider's unsigned calibratedPowerW
        val calibratedW = if (powerStatus.isCharging) powerStatus.calibratedPowerW
                          else -powerStatus.calibratedPowerW
        val rawW = if (powerStatus.isCharging) powerStatus.powerW
                   else -powerStatus.powerW

        // Overlay the authoritative power values onto the battery snapshot
        val snap = rawSnap.copy(
            wattageW = rawW,
            calibratedWattageW = calibratedW,
            calibrationMultiplier = multiplier
        )
        val now = System.currentTimeMillis()

        synchronized(lock) {
            lastSnapshot = snap
            if (isSessionActive) {
                computeAndEmitLocked(now)
            } else {
                emitFromSnapshotLocked(snap)
            }
        }
    }

    // ------------------------------------------------------------------
    // Computation
    // ------------------------------------------------------------------

    private fun computeAndEmitLocked(now: Long) {
        val snap = lastSnapshot ?: return
        val currentScreenOn = accumulatedScreenOnMs + if (isScreenOn) (now - lastScreenChangeTimeMs) else 0L
        val currentScreenOff = accumulatedScreenOffMs + if (!isScreenOn) (now - lastScreenChangeTimeMs) else 0L
        val totalSession = now - sessionStartTimeMs

        // Deep sleep: try kernel, else estimate from screen-off time
        val currentDeepSleepMs = readDeepSleepLocked(now, currentScreenOff)
        val awakeMs = max(0L, totalSession - currentDeepSleepMs)

        val (activeDrain, idleDrain, isDrainReliable) = computeDrainRatesLocked()

        // Compute absolute drain percentages: how many battery % points were drained during each state
        val (screenOnDrainPercent, screenOffDrainPercent) = computeDrainPercentagesLocked()

        // Deep sleep is a subset of screen-off. Estimate its absolute drain proportionally by time.
        val deepSleepDrainPercent = if (currentScreenOff > 0 && screenOffDrainPercent > 0f) {
            screenOffDrainPercent * (currentDeepSleepMs.toFloat() / currentScreenOff)
        } else 0f
        // Awake drain = all drain that isn't deep sleep (screenOn + screenOff - deepSleep)
        val awakeDrainPercent = (screenOnDrainPercent + screenOffDrainPercent - deepSleepDrainPercent).coerceAtLeast(0f)

        // Track charging current samples for average
        if (isChargingSession && snap.currentMa != 0) {
            chargingCurrentSamples.add(kotlin.math.abs(snap.currentMa))
            if (chargingCurrentSamples.size > MAX_HISTORY) chargingCurrentSamples.removeAt(0)
        }

        // Track running session averages for the history record
        currentSamples.add(kotlin.math.abs(snap.currentMa))
        wattageSamples.add(kotlin.math.abs(snap.calibratedWattageW))
        temperatureSamples.add(snap.temperatureC)
        if (currentSamples.size > MAX_HISTORY) currentSamples.removeAt(0)
        if (wattageSamples.size > MAX_HISTORY) wattageSamples.removeAt(0)
        if (temperatureSamples.size > MAX_HISTORY) temperatureSamples.removeAt(0)

        // Update rolling history — normalise by the dynamic session peak, not a hardcoded cap.
        lastWattageW = kotlin.math.abs(snap.calibratedWattageW)
        lastDrain = activeDrain.coerceAtLeast(0f)
        if (lastWattageW > peakWattageW) peakWattageW = lastWattageW
        wattageHistory.add((lastWattageW / peakWattageW).coerceIn(0f, 1f))
        drainHistory.add((lastDrain / 20f).coerceIn(0f, 1f))
        if (wattageHistory.size > MAX_HISTORY) wattageHistory.removeAt(0)
        if (drainHistory.size > MAX_HISTORY) drainHistory.removeAt(0)

        val estimatedMinutes = estimateTimeRemainingLocked(
            snap = snap,
            activeDrain = activeDrain,
            idleDrain = idleDrain,
            screenOnRatio = percentOf(currentScreenOn, totalSession) / 100f,
            totalSessionMs = totalSession,
            isDrainReliable = isDrainReliable
        )

        _stats.value = BatteryStats(
            percent = snap.percent,
            temperatureC = snap.temperatureC,
            currentMa = snap.currentMa,
            isCharging = snap.isCharging,
            voltageMv = snap.voltageMv,
            wattageW = snap.wattageW,
            calibratedWattageW = snap.calibratedWattageW,
            ratedCapacityMah = snap.ratedCapacityMah,
            estimatedCapacityMah = snap.estimatedCapacityMah,
            activeDrainPerHr = activeDrain,
            idleDrainPerHr = idleDrain,
            screenOnTimeMs = currentScreenOn,
            screenOffTimeMs = currentScreenOff,
            deepSleepTimeMs = currentDeepSleepMs,
            awakeTimeMs = awakeMs,
            sessionStartTimeMs = sessionStartTimeMs,
            totalSessionTimeMs = totalSession,
            screenOnPercent = percentOf(currentScreenOn, totalSession),
            screenOffPercent = percentOf(currentScreenOff, totalSession),
            deepSleepPercent = percentOf(currentDeepSleepMs, totalSession),
            awakePercent = percentOf(awakeMs, totalSession),
            screenOnDrainPercent = screenOnDrainPercent,
            screenOffDrainPercent = screenOffDrainPercent,
            deepSleepDrainPercent = deepSleepDrainPercent,
            awakeDrainPercent = awakeDrainPercent,
            isSessionActive = isSessionActive,
            intervalCount = intervals.size,
            wattageHistory = wattageHistory.toList(),
            drainHistory = drainHistory.toList(),
            estimatedTimeRemainingMin = estimatedMinutes,
            chargingSessionStartPercent = if (isChargingSession) chargingSessionStartPercent else 0,
            chargingGainedPercent = if (isChargingSession) (snap.percent - chargingSessionStartPercent).coerceAtLeast(0) else 0,
            chargingAvgCurrentMa = if (isChargingSession && chargingCurrentSamples.isNotEmpty())
                (chargingCurrentSamples.average().toInt()) else 0
        )
    }

    private fun estimateTimeRemainingLocked(
        snap: BatterySnapshot,
        activeDrain: Float,
        idleDrain: Float,
        screenOnRatio: Float,
        totalSessionMs: Long,
        isDrainReliable: Boolean
    ): Long {
        // Need at least 3 minutes of session data before trusting any estimate.
        if (totalSessionMs < 180_000L) return 0L

        return if (snap.isCharging) {
            // Charging: estimate time to full from current and capacity.
            if (snap.estimatedCapacityMah <= 0 || snap.percent >= 100) return 0L
            val chargeRateMa = kotlin.math.abs(snap.currentMa)
            // Reject if charge current is too low (trickle or no real data).
            if (chargeRateMa < 50) return 0L
            val remainingMah = (100 - snap.percent) * snap.estimatedCapacityMah / 100f
            val hours = remainingMah / chargeRateMa
            val minutes = (hours * 60).toLong()
            // Sanity cap: more than 24h is unrealistic.
            if (minutes in 1..1440) minutes else 0L
        } else {
            // Discharging: only trust estimate when active drain comes from
            // real screen-on intervals, not the overall-session fallback.
            if (!isDrainReliable || activeDrain <= 0f) return 0L

            val blended = activeDrain * screenOnRatio + idleDrain * (1f - screenOnRatio)
            // Reject if blended drain is unrealistically low.
            if (blended < 0.1f) return 0L

            val minutes = ((snap.percent / blended) * 60).toLong()
            // Sanity cap: more than 7 days is unrealistic.
            if (minutes in 1..10080) minutes else 0L
        }
    }

    // emitFromSnapshotLocked kept as a no-op safety valve; normal path always uses computeAndEmitLocked.
    @Suppress("unused")
    private fun emitFromSnapshotLocked(snap: BatterySnapshot) {
        lastWattageW = kotlin.math.abs(snap.calibratedWattageW)
        if (lastWattageW > peakWattageW) peakWattageW = lastWattageW
        wattageHistory.add((lastWattageW / peakWattageW).coerceIn(0f, 1f))
        if (wattageHistory.size > MAX_HISTORY) wattageHistory.removeAt(0)
        computeAndEmitLocked(System.currentTimeMillis())
    }

    // ------------------------------------------------------------------
    // Interval bookkeeping
    // ------------------------------------------------------------------

    private fun beginIntervalLocked(percent: Int, timeMs: Long) {
        pendingIntervalStartMs = timeMs
        pendingIntervalStartPercent = percent
    }

    private fun endIntervalLocked(isScreenOnState: Boolean, endMs: Long, endPercent: Int) {
        if (endMs > pendingIntervalStartMs) {
            intervals.add(
                BatteryInterval(
                    startTimeMs = pendingIntervalStartMs,
                    endTimeMs = endMs,
                    startPercent = pendingIntervalStartPercent,
                    endPercent = endPercent,
                    isScreenOn = isScreenOnState
                )
            )
        }
    }

    // ------------------------------------------------------------------
    // Drain-rate computation
    // ------------------------------------------------------------------

    private fun computeDrainRatesLocked(): Triple<Float, Float, Boolean> {
        val onSamples = intervals.filter { it.isScreenOn && it.durationHours > 0.05f }
        val offSamples = intervals.filter { !it.isScreenOn && it.durationHours > 0.05f }

        val active = if (onSamples.isNotEmpty()) {
            onSamples.map { it.drainPerHour }.average().toFloat()
        } else {
            // Fallback: derive from overall session if we have enough time
            overallDrainLocked()
        }

        val idle = if (offSamples.isNotEmpty()) {
            offSamples.map { it.drainPerHour }.average().toFloat()
        } else {
            0f
        }

        // Reliable only when we have actual interval samples for screen-on time.
        val reliable = onSamples.isNotEmpty()
        return Triple(active.coerceAtLeast(0f), idle.coerceAtLeast(0f), reliable)
    }

    private fun overallDrainLocked(): Float {
        val totalHours = (System.currentTimeMillis() - sessionStartTimeMs) / 3_600_000f
        val drop = sessionStartPercent - (lastSnapshot?.percent ?: sessionStartPercent)
        return if (totalHours > 0 && drop > 0) drop / totalHours else 0f
    }

    /**
     * Computes screen-on and screen-off **absolute** battery drain in percent points.
     * E.g., if screen-on periods consumed 8% of the battery, returns 8f.
     * These are raw % points drained, NOT a ratio of total drained battery.
     */
    private fun computeDrainPercentagesLocked(): Pair<Float, Float> {
        val onDrain = intervals.filter { it.isScreenOn }.sumOf { it.percentDrop.coerceAtLeast(0) }
        val offDrain = intervals.filter { !it.isScreenOn }.sumOf { it.percentDrop.coerceAtLeast(0) }
        return onDrain.toFloat() to offDrain.toFloat()
    }

    // ------------------------------------------------------------------
    // Deep sleep
    // ------------------------------------------------------------------

    private fun readDeepSleepLocked(now: Long, screenOffMs: Long): Long {
        if (now - lastDeepSleepReadTimeMs < DEEP_SLEEP_CACHE_TTL_MS && cachedDeepSleepMs > 0L) {
            return cachedDeepSleepMs.coerceAtMost(screenOffMs)
        }

        val kernelMs = provider.getLastSuspendTimeMs()

        if (kernelMs > 0 && lastDeepSleepNs > 0) {
            val deltaMs = kernelMs - lastDeepSleepNs
            if (deltaMs > 0) {
                accumulatedDeepSleepMs += deltaMs
            }
            lastDeepSleepNs = kernelMs
            cachedDeepSleepMs = accumulatedDeepSleepMs.coerceAtMost(screenOffMs)
            lastDeepSleepReadTimeMs = now
            return cachedDeepSleepMs
        }

        if (kernelMs > 0) {
            lastDeepSleepNs = kernelMs
            accumulatedDeepSleepMs = kernelMs
            cachedDeepSleepMs = accumulatedDeepSleepMs.coerceAtMost(screenOffMs)
            lastDeepSleepReadTimeMs = now
            return cachedDeepSleepMs
        }

        val estimated = (screenOffMs * 0.85).toLong()
        cachedDeepSleepMs = estimated
        lastDeepSleepReadTimeMs = now
        return estimated
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun startNewSessionLocked(snap: BatterySnapshot) {
        sessionStartTimeMs = System.currentTimeMillis()
        sessionStartPercent = snap.percent
        accumulatedScreenOnMs = 0L
        accumulatedScreenOffMs = 0L
        accumulatedDeepSleepMs = 0L
        intervals.clear()
        lastDeepSleepNs = 0L
        lastDeepSleepReadTimeMs = 0L
        cachedDeepSleepMs = 0L
        pendingIntervalStartMs = sessionStartTimeMs
        pendingIntervalStartPercent = snap.percent
        // Reset charging tracking
        chargingSessionStartPercent = snap.percent
        chargingCurrentSamples.clear()
        // Reset session averages for the new record
        currentSamples.clear()
        wattageSamples.clear()
        temperatureSamples.clear()
        // Reset sparkline peak so the new session scales independently
        peakWattageW = 1f
    }

    private fun accumulateScreenOnLocked(now: Long) {
        accumulatedScreenOnMs += (now - lastScreenChangeTimeMs).coerceAtLeast(0L)
    }

    private fun accumulateScreenOffLocked(now: Long) {
        accumulatedScreenOffMs += (now - lastScreenChangeTimeMs).coerceAtLeast(0L)
    }

    private fun percentOf(part: Long, total: Long): Float {
        return if (total > 0) (part * 100f / total) else 0f
    }

    // ------------------------------------------------------------------
    // History persistence
    // ------------------------------------------------------------------

    /**
     * Builds a [com.ivarna.mkm.data.model.BatterySessionRecord] from the
     * currently accumulated session data and writes it to disk on a
     * background coroutine.
     *
     * Skipped (no record written) when:
     * - the session is shorter than 10 seconds (noise: boot, transient blips), or
     * - the battery percentage did not actually change in the expected
     *   direction (e.g. a discharging session that didn't discharge, or a
     *   charging session that didn't charge). These zero-value records would
     *   only clutter the history list.
     */
    private fun persistSessionLocked(endedSessionType: SessionType, endTimeMs: Long) {
        val totalMs = endTimeMs - sessionStartTimeMs
        val endPercent = lastSnapshot?.percent ?: sessionStartPercent
        val percentDelta = when (endedSessionType) {
            SessionType.CHARGING -> endPercent - sessionStartPercent
            SessionType.DISCHARGING -> sessionStartPercent - endPercent
        }

        // Only persist sessions >= 30s where the battery % actually changed by at least 1 point.
        val tooShort = totalMs < 30_000L
        val noChange = percentDelta <= 0
        if (tooShort || noChange) {
            // Reset the buffers so the next session starts clean.
            currentSamples.clear()
            wattageSamples.clear()
            temperatureSamples.clear()
            chargingCurrentSamples.clear()
            return
        }

        val screenOn = accumulatedScreenOnMs
        val screenOff = accumulatedScreenOffMs
        val deepSleep = cachedDeepSleepMs.coerceAtMost(screenOff)
        val awake = max(0L, totalMs - deepSleep)
        val (onDrainPct, offDrainPct) = computeDrainPercentagesLocked()
        val deepSleepDrainPct = if (screenOff > 0 && offDrainPct > 0f) {
            offDrainPct * (deepSleep.toFloat() / screenOff)
        } else 0f
        val awakeDrainPct = (onDrainPct + offDrainPct - deepSleepDrainPct).coerceAtLeast(0f)
        val (activeDrain, idleDrain, _) = computeDrainRatesLocked()

        val avgCurrent = if (currentSamples.isNotEmpty()) currentSamples.average().toInt() else 0
        val avgWattage = if (wattageSamples.isNotEmpty()) wattageSamples.average().toFloat() else 0f
        val avgTemp = if (temperatureSamples.isNotEmpty()) temperatureSamples.average().toFloat() else 0f

        val record = historyManager.buildRecord(
            sessionType = endedSessionType,
            startTimeMs = sessionStartTimeMs,
            endTimeMs = endTimeMs,
            startPercent = sessionStartPercent,
            endPercent = endPercent,
            screenOnTimeMs = screenOn,
            screenOffTimeMs = screenOff,
            deepSleepTimeMs = deepSleep,
            awakeTimeMs = awake,
            screenOnDrainPercent = onDrainPct,
            screenOffDrainPercent = offDrainPct,
            deepSleepDrainPercent = deepSleepDrainPct,
            awakeDrainPercent = awakeDrainPct,
            activeDrainPerHr = activeDrain,
            idleDrainPerHr = idleDrain,
            avgCurrentMa = avgCurrent,
            avgWattageW = avgWattage,
            avgTemperatureC = avgTemp
        )
        historyScope.launch { historyManager.add(record) }
    }
}
