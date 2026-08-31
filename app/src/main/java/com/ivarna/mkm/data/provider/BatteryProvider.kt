package com.ivarna.mkm.data.provider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ivarna.mkm.data.model.BatterySnapshot
import com.ivarna.mkm.shell.ShellManager

class BatteryProvider(context: Context) {

    private val appContext = context.applicationContext
    private val calibrationManager = PowerCalibrationManager(appContext)

    private var lastCurrentMa = 0
    private var lastVoltageMv = 0
    private var lastZeroReadTime = 0L
    private val ZERO_STALENESS_MS = 5_000L

    private var cachedSnapshot: BatterySnapshot? = null
    private var cacheTimeMs = 0L
    private val CACHE_TTL_MS = 5_000L

    private var lastShellSnapshot: ShellBatteryReadings? = null
    private var shellCacheTimeMs = 0L
    private val SHELL_CACHE_TTL_MS = 2_000L

    fun invalidate() {
        cachedSnapshot = null
        cacheTimeMs = 0L
        lastShellSnapshot = null
        shellCacheTimeMs = 0L
        lastCurrentMa = 0
        lastVoltageMv = 0
        lastZeroReadTime = 0L
    }

    fun getSnapshot(context: Context): BatterySnapshot {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val currentIsCharging = BatteryCharging.readAndroidCharging(sticky)
        val cached = cachedSnapshot
        if (cached != null && cached.isCharging != currentIsCharging) {
            invalidate()
        }

        val now = System.currentTimeMillis()
        cachedSnapshot?.let {
            if (now - cacheTimeMs < CACHE_TTL_MS) return it
        }
        val snap = computeSnapshot(context, sticky)
        cachedSnapshot = snap
        cacheTimeMs = now
        return snap
    }

    private fun computeSnapshot(context: Context, stickyIntent: Intent? = null): BatterySnapshot {
        val intent = stickyIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatterySnapshot(0, 0f, 0, 0, false)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = (level * 100) / scale.coerceAtLeast(1)

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureC = tempTenths / 10f

        val isCharging = BatteryCharging.readAndroidCharging(intent)

        val shellReadings = readShellBatteryData()

        var rawCurrentMa = shellReadings.sysfsCurrentUa?.let { (it / 1000).toInt() }
            ?: readCurrentFromBatteryManager(context)

        var rawVoltageMv = shellReadings.sysfsVoltageUv?.let { (it / 1000).toInt() }
            ?: intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        if (rawCurrentMa != 0) {
            val signMatchesState = (isCharging && rawCurrentMa > 0) || (!isCharging && rawCurrentMa < 0)
            if (!signMatchesState) {
                rawCurrentMa = -rawCurrentMa
            }
        }

        if ((isCharging && lastCurrentMa < 0) || (!isCharging && lastCurrentMa > 0)) {
            lastCurrentMa = 0
            lastVoltageMv = 0
            lastZeroReadTime = 0L
        }

        val now = System.currentTimeMillis()
        if (rawCurrentMa == 0 && lastCurrentMa != 0 && (now - lastZeroReadTime) < ZERO_STALENESS_MS) {
            rawCurrentMa = lastCurrentMa
        }
        if (rawVoltageMv == 0 && lastVoltageMv != 0 && (now - lastZeroReadTime) < ZERO_STALENESS_MS) {
            rawVoltageMv = lastVoltageMv
        }

        if (rawCurrentMa != 0) lastCurrentMa = rawCurrentMa
        if (rawVoltageMv != 0) lastVoltageMv = rawVoltageMv
        if (rawCurrentMa == 0 || rawVoltageMv == 0) {
            if (lastZeroReadTime == 0L) lastZeroReadTime = now
        } else {
            lastZeroReadTime = 0L
        }

        val currentMa = rawCurrentMa
        val voltageMv = rawVoltageMv

        var powerW = shellReadings.powerW ?: 0f

        if (powerW == 0f && currentMa != 0 && voltageMv != 0) {
            powerW = kotlin.math.abs(currentMa).toFloat() * voltageMv.toFloat() / 1_000_000f
        }

        val multiplier = calibrationManager.getMultiplier()
        // Apply multiplier to the unsigned magnitude first, then re-apply polarity.
        // This keeps calibratedWattageW signed (+ve = charging, -ve = discharging)
        // while ensuring the scale factor never interacts with the sign bit.
        val calibratedPowerW = powerW * multiplier
        val wattageW = if (isCharging) powerW else -powerW
        val calibratedWattageW = if (isCharging) calibratedPowerW else -calibratedPowerW

        var ratedCapacityMah = shellReadings.sysfsChargeFullDesignUa?.let { (it / 1000).toInt() } ?: 0
        var estimatedCapacityMah = shellReadings.sysfsChargeFullUa?.let { (it / 1000).toInt() } ?: 0

        if (estimatedCapacityMah == 0) {
            val fallback = readCapacityFromBatteryManager(context, percent)
            estimatedCapacityMah = fallback.estimatedMah
            if (ratedCapacityMah == 0) {
                ratedCapacityMah = fallback.ratedMah
            }
        }

        return BatterySnapshot(
            percent = percent,
            temperatureC = temperatureC,
            voltageMv = voltageMv,
            currentMa = currentMa,
            isCharging = isCharging,
            wattageW = wattageW,
            calibratedWattageW = calibratedWattageW,
            calibrationMultiplier = multiplier,
            ratedCapacityMah = ratedCapacityMah,
            estimatedCapacityMah = estimatedCapacityMah
        )
    }

    private fun readShellBatteryData(): ShellBatteryReadings {
        val now = System.currentTimeMillis()
        lastShellSnapshot?.let {
            if (now - shellCacheTimeMs < SHELL_CACHE_TTL_MS) return it
        }

        // Battery-first only — never latch onto usb/dc/ac when battery current is 0 (full).
        // Fields: current voltage charge_full charge_full_design suspend_time
        val script = """
            current=0; voltage=0; charge_full=0; charge_full_design=0; found=0
            try_read() {
                ps=${'$'}1
                if [ -e "${'$'}ps/current_now" ] && [ -e "${'$'}ps/voltage_now" ]; then
                    c=${'$'}(cat "${'$'}ps/current_now" 2>/dev/null)
                    v=${'$'}(cat "${'$'}ps/voltage_now" 2>/dev/null)
                    if [ -n "${'$'}v" ] && [ "${'$'}v" != "0" ]; then
                        current=${'$'}c
                        voltage=${'$'}v
                        [ -e "${'$'}ps/charge_full" ] && charge_full=${'$'}(cat "${'$'}ps/charge_full" 2>/dev/null || echo 0)
                        [ -e "${'$'}ps/charge_full_design" ] && charge_full_design=${'$'}(cat "${'$'}ps/charge_full_design" 2>/dev/null || echo 0)
                        return 0
                    fi
                fi
                return 1
            }
            for name in battery bms BAT0 BAT1 BATTERY Battery; do
                ps="/sys/class/power_supply/${'$'}name"
                if [ -d "${'$'}ps" ] && try_read "${'$'}ps"; then found=1; break; fi
            done
            if [ "${'$'}found" -eq 0 ]; then
                for ps in /sys/class/power_supply/*; do
                    [ -d "${'$'}ps" ] || continue
                    [ -e "${'$'}ps/type" ] || continue
                    t=${'$'}(cat "${'$'}ps/type" 2>/dev/null)
                    if [ "${'$'}t" = "Battery" ] && try_read "${'$'}ps"; then found=1; break; fi
                done
            fi
            if [ "${'$'}charge_full" = "0" ] || [ "${'$'}charge_full_design" = "0" ]; then
                for ps in /sys/class/power_supply/*; do
                    if [ "${'$'}charge_full" = "0" ] && [ -e "${'$'}ps/charge_full" ]; then
                        charge_full=${'$'}(cat "${'$'}ps/charge_full" 2>/dev/null || echo 0)
                    fi
                    if [ "${'$'}charge_full_design" = "0" ] && [ -e "${'$'}ps/charge_full_design" ]; then
                        charge_full_design=${'$'}(cat "${'$'}ps/charge_full_design" 2>/dev/null || echo 0)
                    fi
                done
            fi
            suspend_time=${'$'}(cat /sys/power/suspend_stats/time 2>/dev/null || echo 0)
            echo "${'$'}current ${'$'}voltage ${'$'}charge_full ${'$'}charge_full_design ${'$'}suspend_time"
        """.trimIndent()

        val result = ShellManager.exec(script)
        val output = result.stdout.trim()

        val readings = if (output.isNotBlank()) {
            val parts = output.split(" ")
            val currentRaw = parts.getOrNull(0)?.toLongOrNull()
            val voltageRaw = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it != 0L }
            val chargeFullUa = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it != 0L }
            val chargeFullDesignUa = parts.getOrNull(3)?.toLongOrNull()?.takeIf { it != 0L }
            val suspendTimeMs = parts.getOrNull(4)?.toLongOrNull() ?: 0L

            // Keep signed current for polarity alignment; drop pure zeros for optional path.
            val currentUa = currentRaw?.takeIf { it != 0L }

            var powerW = 0f
            if (currentRaw != null && currentRaw != 0L && voltageRaw != null) {
                val absI = kotlin.math.abs(currentRaw)
                val absV = kotlin.math.abs(voltageRaw)
                val voltageUv = when {
                    absV in 1_000_000L..50_000_000L -> absV
                    absV in 1_000L..50_000L -> absV * 1_000L
                    else -> absV
                }
                val currentUaMag = when {
                    absV in 1_000L..50_000L && absI in 1L until 20_000L -> absI * 1_000L
                    else -> absI
                }
                val watts = (currentUaMag.toDouble() * voltageUv.toDouble()) / 1_000_000_000_000.0
                powerW = if (watts.isFinite() && watts in 0.0..100.0) watts.toFloat() else 0f
            }

            ShellBatteryReadings(
                sysfsCurrentUa = currentUa,
                sysfsVoltageUv = voltageRaw,
                sysfsChargeFullUa = chargeFullUa,
                sysfsChargeFullDesignUa = chargeFullDesignUa,
                powerW = powerW,
                suspendTimeMs = suspendTimeMs
            )
        } else {
            ShellBatteryReadings()
        }

        lastShellSnapshot = readings
        shellCacheTimeMs = now
        return readings
    }

    fun getLastSuspendTimeMs(): Long = lastShellSnapshot?.suspendTimeMs ?: 0L

    private fun readCurrentFromBatteryManager(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNow = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        return if (currentNow != 0) currentNow / 1000 else 0
    }

    private fun readCapacityFromBatteryManager(context: Context, percent: Int): CapacityFallback {
        if (percent <= 0) return CapacityFallback(0, 0)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return CapacityFallback(0, 0)

        val chargeCounterUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (chargeCounterUa == Int.MIN_VALUE || chargeCounterUa <= 0) {
            return CapacityFallback(0, 0)
        }

        val remainingMah = chargeCounterUa / 1000f
        val estimatedFullMah = (remainingMah / percent * 100).toInt()

        return CapacityFallback(
            estimatedMah = estimatedFullMah,
            ratedMah = estimatedFullMah
        )
    }

    private data class CapacityFallback(val estimatedMah: Int, val ratedMah: Int)

    private data class ShellBatteryReadings(
        val sysfsCurrentUa: Long? = null,
        val sysfsVoltageUv: Long? = null,
        val sysfsChargeFullUa: Long? = null,
        val sysfsChargeFullDesignUa: Long? = null,
        val powerW: Float = 0f,
        val suspendTimeMs: Long = 0L
    )
}