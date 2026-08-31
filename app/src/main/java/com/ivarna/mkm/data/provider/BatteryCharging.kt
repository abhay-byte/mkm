package com.ivarna.mkm.data.provider

import android.content.Intent
import android.os.BatteryManager

/**
 * Shared helper for evaluating Android battery charging / plugged state.
 *
 * Polarity contract: Evaluates true if status is CHARGING or plugged != 0.
 */
object BatteryCharging {
    fun isCharging(status: Int, plugged: Int): Boolean {
        return status == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0
    }

    fun readAndroidCharging(sticky: Intent?): Boolean {
        if (sticky == null) return false
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return isCharging(status, plugged)
    }
}
