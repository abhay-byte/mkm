package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.SystemRepository
import com.ivarna.mkm.data.model.StorageStatus
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.UfsScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StorageViewModel(private val repository: SystemRepository = SystemRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow<StorageStatus?>(null)
    val uiState: StateFlow<StorageStatus?> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                _uiState.value = repository.getStorageStatus()
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
