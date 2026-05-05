package com.ivarna.mkm.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ivarna.mkm.R
import com.ivarna.mkm.data.model.BatteryStats

/**
 * Manages a persistent notification card showing battery statistics.
 *
 * Decoupled from UI and tracker — only knows how to build & post notifications.
 */
class BatteryNotificationManager(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "mkm_battery_channel"
        const val NOTIFICATION_ID = 2001
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live battery statistics while a session is active"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds a [Notification] for the given stats without posting it.
     * Useful for [android.app.Service.startForeground].
     */
    fun buildNotification(stats: BatteryStats): Notification {
        val style = NotificationCompat.BigTextStyle()
            .bigText(buildContentText(stats))
            .setSummaryText(buildSubText(stats))

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(buildTitle(stats))
            .setContentText(buildOneLine(stats))
            .setSubText(buildSubText(stats))
            .setNumber(stats.percent)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Builds a placeholder notification for use before the first stats are ready.
     */
    fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("Battery Monitor")
            .setContentText("Initializing...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Posts or updates the battery notification.
     */
    fun show(stats: BatteryStats) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(stats))
    }

    /**
     * Cancels the battery notification.
     */
    fun hide() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildTitle(stats: BatteryStats): String {
        // Title must be short — it's the only text visible in the collapsed notification
        val wattage = if (stats.calibratedWattageW != 0f) " · ${String.format("%+.2f", stats.calibratedWattageW)} W" else ""
        return "${stats.percent}%$wattage"
    }

    private fun buildOneLine(stats: BatteryStats): String {
        val wattage = if (stats.calibratedWattageW != 0f) "${String.format("%+.2f", stats.calibratedWattageW)} W · " else ""
        val status = when {
            stats.isCharging -> "Charging ${kotlin.math.abs(stats.currentMa)} mA"
            stats.isSessionActive -> "Discharging ${kotlin.math.abs(stats.currentMa)} mA"
            else -> "On AC"
        }
        return "${String.format("%.1f", stats.temperatureC)}°C · $wattage$status"
    }

    private fun buildSubText(stats: BatteryStats): String {
        return if (stats.isSessionActive) {
            val drain = String.format("%.2f", stats.activeDrainPerHr)
            val time = if (stats.estimatedTimeRemainingMin > 0) {
                val h = stats.estimatedTimeRemainingMin / 60
                val m = stats.estimatedTimeRemainingMin % 60
                " · ${h}h ${m}m left"
            } else ""
            "Drain ${drain}%/hr$time"
        } else {
            "On AC"
        }
    }

    private fun buildContentText(stats: BatteryStats): String {
        return buildString {
            if (stats.isSessionActive) {
                appendLine("Power: ${String.format("%+.2f", stats.calibratedWattageW)} W")
                appendLine("Active drain: ${String.format("%.2f", stats.activeDrainPerHr)}%/hr · Idle drain: ${String.format("%.2f", stats.idleDrainPerHr)}%/hr")
                if (stats.estimatedTimeRemainingMin > 0) {
                    val h = stats.estimatedTimeRemainingMin / 60
                    val m = stats.estimatedTimeRemainingMin % 60
                    appendLine("Est. time left: ${h}h ${m}m")
                }
                appendLine("Screen on: ${formatDuration(stats.screenOnTimeMs)} (${String.format("%.0f", stats.screenOnPercent)}%)")
                appendLine("Screen off: ${formatDuration(stats.screenOffTimeMs)} (${String.format("%.0f", stats.screenOffPercent)}%)")
                appendLine("Deep sleep: ${formatDuration(stats.deepSleepTimeMs)} (${String.format("%.2f", stats.deepSleepPercent)}%)")
                append("Awake: ${formatDuration(stats.awakeTimeMs)} (${String.format("%.2f", stats.awakePercent)}%)")
            } else {
                append("Plugged in. Monitoring will resume on disconnect.")
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m ${seconds}s"
        } else {
            "${minutes}m ${seconds}s"
        }
    }
}
