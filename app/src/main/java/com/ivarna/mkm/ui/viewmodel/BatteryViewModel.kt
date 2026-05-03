package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.BatteryStats
import com.ivarna.mkm.service.BatterySessionTracker
import com.ivarna.mkm.utils.BatteryNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Battery screen.
 *
 * Cohesion: bridges [BatterySessionTracker] with Compose UI.
 * Decoupling: knows nothing about Composables; only exposes Flows.
 */
class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val tracker = BatterySessionTracker(application)
    private val notificationManager = BatteryNotificationManager(application)

    val batteryStats: StateFlow<BatteryStats?> = tracker.stats

    private val _showNotification = MutableStateFlow(false)
    val showNotification: StateFlow<Boolean> = _showNotification.asStateFlow()

    init {
        tracker.start()

        // Forward stats to notification when enabled
        viewModelScope.launch {
            tracker.stats.collect { stats ->
                if (_showNotification.value && stats != null) {
                    notificationManager.show(stats)
                }
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        _showNotification.value = enabled
        if (!enabled) {
            notificationManager.hide()
        } else {
            batteryStats.value?.let { notificationManager.show(it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tracker.stop()
        notificationManager.hide()
    }
}
