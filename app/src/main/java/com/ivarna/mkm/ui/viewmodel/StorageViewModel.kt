package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.SystemRepository
import com.ivarna.mkm.data.model.StorageStatus
import com.ivarna.mkm.service.BootSettingsManager
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.UfsScripts
import com.ivarna.mkm.util.AppVisibilityMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SystemRepository()
    private val _uiState = MutableStateFlow<StorageStatus?>(null)
    val uiState: StateFlow<StorageStatus?> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _bootEnabled = MutableStateFlow(BootSettingsManager.isStorageEnabled(application))
    val bootEnabled: StateFlow<Boolean> = _bootEnabled.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                if (AppVisibilityMonitor.isForeground.value) {
                    _uiState.value = repository.getStorageStatus()
                }
                delay(5000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            com.ivarna.mkm.data.provider.UfsProvider.clearCache()
            _uiState.value = repository.getStorageStatus()
            delay(500)
            _isRefreshing.value = false
        }
    }

    fun toggleBootEnabled(enabled: Boolean) {
        BootSettingsManager.setStorageEnabled(getApplication(), enabled)
        _bootEnabled.value = enabled
        if (enabled) {
            saveCurrentStorageSettings()
        }
    }

    private fun saveCurrentStorageSettings() {
        val ufs = _uiState.value?.ufsStatus ?: return
        if (!ufs.isSupported) return
        BootSettingsManager.saveStorageSettings(
            getApplication(),
            ufs.controllerPath,
            ufs.currentGovernor,
            ufs.minFreq,
            ufs.maxFreq
        )
    }

    fun setUfsGovernor(path: String, governor: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val script = UfsScripts.setGovernor(path, governor)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                    _errorMessage.value = "Failed to set governor: " + result.stderr.ifEmpty { result.stdout }
                }
                _uiState.value = repository.getStorageStatus()
                if (_bootEnabled.value) saveCurrentStorageSettings()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun setUfsMinFreq(path: String, freq: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val script = UfsScripts.setMinFreq(path, freq)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                    _errorMessage.value = "Failed to set min freq: " + result.stderr.ifEmpty { result.stdout }
                }
                _uiState.value = repository.getStorageStatus()
                if (_bootEnabled.value) saveCurrentStorageSettings()
            } catch (e: Exception) {
                _errorMessage.value = "Error setting min freq: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun setUfsMaxFreq(path: String, freq: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val script = UfsScripts.setMaxFreq(path, freq)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                    _errorMessage.value = "Failed to set max freq: " + result.stderr.ifEmpty { result.stdout }
                }
                _uiState.value = repository.getStorageStatus()
                if (_bootEnabled.value) saveCurrentStorageSettings()
            } catch (e: Exception) {
                _errorMessage.value = "Error setting max freq: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
