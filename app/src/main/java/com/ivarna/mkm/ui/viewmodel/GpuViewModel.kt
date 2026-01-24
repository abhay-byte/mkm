package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.provider.GpuProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GpuViewModel : ViewModel() {
    private val _gpuStatus = MutableStateFlow(GpuStatus())
    val gpuStatus = _gpuStatus.asStateFlow()

    private var setOnBoot = false
    private var freezeValues = false

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                val status = GpuProvider.getGpuStatus()
                _gpuStatus.value = status.copy(
                    setOnBoot = setOnBoot,
                    freezeValues = freezeValues
                )
                
                if (freezeValues) {
                    // Re-apply values if they changed
                    // This is a simple simulation of "Freezing"
                }
                
                delay(1000)
            }
        }
    }

    fun setGovernor(governor: String) {
        viewModelScope.launch {
            if (GpuProvider.setGovernor(governor)) {
                refresh()
            }
        }
    }

    fun setFrequency(freq: String, type: Int) {
        // type: 0=min, 1=max, 2=target
        viewModelScope.launch {
            if (GpuProvider.setFrequency(freq, type)) {
                refresh()
            }
        }
    }

    fun toggleSetOnBoot(enabled: Boolean) {
        setOnBoot = enabled
        _gpuStatus.value = _gpuStatus.value.copy(setOnBoot = enabled)
    }

    fun toggleFreezeValues(enabled: Boolean) {
        freezeValues = enabled
        _gpuStatus.value = _gpuStatus.value.copy(freezeValues = enabled)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _gpuStatus.value = GpuProvider.getGpuStatus().copy(
                setOnBoot = setOnBoot,
                freezeValues = freezeValues
            )
            delay(500)
            _isRefreshing.value = false
        }
    }
}
