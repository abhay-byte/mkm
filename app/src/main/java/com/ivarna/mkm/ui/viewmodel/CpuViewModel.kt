package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.data.provider.CpuProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CpuViewModel : ViewModel() {
    private val _cpuStatus = MutableStateFlow(CpuStatus())
    val cpuStatus: StateFlow<CpuStatus> = _cpuStatus.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                val status = withContext(Dispatchers.IO) {
                    CpuProvider.getCpuStatus()
                }
                _cpuStatus.value = status
                delay(2000)
            }
        }
    }

    fun setGovernor(policyId: Int, governor: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CpuProvider.setGovernor(policyId, governor)
            }
            refresh()
        }
    }

    fun setFrequency(policyId: Int, freqKhz: String, isMax: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CpuProvider.setFrequency(policyId, freqKhz, isMax)
            }
            refresh()
        }
    }

    fun setGovernorForCore(coreId: Int, governor: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CpuProvider.setGovernorForCore(coreId, governor)
            }
            refresh()
        }
    }

    fun setFrequencyForCore(coreId: Int, freqKhz: String, isMax: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CpuProvider.setFrequencyForCore(coreId, freqKhz, isMax)
            }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val status = withContext(Dispatchers.IO) {
                CpuProvider.getCpuStatus()
            }
            _cpuStatus.value = status
            delay(500)
            _isRefreshing.value = false
        }
    }
}
