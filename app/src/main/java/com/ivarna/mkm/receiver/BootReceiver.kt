package com.ivarna.mkm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ivarna.mkm.service.BatteryMonitorService
import com.ivarna.mkm.service.BootApplyService
import com.ivarna.mkm.service.BootSettingsManager

/**
 * Restarts the battery monitor foreground service after device boot
 * if the user had previously enabled the persistent notification.
 * Also starts [BootApplyService] if any "apply on boot" settings are enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // --- Battery monitor (existing behaviour) ---
        val batteryPrefs = context.getSharedPreferences(
            BatteryMonitorService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val batteryEnabled = batteryPrefs.getBoolean(BatteryMonitorService.PREF_NOTIFICATION_ENABLED, false)
        if (batteryEnabled) {
            val batteryIntent = Intent(context, BatteryMonitorService::class.java).apply {
                action = BatteryMonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(batteryIntent)
            } else {
                context.startService(batteryIntent)
            }
        }

        // --- Boot settings (new) ---
        if (BootSettingsManager.hasAnyEnabled(context)) {
            val bootIntent = Intent(context, BootApplyService::class.java).apply {
                action = BootApplyService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(bootIntent)
            } else {
                context.startService(bootIntent)
            }
        }
    }
}
