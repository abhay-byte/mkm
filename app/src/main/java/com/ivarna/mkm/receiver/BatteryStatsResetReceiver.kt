package com.ivarna.mkm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.utils.BatteryStatsResetPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Resets the system battery stats (`dumpsys batterystats --reset`) on three
 * user-enabled triggers, each gated by [BatteryStatsResetPrefs]:
 *   - [Intent.ACTION_POWER_DISCONNECTED] (charger unplugged)
 *   - [Intent.ACTION_BATTERY_CHANGED] with [BatteryManager.EXTRA_STATUS] == FULL (100% reached)
 *   - [Intent.ACTION_BOOT_COMPLETED] (device reboot)
 *
 * Execution uses [ShellManager.exec] which already does shizuku → root → local
 * fallback. If neither shizuku nor root is available, the reset silently fails
 * (better than spamming toasts from BOOT_COMPLETED).
 */
class BatteryStatsResetReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = BatteryStatsResetPrefs

        when (action) {
            Intent.ACTION_POWER_DISCONNECTED -> {
                if (!prefs.isOnUnplug(context)) return
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                if (!prefs.isOnFull(context)) return
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (status != BatteryManager.BATTERY_STATUS_FULL) return
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                if (!prefs.isOnBoot(context)) return
            }
            else -> return
        }

        val trigger = when (action) {
            Intent.ACTION_POWER_DISCONNECTED -> "unplug"
            Intent.ACTION_BATTERY_CHANGED -> "full"
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            else -> return
        }

        scope.launch {
            val result = ShellManager.exec("dumpsys batterystats --reset")
            if (result.isSuccess) {
                Log.i(TAG, "battery stats reset OK (trigger=$trigger)")
                BatteryStatsResetPrefs.recordReset(context, trigger)
            } else {
                Log.w(TAG, "battery stats reset failed (trigger=$trigger, exit=${result.exitCode}, err=${result.stderr})")
            }
        }
    }

    companion object {
        private const val TAG = "BatteryStatsReset"
    }
}
