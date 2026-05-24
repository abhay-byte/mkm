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

    fun getSnapshot(context: Context): BatterySnapshot {
        val now = System.currentTimeMillis()
        cachedSnapshot?.let {
            if (now - cacheTimeMs < CACHE_TTL_MS) return it
        }
        val snap = computeSnapshot(context)
        cachedSnapshot = snap
        cacheTimeMs = now
        return snap
    }

    private fun computeSnapshot(context: Context): BatterySnapshot {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatterySnapshot(0, 0f, 0, 0, false)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = (level * 100) / scale.coerceAtLeast(1)

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureC = tempTenths / 10f

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0

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
        val wattageW = if (isCharging) powerW else -powerW
        val calibratedWattageW = wattageW * multiplier

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

        val script = """
            current=0; voltage=0; charge_full=0; charge_full_design=0; p_current=0; p_voltage=0
            for ps in /sys/class/power_supply/*; do
                if [ ${'$'}current -eq 0 ] && [ -e "${'$'}ps/current_now" ] && [ -e "${'$'}ps/voltage_now" ]; then
                    current=${'$'}(cat "${'$'}ps/current_now")
                    voltage=${'$'}(cat "${'$'}ps/voltage_now")
                fi
                if [ ${'$'}charge_full -eq 0 ] && [ -e "${'$'}ps/charge_full" ]; then
                    charge_full=${'$'}(cat "${'$'}ps/charge_full")
                fi
                if [ ${'$'}charge_full_design -eq 0 ] && [ -e "${'$'}ps/charge_full_design" ]; then
                    charge_full_design=${'$'}(cat "${'$'}ps/charge_full_design")
                fi
                if [ ${'$'}p_current -eq 0 ] && [ -e "${'$'}ps/current_now" ] && [ -e "${'$'}ps/voltage_now" ]; then
                    p_current=${'$'}(cat "${'$'}ps/current_now")
                    p_voltage=${'$'}(cat "${'$'}ps/voltage_now")
                fi
            done
            suspend_time=${'$'}(cat /sys/power/suspend_stats/time 2>/dev/null || echo 0)
            echo "${'$'}current ${'$'}voltage ${'$'}charge_full ${'$'}charge_full_design ${'$'}p_current ${'$'}p_voltage ${'$'}suspend_time"
        """.trimIndent()

        val result = ShellManager.exec(script)
        val output = result.stdout.trim()

        val readings = if (output.isNotBlank()) {
            val parts = output.split(" ")
            val currentUa = parts.getOrNull(0)?.toLongOrNull()?.takeIf { it != 0L }
            val voltageUv = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it != 0L }
            val chargeFullUa = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it != 0L }
            val chargeFullDesignUa = parts.getOrNull(3)?.toLongOrNull()?.takeIf { it != 0L }
            val pCurrentRaw = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            val pVoltageRaw = parts.getOrNull(5)?.toLongOrNull() ?: 0L

            var powerW = 0f
            if (pCurrentRaw != 0L && pVoltageRaw != 0L) {
                val currentUa = kotlin.math.abs(pCurrentRaw)
                val voltageUv = pVoltageRaw
                val powerUw = (currentUa * voltageUv) / 1_000_000L
                powerW = powerUw / 1_000_000f
            }

            val suspendTimeMs = parts.getOrNull(6)?.toLongOrNull() ?: 0L

            ShellBatteryReadings(
                sysfsCurrentUa = currentUa,
                sysfsVoltageUv = voltageUv,
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