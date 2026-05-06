package com.ivarna.mkm.data.provider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ivarna.mkm.data.model.BatterySnapshot
import com.ivarna.mkm.shell.PowerScripts
import com.ivarna.mkm.shell.ShellManager

/**
 * Provides raw battery readings from the Android framework and kernel sysfs.
 * Single-responsibility: read current battery state, no session logic.
 *
 * Priority for current/voltage: Root/sysfs shell → BatteryManager framework.
 * This gives more accurate instantaneous readings when elevated access is available.
 *
 * Wattage preserves polarity (negative = discharging, positive = charging)
 * and is calibrated via [PowerCalibrationManager] so it matches the overlay / power page.
 */
class BatteryProvider(context: Context) {

    private val appContext = context.applicationContext
    private val calibrationManager = PowerCalibrationManager(appContext)

    // Cache last known good values to avoid flickering to 0 when a single
    // sysfs / BatteryManager read returns 0.
    private var lastCurrentMa = 0
    private var lastVoltageMv = 0
    private var lastZeroReadTime = 0L
    private val ZERO_STALENESS_MS = 5_000L

    /**
     * Reads the current battery state using the sticky [ACTION_BATTERY_CHANGED] broadcast
     * plus supplemental sysfs readings for current, voltage, and capacity.
     */
    fun getSnapshot(context: Context): BatterySnapshot {
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

        // --- Root-first readings from sysfs ---
        val sysfs = readSysfsPowerSupply()

        var rawCurrentMa = sysfs.currentUa?.let { (it / 1000).toInt() }
            ?: readCurrentFromBatteryManager(context)

        var rawVoltageMv = sysfs.voltageUv?.let { (it / 1000).toInt() }
            ?: intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        // --- Sign correction: some OEMs (Samsung, MediaTek) always report
        // positive current magnitude. Flip sign so it matches charging state.
        if (rawCurrentMa != 0) {
            val signMatchesState = (isCharging && rawCurrentMa > 0) || (!isCharging && rawCurrentMa < 0)
            if (!signMatchesState) {
                rawCurrentMa = -rawCurrentMa
            }
        }

        // --- Zero-read smoothing: if current or voltage is 0, reuse the last
        // known good value for up to 5 seconds to avoid UI flickering.
        val now = System.currentTimeMillis()
        if (rawCurrentMa == 0 && lastCurrentMa != 0 && (now - lastZeroReadTime) < ZERO_STALENESS_MS) {
            rawCurrentMa = lastCurrentMa
        }
        if (rawVoltageMv == 0 && lastVoltageMv != 0 && (now - lastZeroReadTime) < ZERO_STALENESS_MS) {
            rawVoltageMv = lastVoltageMv
        }

        // Update cache
        if (rawCurrentMa != 0) lastCurrentMa = rawCurrentMa
        if (rawVoltageMv != 0) lastVoltageMv = rawVoltageMv
        if (rawCurrentMa == 0 || rawVoltageMv == 0) {
            if (lastZeroReadTime == 0L) lastZeroReadTime = now
        } else {
            lastZeroReadTime = 0L
        }

        val currentMa = rawCurrentMa
        val voltageMv = rawVoltageMv

        // --- Wattage: use the exact same script + parsing as PowerProvider /
        // overlay so the value is stable and consistent across the app.
        // Note: do NOT gate on isSuccess — PowerProvider doesn't either.
        // The shell may exit non-zero for harmless reasons (stderr, glob)
        // while still printing valid values to stdout.
        val powerResult = ShellManager.exec(PowerScripts.getPowerAndVoltage())
        var powerW = 0f
        val output = powerResult.stdout.trim()
        if (output.isNotBlank()) {
            val powerParts = output.split(" ")
            if (powerParts.size >= 2) {
                val currentRaw = powerParts[0].toLongOrNull() ?: 0L
                val voltageRaw = powerParts[1].toLongOrNull() ?: 0L
                if (currentRaw != 0L && voltageRaw != 0L) {
                    val currentUa = kotlin.math.abs(currentRaw)
                    val voltageUv = voltageRaw
                    val powerUw = (currentUa * voltageUv) / 1_000_000L
                    powerW = powerUw / 1_000_000f
                }
            }
        }

        // Fallback: if the shell script returned 0, compute from the
        // currentMa / voltageMv we already have (includes BatteryManager
        // fallback and zero-read smoothing).
        if (powerW == 0f && currentMa != 0 && voltageMv != 0) {
            powerW = kotlin.math.abs(currentMa).toFloat() * voltageMv.toFloat() / 1_000_000f
        }

        val multiplier = calibrationManager.getMultiplier()
        // Apply polarity based on charging state from the battery intent.
        val wattageW = if (isCharging) powerW else -powerW
        val calibratedWattageW = wattageW * multiplier

        var ratedCapacityMah = sysfs.chargeFullDesignUa?.let { (it / 1000).toInt() } ?: 0
        var estimatedCapacityMah = sysfs.chargeFullUa?.let { (it / 1000).toInt() } ?: 0

        // Non-root fallback: estimate capacity from BatteryManager charge counter
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

    /**
     * Reads current, voltage, and capacity from kernel power-supply sysfs via shell.
     * Uses the same first-valid-power-supply logic as PowerProvider for consistency.
     */
    private fun readSysfsPowerSupply(): SysfsReadings {
        val script = """
            current=0; voltage=0; charge_full=0; charge_full_design=0
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
            done
            echo "${'$'}current ${'$'}voltage ${'$'}charge_full ${'$'}charge_full_design"
        """.trimIndent()

        val result = ShellManager.exec(script)
        if (!result.isSuccess) return SysfsReadings()

        val parts = result.stdout.trim().split(" ")
        if (parts.size < 4) return SysfsReadings()

        return SysfsReadings(
            currentUa = parts[0].toLongOrNull()?.takeIf { it != 0L },
            voltageUv = parts[1].toLongOrNull()?.takeIf { it != 0L },
            chargeFullUa = parts[2].toLongOrNull()?.takeIf { it != 0L },
            chargeFullDesignUa = parts[3].toLongOrNull()?.takeIf { it != 0L }
        )
    }

    /**
     * Non-root fallback for current via [BatteryManager].
     */
    private fun readCurrentFromBatteryManager(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNow = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        return if (currentNow != 0) currentNow / 1000 else 0
    }

    /**
     * Non-root fallback for battery capacity.
     *
     * Uses [BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER] (remaining charge in µAh)
     * combined with the current percentage to estimate full capacity.
     *
     * Formula: estimatedFullCapacityMah = (chargeCounter µAh) / (percent / 100) / 1000
     *
     * For rated/design capacity there is no standard non-root API;
     * we fall back to the estimated capacity.
     */
    private fun readCapacityFromBatteryManager(context: Context, percent: Int): CapacityFallback {
        if (percent <= 0) return CapacityFallback(0, 0)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return CapacityFallback(0, 0)

        val chargeCounterUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (chargeCounterUa == Int.MIN_VALUE || chargeCounterUa <= 0) {
            // Some OEMs (MediaTek, Samsung Exynos) don't expose this property.
            return CapacityFallback(0, 0)
        }

        // chargeCounter is in µAh. Convert to mAh and extrapolate to 100%.
        val remainingMah = chargeCounterUa / 1000f
        val estimatedFullMah = (remainingMah / percent * 100).toInt()

        return CapacityFallback(
            estimatedMah = estimatedFullMah,
            ratedMah = estimatedFullMah // best non-root approximation
        )
    }

    private data class CapacityFallback(val estimatedMah: Int, val ratedMah: Int)

    private data class SysfsReadings(
        val currentUa: Long? = null,
        val voltageUv: Long? = null,
        val chargeFullUa: Long? = null,
        val chargeFullDesignUa: Long? = null
    )
}
