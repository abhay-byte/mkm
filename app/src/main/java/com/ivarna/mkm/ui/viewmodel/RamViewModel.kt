package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.RamData
import com.ivarna.mkm.data.SystemRepository
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.ShellScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

class RamViewModel(private val repository: SystemRepository = SystemRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow<RamData?>(null)
    val uiState: StateFlow<RamData?> = _uiState.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            Log.d("RamViewModel", "Starting monitoring...")
            
            // Check elevated privileges on startup
            try {
                Log.d("RamViewModel", "Checking elevated access...")
                
                val hasElevatedAccess = withContext(Dispatchers.IO) {
                    ShellManager.hasElevatedAccess()
                }
                
                val accessMethod = withContext(Dispatchers.IO) {
                    ShellManager.getAvailableMethod()
                }
                
                Log.d("RamViewModel", "Elevated access: $hasElevatedAccess, Method: $accessMethod")
                
                if (!hasElevatedAccess) {
                    _errorMessage.value = "No elevated access. Please enable Shizuku or grant root access."
                } else {
                    val methodMsg = when (accessMethod) {
                        ShellManager.AccessMethod.ROOT -> "Using Root access"
                        ShellManager.AccessMethod.LOCAL -> "Local shell only (limited access)"
                    }
                    val shizukuStatus = if (ShellManager.hasShizuku()) " (Shizuku detected)" else ""
                    Log.d("RamViewModel", methodMsg + shizukuStatus)
                }
            } catch (e: Exception) {
                Log.e("RamViewModel", "Failed to check access", e)
                _errorMessage.value = "Access check failed: ${e.message}"
            }

            while (true) {
                _uiState.value = repository.getRamData()
                delay(2000) // Refresh every 2 seconds
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            com.ivarna.mkm.data.provider.DevfreqProvider.clearCache()
            _uiState.value = repository.getRamData()
            delay(500) // Artificial delay for visual feedback if operation is too fast
            _isRefreshing.value = false
        }
    }

    fun applySwap(path: String, sizeMb: Int) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            
            // Check if we have elevated access
            if (!ShellManager.hasElevatedAccess()) {
                _errorMessage.value = "Swap creation requires elevated access. Please enable Shizuku or grant root."
                _isProcessing.value = false
                return@launch
            }
            
            try {
                val script = ShellScripts.createSwap(path, sizeMb)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                    val errorMsg = result.stderr.ifEmpty { 
                        result.stdout.ifEmpty { 
                            "Failed to apply swap (exit ${result.exitCode})" 
                        } 
                    }
                    _errorMessage.value = errorMsg
                    Log.e("RamViewModel", "Swap creation failed: $errorMsg")
                } else {
                    Log.d("RamViewModel", "Swap created successfully")
                }
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("RamViewModel", "Swap creation exception", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun disableSwap(path: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            
            // Check if we have elevated access
            if (!ShellManager.hasElevatedAccess()) {
                _errorMessage.value = "Swap operations require elevated access. Please enable Shizuku or grant root."
                _isProcessing.value = false
                return@launch
            }
            
            try {
                val script = ShellScripts.disableSwap(path)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                    val errorMsg = result.stderr.ifEmpty { 
                        result.stdout.ifEmpty { 
                            "Failed to disable (exit ${result.exitCode})" 
                        } 
                    }
                    _errorMessage.value = errorMsg
                    Log.e("RamViewModel", "Swap disable failed: $errorMsg")
                }
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("RamViewModel", "Swap disable exception", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun removeSwap(path: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            
            // Check if we have elevated access
            if (!ShellManager.hasElevatedAccess()) {
                _errorMessage.value = "Swap removal requires elevated access. Please enable Shizuku or grant root."
                _isProcessing.value = false
                return@launch
            }
            
            try {
                val script = ShellScripts.removeSwap(path)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                    val errorMsg = result.stderr.ifEmpty { 
                        result.stdout.ifEmpty { 
                            "Failed to delete (exit ${result.exitCode})" 
                        } 
                    }
                    _errorMessage.value = errorMsg
                    Log.e("RamViewModel", "Swap removal failed: $errorMsg")
                }
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Delete failed: ${e.message}"
                Log.e("RamViewModel", "Swap removal exception", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }


    fun setDevfreqGovernor(path: String, governor: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val script = com.ivarna.mkm.shell.DevfreqScripts.setGovernor(path, governor)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                    _errorMessage.value = "Failed to set devfreq gov: " + result.stderr.ifEmpty { result.stdout }
                }
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                _errorMessage.value = "Error setting devfreq gov: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun setDevfreqFreq(path: String, freq: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                // To set a specific freq, we often need to be in userspace governor first.
                // However, we'll let the user manually switch to userspace if needed, 
                // OR we could force it. For now, we'll just try setting the freq.
                // If it fails, the user might need to switch governor.
                val script = com.ivarna.mkm.shell.DevfreqScripts.setFreq(path, freq)
                val result = withContext(Dispatchers.IO) {
                    ShellManager.exec(script)
                }
                if (!result.isSuccess) {
                     _errorMessage.value = "Failed to set devfreq freq: " + result.stderr.ifEmpty { result.stdout }
                }
                _uiState.value = repository.getRamData()
            } catch (e: Exception) {
                _errorMessage.value = "Error setting devfreq freq: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
