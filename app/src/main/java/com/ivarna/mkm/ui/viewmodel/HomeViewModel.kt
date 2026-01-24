package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.HomeData
import com.ivarna.mkm.data.SystemRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: SystemRepository = SystemRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeData?>(null)
    val uiState: StateFlow<HomeData?> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                _uiState.value = repository.getHomeData()
                delay(2000) // Refresh every 2 seconds
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.value = repository.getHomeData()
            delay(500)
            _isRefreshing.value = false
        }
    }
}
