package com.ivarna.mkm.utils

import android.content.Context
import android.content.pm.PackageManager
import com.ivarna.mkm.BuildConfig

/**
 * Single source of truth for app version info shown in Settings.
 *
 * All values come from build data (BuildConfig), with a PackageManager
 * fallback so the UI never shows a hardcoded stale version.
 */
object AppBuildInfo {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val versionCode: Int
        get() = BuildConfig.VERSION_CODE

    val buildDate: String
        get() = BuildConfig.BUILD_TIME

    fun versionLabel(): String = "v$versionName"

    fun versionCodeLabel(): String = versionCode.toString()

    /**
     * Resolve version triple for the Settings page.
     * Prefers BuildConfig; falls back to PackageManager if BuildConfig
     * is unavailable (e.g. stripped test builds).
     */
    fun resolve(context: Context): Triple<String, String, String> {
        return try {
            val pmVersionName = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val name = versionName.ifBlank { pmVersionName ?: "1.9" }
            Triple("v$name", versionCode.toString(), buildDate)
        } catch (_: Exception) {
            Triple("v1.9", "10", buildDate)
        }
    }
}
