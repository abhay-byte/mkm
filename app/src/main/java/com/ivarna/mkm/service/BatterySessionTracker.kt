package com.ivarna.mkm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.ivarna.mkm.data.model.BatteryInterval
import com.ivarna.mkm.data.model.BatterySnapshot
import com.ivarna.mkm.data.model.BatteryStats
import com.ivarna.mkm.data.provider.BatteryProvider
import com.ivarna.mkm.shell.ShellManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Tracks a single battery session (unplugged → plugged).
 *
 * Responsibilities:
 * - Listen to system broadcasts (screen on/off, power connect/disconnect, battery change).
 * - Accumulate time spent in screen-on, screen-off, and deep-sleep states.
 * - Record battery intervals at state transitions to compute active/idle drain rates.
 * - Reset the session when the charger is connected.
 * - Start a new session when the charger is disconnected.
 *
 * Cohesion: knows **only** about session lifecycle and metric aggregation.
 * Decoupling: emits immutable [BatteryStats] via Flow; has no Compose or View knowledge.
 */
class BatterySessionTracker(context: Context) {

    companion object {
        const val MAX_HISTORY = 60 // 60 seconds of history at 1 sample/sec
    }

    private val appContext = context.applicationContext
    private val provider = BatteryProvider(appContext)
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

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

    // Interval bookkeeping
    private var pendingIntervalStartMs = 0L
    private var pendingIntervalStartPercent = 0

    // Rolling history for sparklines (normalized 0..1)
    private val wattageHistory = mutableListOf<Float>()
    private val drainHistory = mutableListOf<Float>()
    private var lastWattageW = 0f
    private var lastDrain = 0f

    private val _stats = MutableStateFlow<BatteryStats?>(null)
    val stats: StateFlow<BatteryStats?> = _stats.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_POWER_CONNECTED -> onPowerConnected()
                Intent.ACTION_POWER_DISCONNECTED -> onPowerDisconnected()
                Intent.ACTION_BATTERY_CHANGED -> onBatteryChanged()
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
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        appContext.registerReceiver(receiver, filter)

        synchronized(lock) {
            isScreenOn = powerManager.isInteractive
            lastScreenChangeTimeMs = System.currentTimeMillis()

            val snap = provider.getSnapshot(appContext)
            lastSnapshot = snap
            isSessionActive = !snap.isCharging

            if (isSessionActive) {
                startNewSessionLocked(snap)
            } else {
                // Still emit a reading so the UI isn't blank
                emitFromSnapshotLocked(snap)
            }
        }

        pollJob = scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    fun stop() {
        runCatching { appContext.unregisterReceiver(receiver) }
        pollJob?.cancel()
        scope.cancel()
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
    }

    private fun onPowerConnected() {
        synchronized(lock) {
            if (!isSessionActive) return
            val now = System.currentTimeMillis()
            val snap = lastSnapshot
            if (isScreenOn) accumulateScreenOnLocked(now) else accumulateScreenOffLocked(now)
            endIntervalLocked(isScreenOn, now, snap?.percent ?: sessionStartPercent)
            lastScreenChangeTimeMs = now
            isSessionActive = false
            // Emit final stats for the closed session
            computeAndEmitLocked(now)
        }
    }

    private fun onPowerDisconnected() {
        synchronized(lock) {
            val snap = provider.getSnapshot(appContext)
            lastSnapshot = snap
            val now = System.currentTimeMillis()
            startNewSessionLocked(snap)
            isSessionActive = true
            isScreenOn = powerManager.isInteractive
            lastScreenChangeTimeMs = now
            beginIntervalLocked(snap.percent, now)
        }
    }

    private fun onBatteryChanged() {
        synchronized(lock) {
            val snap = provider.getSnapshot(appContext)
            lastSnapshot = snap
            if (!isSessionActive) {
                emitFromSnapshotLocked(snap)
            }
        }
    }

    // ------------------------------------------------------------------
    // Periodic tick
    // ------------------------------------------------------------------

    private suspend fun tick() {
        val snap = provider.getSnapshot(appContext)
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

        val (activeDrain, idleDrain) = computeDrainRatesLocked()

        // Update rolling history (use magnitude for sparkline)
        lastWattageW = kotlin.math.abs(snap.calibratedWattageW)
        lastDrain = activeDrain.coerceAtLeast(0f)
        wattageHistory.add((lastWattageW / 10f).coerceIn(0f, 1f))
        drainHistory.add((lastDrain / 20f).coerceIn(0f, 1f))
        if (wattageHistory.size > MAX_HISTORY) wattageHistory.removeAt(0)
        if (drainHistory.size > MAX_HISTORY) drainHistory.removeAt(0)

        val estimatedMinutes = estimateTimeRemainingLocked(
            snap = snap,
            activeDrain = activeDrain,
            idleDrain = idleDrain,
            screenOnRatio = percentOf(currentScreenOn, totalSession) / 100f
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
            isSessionActive = isSessionActive,
            intervalCount = intervals.size,
            wattageHistory = wattageHistory.toList(),
            drainHistory = drainHistory.toList(),
            estimatedTimeRemainingMin = estimatedMinutes
        )
    }

    private fun estimateTimeRemainingLocked(
        snap: BatterySnapshot,
        activeDrain: Float,
        idleDrain: Float,
        screenOnRatio: Float
    ): Long {
        return if (snap.isCharging) {
            // Charging: estimate time to full from current and capacity.
            if (snap.estimatedCapacityMah > 0 && snap.currentMa != 0) {
                val remainingMah = (100 - snap.percent) * snap.estimatedCapacityMah / 100f
                val chargeRateMa = kotlin.math.abs(snap.currentMa)
                val hours = remainingMah / chargeRateMa
                (hours * 60).toLong()
            } else 0L
        } else {
            // Discharging: blended drain based on screen-on ratio.
            val blended = if (activeDrain > 0f || idleDrain > 0f) {
                activeDrain * screenOnRatio + idleDrain * (1f - screenOnRatio)
            } else 0f
            val effectiveDrain = blended.coerceAtLeast(0.1f)
            ((snap.percent / effectiveDrain) * 60).toLong()
        }
    }

    private fun emitFromSnapshotLocked(snap: BatterySnapshot) {
        lastWattageW = kotlin.math.abs(snap.calibratedWattageW)
        wattageHistory.add((lastWattageW / 10f).coerceIn(0f, 1f))
        if (wattageHistory.size > MAX_HISTORY) wattageHistory.removeAt(0)

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
            activeDrainPerHr = 0f,
            idleDrainPerHr = 0f,
            screenOnTimeMs = 0L,
            screenOffTimeMs = 0L,
            deepSleepTimeMs = 0L,
            awakeTimeMs = 0L,
            sessionStartTimeMs = 0L,
            totalSessionTimeMs = 0L,
            screenOnPercent = 0f,
            screenOffPercent = 0f,
            deepSleepPercent = 0f,
            awakePercent = 0f,
            isSessionActive = false,
            intervalCount = 0,
            wattageHistory = wattageHistory.toList(),
            drainHistory = drainHistory.toList()
        )
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

    private fun computeDrainRatesLocked(): Pair<Float, Float> {
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

        return active.coerceAtLeast(0f) to idle.coerceAtLeast(0f)
    }

    private fun overallDrainLocked(): Float {
        val totalHours = (System.currentTimeMillis() - sessionStartTimeMs) / 3_600_000f
        val drop = sessionStartPercent - (lastSnapshot?.percent ?: sessionStartPercent)
        return if (totalHours > 0 && drop > 0) drop / totalHours else 0f
    }

    // ------------------------------------------------------------------
    // Deep sleep
    // ------------------------------------------------------------------

    private fun readDeepSleepLocked(now: Long, screenOffMs: Long): Long {
        // Try kernel suspend stats (unit: ms on most Android kernels)
        val result = ShellManager.exec(
            "cat /sys/power/suspend_stats/time 2>/dev/null || echo 0"
        )
        val kernelMs = result.stdout.trim().toLongOrNull() ?: 0L

        if (kernelMs > 0 && lastDeepSleepNs > 0) {
            val deltaMs = kernelMs - lastDeepSleepNs
            if (deltaMs > 0) {
                accumulatedDeepSleepMs += deltaMs
            }
            lastDeepSleepNs = kernelMs
            return accumulatedDeepSleepMs.coerceAtMost(screenOffMs)
        }

        if (kernelMs > 0) {
            lastDeepSleepNs = kernelMs
            accumulatedDeepSleepMs = kernelMs
            return accumulatedDeepSleepMs.coerceAtMost(screenOffMs)
        }

        // Fallback: estimate deep sleep as 85 % of screen-off time (typical).
        return (screenOffMs * 0.85).toLong()
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
        pendingIntervalStartMs = sessionStartTimeMs
        pendingIntervalStartPercent = snap.percent
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
}
