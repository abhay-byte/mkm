package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.RamData
import com.ivarna.mkm.data.SystemRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RamViewModel(private val repository: SystemRepository = SystemRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow<RamData?>(null)
    val uiState: StateFlow<RamData?> = _uiState.asStateFlow()

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
}
