package com.ivarna.mkm.data.provider

import android.content.Context
import android.content.SharedPreferences

class PowerCalibrationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("power_calibration_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MULTIPLIER = "power_multiplier"
        private const val KEY_UPDATE_INTERVAL = "update_interval_ms"
        private const val DEFAULT_UPDATE_INTERVAL = 1000L // 1 second
    }

    fun getMultiplier(): Float {
        return prefs.getFloat(KEY_MULTIPLIER, 1.0f)
    }

    fun saveMultiplier(multiplier: Float) {
        prefs.edit().putFloat(KEY_MULTIPLIER, multiplier).apply()
    }
    
    fun getUpdateInterval(): Long {
        return prefs.getLong(KEY_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL)
    }
    
    fun saveUpdateInterval(intervalMs: Long) {
        prefs.edit().putLong(KEY_UPDATE_INTERVAL, intervalMs).apply()
    }
}
