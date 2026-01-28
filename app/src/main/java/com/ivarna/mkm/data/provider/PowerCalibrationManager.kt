package com.ivarna.mkm.data.provider

import android.content.Context
import android.content.SharedPreferences

class PowerCalibrationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("power_calibration_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MULTIPLIER = "power_multiplier"
    }

    fun getMultiplier(): Float {
        return prefs.getFloat(KEY_MULTIPLIER, 1.0f)
    }

    fun saveMultiplier(multiplier: Float) {
        prefs.edit().putFloat(KEY_MULTIPLIER, multiplier).apply()
    }
}
