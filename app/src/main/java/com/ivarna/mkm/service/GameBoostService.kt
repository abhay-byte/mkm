package com.ivarna.mkm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ivarna.mkm.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps Game Boost ownership and thermal release monitoring alive outside the UI. */
class GameBoostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("Game Boost")
            .setContentText("Global performance tuning is active")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)

        if (Build.VERSION.SDK_INT >= 29) {
            getSystemService(PowerManager::class.java)?.let { power ->
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    scope.launch { GameBoostRegistry.manager(this@GameBoostService).onThermalStatus(status) }
                }
                thermalListener = listener
                power.addThermalStatusListener(mainExecutor, listener)
                scope.launch {
                    while (true) {
                        GameBoostRegistry.manager(this@GameBoostService).onThermalStatus(power.currentThermalStatus)
                        delay(5_000L)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 29) {
            val power = getSystemService(PowerManager::class.java)
            thermalListener?.let { power?.removeThermalStatusListener(it) }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Game Boost", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_START = "com.ivarna.mkm.action.START_GAME_BOOST"
        const val ACTION_STOP = "com.ivarna.mkm.action.STOP_GAME_BOOST"
        const val CHANNEL_ID = "mkm_game_boost_channel"
        const val NOTIFICATION_ID = 3002

        fun start(context: Context) {
            val intent = Intent(context, GameBoostService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameBoostService::class.java).setAction(ACTION_STOP))
        }
    }
}
