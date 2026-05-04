package com.ivarna.mkm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ivarna.mkm.service.BatteryMonitorService

/**
 * Restarts the battery monitor foreground service after device boot
 * if the user had previously enabled the persistent notification.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(
            BatteryMonitorService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val enabled = prefs.getBoolean(BatteryMonitorService.PREF_NOTIFICATION_ENABLED, false)
        if (!enabled) return

        val serviceIntent = Intent(context, BatteryMonitorService::class.java).apply {
            action = BatteryMonitorService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
