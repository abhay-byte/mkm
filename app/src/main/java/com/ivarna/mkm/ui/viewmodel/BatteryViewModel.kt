package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.BatteryStats
import com.ivarna.mkm.service.BatteryMonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Battery screen.
 *
 * Binds to [BatteryMonitorService] to observe live battery stats.
 * The service outlives the UI, so the persistent notification keeps
 * updating even when the app is closed or in the background.
 */
class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = BatteryMonitorService.PREFS_NAME
        private const val PREF_NOTIFICATION_ENABLED = BatteryMonitorService.PREF_NOTIFICATION_ENABLED
    }

    private val _batteryStats = MutableStateFlow<BatteryStats?>(null)
    val batteryStats: StateFlow<BatteryStats?> = _batteryStats.asStateFlow()

    private val _showNotification = MutableStateFlow(false)
    val showNotification: StateFlow<Boolean> = _showNotification.asStateFlow()

    private var serviceBound = false
    private var statsCollectionJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BatteryMonitorService.LocalBinder
            statsCollectionJob?.cancel()
            statsCollectionJob = viewModelScope.launch {
                binder.stats.collect { stats ->
                    _batteryStats.value = stats
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            statsCollectionJob?.cancel()
        }
    }

    init {
        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _showNotification.value = prefs.getBoolean(PREF_NOTIFICATION_ENABLED, false)

        if (_showNotification.value) {
            val intent = Intent(application, BatteryMonitorService::class.java).apply {
                action = BatteryMonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
        }

        bindToService()
    }

    fun setNotificationEnabled(enabled: Boolean) {
        _showNotification.value = enabled
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_NOTIFICATION_ENABLED, enabled)
            .apply()

        val intent = Intent(getApplication(), BatteryMonitorService::class.java).apply {
            action = if (enabled) BatteryMonitorService.ACTION_START else BatteryMonitorService.ACTION_STOP
        }
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun bindToService() {
        val intent = Intent(getApplication(), BatteryMonitorService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        serviceBound = true
    }
}
