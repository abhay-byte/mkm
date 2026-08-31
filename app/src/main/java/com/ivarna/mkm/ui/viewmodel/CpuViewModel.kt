package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.data.model.TuningPersistencePolicy
import com.ivarna.mkm.data.provider.CpuProvider
import com.ivarna.mkm.data.provider.ThermalProvider
import com.ivarna.mkm.data.provider.ThermalStatus
import com.ivarna.mkm.service.BootSettingsManager
import com.ivarna.mkm.util.AppVisibilityMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CpuViewModel(application: Application) : AndroidViewModel(application) {
    private val _cpuStatus = MutableStateFlow(CpuStatus())
    val cpuStatus: StateFlow<CpuStatus> = _cpuStatus.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _thermalStatus = MutableStateFlow(ThermalStatus(emptyList(), 0f))
    val thermalStatus: StateFlow<ThermalStatus> = _thermalStatus.asStateFlow()
    private val _bootEnabled = MutableStateFlow(BootSettingsManager.isCpuEnabled(application))
    val bootEnabled: StateFlow<Boolean> = _bootEnabled.asStateFlow()
    private val _pendingControlId = MutableStateFlow<String?>(null)
    val pendingControlId: StateFlow<String?> = _pendingControlId.asStateFlow()
    private val _lastApplyResult = MutableStateFlow<ApplyResult?>(null)
    val lastApplyResult: StateFlow<ApplyResult?> = _lastApplyResult.asStateFlow()

    private val tuningMutex = Mutex()
    private var cachedLimit = 0
    private var monitorJob: Job? = null

    init { startMonitoring() }

    private fun startMonitoring() {
        monitorJob = viewModelScope.launch {
            cachedLimit = withContext(Dispatchers.IO) { ThermalProvider.getThermalLimit() }
            while (true) {
                if (AppVisibilityMonitor.isForeground.value) {
                    tuningMutex.withLock { publishState() }
                }
                delay(2000L)
            }
        }
    }

    fun setGovernor(policyId: Int, governor: String, onResult: (ApplyResult) -> Unit = {}) =
        mutate("cpu-policy-$policyId-governor", { CpuProvider.applyGovernor(policyId, governor) }, onResult)

    fun setFrequency(policyId: Int, freqKhz: String, isMax: Boolean, onResult: (ApplyResult) -> Unit = {}) {
        val value = freqKhz.toLongOrNull()
        mutate("cpu-policy-$policyId-${if (isMax) "max" else "min"}", {
            if (value == null) ApplyResult.Failed("Invalid CPU frequency")
            else if (isMax) CpuProvider.applyRange(policyId, desiredMax = value)
            else CpuProvider.applyRange(policyId, desiredMin = value)
        }, onResult)
    }

    // Kept for benchmark/backward-compatible callers; all writes resolve to the owning policy.
    fun setGovernorForCore(coreId: Int, governor: String, onResult: (ApplyResult) -> Unit = {}) {
        val policyId = _cpuStatus.value.clusters.firstOrNull { cluster ->
            cluster.affectedCpus.contains(coreId) || cluster.relatedCpus.contains(coreId) ||
                cluster.cores.any { it.id == coreId && it.policyId != null }
        }?.id
        if (policyId == null) onResult(ApplyResult.Failed("No cpufreq policy owns CPU $coreId"))
        else setGovernor(policyId, governor, onResult)
    }

    fun setFrequencyForCore(coreId: Int, freqKhz: String, isMax: Boolean, onResult: (ApplyResult) -> Unit = {}) {
        val policyId = _cpuStatus.value.clusters.firstOrNull { cluster ->
            cluster.affectedCpus.contains(coreId) || cluster.relatedCpus.contains(coreId) ||
                cluster.cores.any { it.id == coreId && it.policyId != null }
        }?.id
        if (policyId == null) onResult(ApplyResult.Failed("No cpufreq policy owns CPU $coreId"))
        else setFrequency(policyId, freqKhz, isMax, onResult)
    }

    private fun mutate(controlId: String, operation: () -> ApplyResult, onResult: (ApplyResult) -> Unit) {
        viewModelScope.launch {
            val result = tuningMutex.withLock {
                _pendingControlId.value = controlId
                try {
                    val applied = try {
                        withContext(Dispatchers.IO) { operation() }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ApplyResult.Failed(error.message ?: "CPU tuning operation failed")
                    }
                    var stateRefreshed = false
                    try {
                        publishState()
                        stateRefreshed = true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.e("CpuViewModel", "Failed to refresh after CPU tuning", error)
                    }
                    if (TuningPersistencePolicy.shouldPersist(applied, _bootEnabled.value, stateRefreshed)) {
                        saveCurrentCpuSettings()
                    }
                    applied
                } finally {
                    _pendingControlId.value = null
                }
            }
            _lastApplyResult.value = result
            onResult(result)
        }
    }

    fun setThermalLimit(limit: Int) {
        viewModelScope.launch {
            tuningMutex.withLock {
                withContext(Dispatchers.IO) { if (ThermalProvider.setThermalLimit(limit)) cachedLimit = limit }
                publishState()
            }
        }
    }

    fun disableThrottling() {
        viewModelScope.launch {
            tuningMutex.withLock {
                withContext(Dispatchers.IO) { ThermalProvider.disableThrottling() }
                publishState()
            }
        }
    }

    fun toggleBootEnabled(enabled: Boolean) {
        BootSettingsManager.setCpuEnabled(getApplication(), enabled)
        _bootEnabled.value = enabled
        if (enabled) saveCurrentCpuSettings()
    }

    private fun saveCurrentCpuSettings() {
        _cpuStatus.value.clusters.forEach { cluster ->
            BootSettingsManager.saveCpuPolicy(getApplication(), cluster.id, cluster.governor, cluster.rawMaxFreq, cluster.rawMinFreq)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { tuningMutex.withLock { publishState() } }
            finally { _isRefreshing.value = false }
        }
    }

    private suspend fun publishState() {
        val status = withContext(Dispatchers.IO) { CpuProvider.getCpuStatus() }
        _cpuStatus.value = status
        val thermal = withContext(Dispatchers.IO) { ThermalProvider.getThermalStatus(fetchLimit = false) }
        _thermalStatus.value = thermal.copy(currentLimit = cachedLimit)
    }
}
