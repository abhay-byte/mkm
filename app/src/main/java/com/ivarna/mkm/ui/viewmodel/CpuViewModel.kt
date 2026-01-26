package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.data.provider.CpuProvider
import com.ivarna.mkm.data.provider.ThermalProvider
import com.ivarna.mkm.data.provider.ThermalStatus
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

    private val _thermalStatus = MutableStateFlow(ThermalStatus(emptyList(), 0f))
    val thermalStatus: StateFlow<ThermalStatus> = _thermalStatus.asStateFlow()

    private var cachedLimit = 0

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            // Fetch thermal limit ONCE initially (heavy operation)
            cachedLimit = withContext(Dispatchers.IO) {
                ThermalProvider.getThermalLimit()
            }

            while (true) {
                val status = withContext(Dispatchers.IO) {
                    CpuProvider.getCpuStatus()
                }
                _cpuStatus.value = status
                
                val tStatus = withContext(Dispatchers.IO) {
                    // Fast poll (no limit parsing)
                    ThermalProvider.getThermalStatus(fetchLimit = false)
                }
                
                _thermalStatus.value = tStatus.copy(currentLimit = cachedLimit)
                
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
            val type = if (isMax) "MAX" else "MIN"
            val cmd = "printf '%s' '$freqKhz' > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_${if (isMax) "max" else "min"}_freq"
            android.util.Log.d("CpuViewModel", "Setting $type freq for policy $policyId: $cmd")
            
            val result = withContext(Dispatchers.IO) {
                CpuProvider.setFrequency(policyId, freqKhz, isMax)
            }
            
            android.util.Log.d("CpuViewModel", "Result: $result")
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
            val type = if (isMax) "MAX" else "MIN"
            val policyPath = CpuProvider.findPolicyForCore(coreId) ?: "/sys/devices/system/cpu/cpu$coreId/cpufreq"
            val cmd = "printf '%s' '$freqKhz' > $policyPath/scaling_${if (isMax) "max" else "min"}_freq"
            android.util.Log.d("CpuViewModel", "Setting $type freq for core $coreId: $cmd")
            
            val result = withContext(Dispatchers.IO) {
                CpuProvider.setFrequencyForCore(coreId, freqKhz, isMax)
            }
            
            android.util.Log.d("CpuViewModel", "Result: $result")
            refresh()
        }
    }

    fun setThermalLimit(limit: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (ThermalProvider.setThermalLimit(limit)) {
                    cachedLimit = limit
                }
            }
            refresh()
        }
    }

    fun disableThrottling() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ThermalProvider.disableThrottling()
            }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            // Full refresh, including limit (heavy)
            cachedLimit = withContext(Dispatchers.IO) {
                ThermalProvider.getThermalLimit()
            }
            
            val status = withContext(Dispatchers.IO) {
                CpuProvider.getCpuStatus()
            }
            _cpuStatus.value = status
            
            val tStatus = withContext(Dispatchers.IO) {
                ThermalProvider.getThermalStatus(fetchLimit = false)
            }
            _thermalStatus.value = tStatus.copy(currentLimit = cachedLimit)

            delay(500)
            _isRefreshing.value = false
        }
    }
}
