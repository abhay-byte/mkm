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
    private val appContext = context.applicationContext
    private var powerManager: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    /** Returns false when API 29+ thermal monitoring cannot be initialized. */
    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val power = appContext.getSystemService(PowerManager::class.java) ?: return false
        return runCatching {
            val callback = PowerManager.OnThermalStatusChangedListener { status ->
                scope.launch { onStatus(status) }
            }
            power.addThermalStatusListener(appContext.mainExecutor, callback)
            powerManager = power
            listener = callback
            scope.launch {
                while (isActive) {
                    onStatus(power.currentThermalStatus)
                    delay(POLL_INTERVAL_MS)
                }
            }
        }.isSuccess
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
