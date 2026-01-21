package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.RamData
import com.ivarna.mkm.data.SystemRepository
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.ShellScripts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RamViewModel(private val repository: SystemRepository = SystemRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow<RamData?>(null)
    val uiState: StateFlow<RamData?> = _uiState.asStateFlow()

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
                _uiState.value = repository.getRamData()
                delay(2000) // Refresh every 2 seconds
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = repository.getRamData()
        }
    }

    fun applySwap(path: String, sizeMb: Int) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            try {
                val script = ShellScripts.createSwap(path, sizeMb)
                val result = ShellManager.exec(script)
                if (!result.isSuccess) {
                    _errorMessage.value = result.stderr.ifEmpty { "Failed to apply swap" }
                }
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun disableSwap(path: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val script = ShellScripts.disableSwap(path)
                ShellManager.exec(script)
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun removeSwap(path: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val script = ShellScripts.removeSwap(path)
                ShellManager.exec(script)
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
