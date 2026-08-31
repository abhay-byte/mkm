package com.ivarna.mkm.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns the Android thermal listener and the low-frequency safety poll for Game Boost. */
class GameBoostThermalGuard(
    context: Context,
    private val scope: CoroutineScope,
    private val onStatus: suspend (Int) -> Unit
) {
    data class StartResult(val monitoringAvailable: Boolean, val listenerRegistered: Boolean)

    private val appContext = context.applicationContext
    private var powerManager: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    /** Listener registration is optional; polling is the mandatory safety path. */
    fun start(): StartResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return StartResult(true, false)
        val power = appContext.getSystemService(PowerManager::class.java)
            ?: return StartResult(false, false)
        powerManager = power

        var listenerRegistered = false
        runCatching {
            val callback = PowerManager.OnThermalStatusChangedListener { status ->
                scope.launch { onStatus(status) }
            }
            power.addThermalStatusListener(appContext.mainExecutor, callback)
            listener = callback
            listenerRegistered = true
        }

        val initialStatus = runCatching { power.currentThermalStatus }.getOrNull()
        val pollingAvailable = initialStatus != null
        if (pollingAvailable) {
            scope.launch {
                onStatus(initialStatus!!)
                while (isActive) {
                    val status = runCatching { power.currentThermalStatus }.getOrNull() ?: break
                    onStatus(status)
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        return StartResult(listenerRegistered || pollingAvailable, listenerRegistered)
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listener?.let { powerManager?.removeThermalStatusListener(it) }
        }
        listener = null
        powerManager = null
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
