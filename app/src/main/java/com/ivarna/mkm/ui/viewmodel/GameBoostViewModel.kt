package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostState
import com.ivarna.mkm.service.GameBoostManager
import com.ivarna.mkm.service.GameBoostRegistry
import com.ivarna.mkm.service.GameBoostService
import com.ivarna.mkm.service.GameBoostTransitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameBoostViewModel(application: Application) : AndroidViewModel(application) {
    private val manager: GameBoostManager = GameBoostRegistry.manager(application)
    val state: StateFlow<GameBoostState> = GameBoostRegistry.state
    private val _capabilities = MutableStateFlow<GameBoostCapabilities?>(null)
    val capabilities: StateFlow<GameBoostCapabilities?> = _capabilities.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val prefs = application.getSharedPreferences(PREFS, Application.MODE_PRIVATE)
    val disclosureAcknowledged: Boolean get() = prefs.getBoolean(KEY_DISCLOSURE, false)

    init { refreshCapabilities() }

    fun acknowledgeDisclosure() {
        prefs.edit().putBoolean(KEY_DISCLOSURE, true).apply()
    }

    fun refreshCapabilities() {
        viewModelScope.launch(Dispatchers.IO) {
            _capabilities.value = runCatching { manager.capabilities() }.getOrNull()
        }
    }

    fun toggle(enabled: Boolean) {
        viewModelScope.launch {
            _message.value = null
            val result = if (enabled) {
                // The service is promoted before the first manager mutation.
                GameBoostService.start(getApplication())
                if (!GameBoostService.awaitReady()) GameBoostTransitionResult.Failure("Game Boost foreground service could not start")
                else withContext(Dispatchers.IO) { manager.enable() }
            } else withContext(Dispatchers.IO) { manager.disable() }
            if (result is GameBoostTransitionResult.Failure) {
                _message.value = result.reason
                if (enabled) GameBoostService.stop(getApplication())
            }
            if (!enabled && result is GameBoostTransitionResult.Success) GameBoostService.stop(getApplication())
            refreshCapabilities()
        }
    }

    fun retryRecovery() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = manager.retryRecovery()
            if (result is GameBoostTransitionResult.Failure) _message.value = result.reason
            else GameBoostService.stop(getApplication())
        }
    }

    companion object {
        private const val PREFS = "game_boost_session"
        private const val KEY_DISCLOSURE = "heat_disclosure_acknowledged"
    }
}
