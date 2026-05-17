package com.ivarna.mkm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.ivarna.mkm.data.model.BatteryStats
import com.ivarna.mkm.utils.BatteryNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps battery session tracking alive independent of UI.
 *
 * - Runs [BatterySessionTracker] continuously while the service exists.
 * - When notification is enabled, promotes itself to a foreground service and
 *   posts/updating the persistent notification every second.
 * - Exposes [BatteryStats] via a binder so [BatteryViewModel] can observe live
 *   data while the Battery screen is visible.
 * - Survives app swipe-away because it is a started (foreground) service.
 */
class BatteryMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.ivarna.mkm.action.START_BATTERY_MONITOR"
        const val ACTION_STOP = "com.ivarna.mkm.action.STOP_BATTERY_MONITOR"
        const val ACTION_OPEN_BATTERY = "com.ivarna.mkm.action.OPEN_BATTERY"
        const val PREFS_NAME = "battery_prefs"
        const val PREF_NOTIFICATION_ENABLED = "notification_enabled"
        // Notification content customization
        const val PREF_NOTIF_SHOW_WATTAGE = "notif_show_wattage"
        const val PREF_NOTIF_SHOW_TEMPERATURE = "notif_show_temperature"
        const val PREF_NOTIF_SHOW_DRAIN = "notif_show_drain"
        const val PREF_NOTIF_SHOW_TIME_LEFT = "notif_show_time_left"
        const val PREF_NOTIF_SHOW_CURRENT = "notif_show_current"
        const val PREF_NOTIF_SHOW_VOLTAGE = "notif_show_voltage"
        // Expanded notification content customization
        const val PREF_NOTIF_EXP_TEMP_VOLTAGE = "notif_exp_temp_voltage"
        const val PREF_NOTIF_EXP_POWER = "notif_exp_power"
        const val PREF_NOTIF_EXP_DRAIN = "notif_exp_drain"
        const val PREF_NOTIF_EXP_TIME_LEFT = "notif_exp_time_left"
        const val PREF_NOTIF_EXP_SCREEN_ON = "notif_exp_screen_on"
        const val PREF_NOTIF_EXP_SCREEN_OFF = "notif_exp_screen_off"
        const val PREF_NOTIF_EXP_DEEP_SLEEP = "notif_exp_deep_sleep"
        const val PREF_NOTIF_EXP_AWAKE = "notif_exp_awake"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var tracker: BatterySessionTracker
    private var notificationManager: BatteryNotificationManager? = null
    private var notificationJob: kotlinx.coroutines.Job? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val stats: StateFlow<BatteryStats?> get() = tracker.stats
    }

    override fun onCreate() {
        super.onCreate()
        tracker = BatterySessionTracker(this)
        tracker.start()

        if (isNotificationEnabled()) {
            startMonitoring()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        if (!isNotificationEnabled()) {
            stopSelf()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        tracker.stop()
        notificationManager?.hide()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Notification management
    // ------------------------------------------------------------------

    private fun startMonitoring() {
        setNotificationEnabled(true)
        if (notificationManager == null) {
            notificationManager = BatteryNotificationManager(this)
        }

        val nm = notificationManager!!
        val currentStats = tracker.stats.value
        val notification = if (currentStats != null) {
            nm.buildNotification(currentStats)
        } else {
            nm.buildPlaceholderNotification()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                BatteryNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(BatteryNotificationManager.NOTIFICATION_ID, notification)
        }

        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            tracker.stats.collect { stats ->
                if (stats != null) {
                    nm.show(stats)
                }
            }
        }
    }

    private fun stopMonitoring() {
        setNotificationEnabled(false)
        notificationJob?.cancel()
        notificationManager?.hide()
        notificationManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isNotificationEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_NOTIFICATION_ENABLED, false)
    }

    private fun setNotificationEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_NOTIFICATION_ENABLED, enabled)
            .apply()
    }
}
