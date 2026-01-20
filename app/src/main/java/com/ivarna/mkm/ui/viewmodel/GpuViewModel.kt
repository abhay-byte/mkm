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

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                _gpuStatus.value = GpuProvider.getGpuStatus()
                delay(1000)
            }
        }
    }

    fun setGovernor(governor: String) {
        viewModelScope.launch {
            if (GpuProvider.setGovernor(governor)) {
                _gpuStatus.value = GpuProvider.getGpuStatus()
            }
        }
    }

    fun setFrequency(freq: String, isMax: Boolean) {
        viewModelScope.launch {
            if (GpuProvider.setFrequency(freq, isMax)) {
                _gpuStatus.value = GpuProvider.getGpuStatus()
            }
        }
    }
}
