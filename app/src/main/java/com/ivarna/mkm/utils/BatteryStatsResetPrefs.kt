package com.ivarna.mkm.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists per-trigger toggles for the auto-reset battery stats feature (T1).
 * Mirrors the pattern used by [com.ivarna.mkm.service.BootSettingsManager].
 */
object BatteryStatsResetPrefs {

    private const val PREFS_NAME = "mkm_battery_stats_reset"

    private const val PREF_ON_UNPLUG = "reset_on_unplug"
    private const val PREF_ON_FULL = "reset_on_full"
    private const val PREF_ON_BOOT = "reset_on_boot"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAnyEnabled(context: Context): Boolean =
        isOnUnplug(context) || isOnFull(context) || isOnBoot(context)

    // --- On charger unplug ---

    fun setOnUnplug(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(PREF_ON_UNPLUG, enabled) }
    }

    fun isOnUnplug(context: Context): Boolean =
        prefs(context).getBoolean(PREF_ON_UNPLUG, false)

    // --- On device reaches 100% ---

    fun setOnFull(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(PREF_ON_FULL, enabled) }
    }

    fun isOnFull(context: Context): Boolean =
        prefs(context).getBoolean(PREF_ON_FULL, false)

    // --- On device boot ---

    fun setOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(PREF_ON_BOOT, enabled) }
    }

    fun isOnBoot(context: Context): Boolean =
        prefs(context).getBoolean(PREF_ON_BOOT, false)
}
