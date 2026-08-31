package com.ivarna.mkm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ivarna.mkm.R
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.provider.CpuProvider
import com.ivarna.mkm.data.provider.GpuProvider
import com.ivarna.mkm.shell.DevfreqScripts
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.UfsScripts
import kotlinx.coroutines.*

/**
 * Foreground service that shows a 10-second countdown notification after boot,
 * then applies all kernel settings that the user has marked for "apply on boot".
 *
 * Started by [BootReceiver] when ACTION_BOOT_COMPLETED is received and at least
 * one category is enabled.
 */
class BootApplyService : Service() {

    companion object {
        const val ACTION_START = "com.ivarna.mkm.action.START_BOOT_APPLY"
        const val CHANNEL_ID = "mkm_boot_channel"
        const val NOTIFICATION_ID = 3001
        const val COUNTDOWN_SECONDS = 10
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var applyJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startCountdownAndApply()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        applyJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Countdown + apply logic
    // ------------------------------------------------------------------

    private fun startCountdownAndApply() {
        val notification = buildCountdownNotification(COUNTDOWN_SECONDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        applyJob = serviceScope.launch {
            for (secondsRemaining in COUNTDOWN_SECONDS downTo 1) {
                updateNotification(secondsRemaining)
                delay(1000L)
            }

            val failures = applyAllSettings()
            updateNotificationDone(failures)
            delay(3000L)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun applyAllSettings(): List<String> {
        val failures = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            // CPU
            if (BootSettingsManager.isCpuEnabled(this@BootApplyService)) {
                val policies = BootSettingsManager.loadCpuPolicies(this@BootApplyService)
                policies.forEach { policy ->
                    if (policy.governor.isNotBlank() && CpuProvider.applyGovernor(policy.policyId, policy.governor) is ApplyResult.Failed) {
                        failures += "CPU policy${policy.policyId} governor"
                    }
                    val min = policy.minFreq.toLongOrNull()
                    val max = policy.maxFreq.toLongOrNull()
                    if (min != null || max != null) {
                        val result = CpuProvider.applyRange(policy.policyId, min, max)
                        if (result is ApplyResult.Failed) failures += "CPU policy${policy.policyId} frequency"
                    }
                }
            }

            // GPU
            if (BootSettingsManager.isGpuEnabled(this@BootApplyService)) {
                BootSettingsManager.loadGpuSettings(this@BootApplyService)?.let { gpu ->
                    if (gpu.governor.isNotBlank() && GpuProvider.applyGovernor(gpu.governor) is ApplyResult.Failed) {
                        failures += "GPU governor"
                    }
                    val min = gpu.minFreq.toLongOrNull()
                    val max = gpu.maxFreq.toLongOrNull()
                    if (min != null || max != null) {
                        val result = GpuProvider.applyRange(min, max)
                        if (result is ApplyResult.Failed) failures += "GPU frequency"
                    }
                    val target = gpu.targetFreq.toLongOrNull()
                    if (target != null && GpuProvider.applyTarget(target) is ApplyResult.Failed) failures += "GPU target frequency"
                }
            }

            // RAM (DDR devfreq)
            if (BootSettingsManager.isRamEnabled(this@BootApplyService)) {
                BootSettingsManager.loadRamSettings(this@BootApplyService)?.let { ram ->
                    if (ram.governor.isNotBlank()) {
                        if (!ShellManager.exec(DevfreqScripts.setGovernor(ram.controllerPath, ram.governor)).isSuccess) failures += "RAM governor"
                    }
                    if (ram.freq.isNotBlank()) {
                        if (!ShellManager.exec(DevfreqScripts.setFreq(ram.controllerPath, ram.freq)).isSuccess) failures += "RAM frequency"
                    }
                }
            }

            // Storage (UFS)
            if (BootSettingsManager.isStorageEnabled(this@BootApplyService)) {
                BootSettingsManager.loadStorageSettings(this@BootApplyService)?.let { storage ->
                    if (storage.governor.isNotBlank()) {
                        if (!ShellManager.exec(UfsScripts.setGovernor(storage.controllerPath, storage.governor)).isSuccess) failures += "Storage governor"
                    }
                    if (storage.minFreq.isNotBlank()) {
                        if (!ShellManager.exec(UfsScripts.setMinFreq(storage.controllerPath, storage.minFreq)).isSuccess) failures += "Storage minimum frequency"
                    }
                    if (storage.maxFreq.isNotBlank()) {
                        if (!ShellManager.exec(UfsScripts.setMaxFreq(storage.controllerPath, storage.maxFreq)).isSuccess) failures += "Storage maximum frequency"
                    }
                }
            }
        }
        return failures
    }

    // ------------------------------------------------------------------
    // Notification helpers
    // ------------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Boot Settings",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Countdown before applying kernel settings on boot"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildCountdownNotification(seconds: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("MKM Boot Settings")
            .setContentText("Applying settings in ${seconds}s...")
            .setProgress(COUNTDOWN_SECONDS, COUNTDOWN_SECONDS - seconds, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification(secondsRemaining: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val elapsed = COUNTDOWN_SECONDS - secondsRemaining
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("MKM Boot Settings")
            .setContentText("Applying settings in ${secondsRemaining}s...")
            .setProgress(COUNTDOWN_SECONDS, elapsed, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationDone(failures: List<String>) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("MKM Boot Settings")
            .setContentText(if (failures.isEmpty()) "Settings applied successfully." else "Some settings failed: ${failures.joinToString()}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}
