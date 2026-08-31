package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.model.TuningMutationCoordinator
import com.ivarna.mkm.data.model.TuningPersistencePolicy
import com.ivarna.mkm.data.provider.GpuProvider
import com.ivarna.mkm.service.BootSettingsManager
import com.ivarna.mkm.service.GameBoostRegistry
import com.ivarna.mkm.service.KernelTuningCoordinator
import com.ivarna.mkm.util.AppVisibilityMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GpuViewModel(application: Application) : AndroidViewModel(application) {
    private val _gpuStatus = MutableStateFlow(GpuStatus())
    val gpuStatus = _gpuStatus.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _bootEnabled = MutableStateFlow(BootSettingsManager.isGpuEnabled(application))
    val bootEnabled = _bootEnabled.asStateFlow()
    private val _pendingControlId = MutableStateFlow<String?>(null)
    val pendingControlId = _pendingControlId.asStateFlow()
    private val _lastApplyResult = MutableStateFlow<ApplyResult?>(null)
    val lastApplyResult = _lastApplyResult.asStateFlow()

    private val tuningCoordinator = TuningMutationCoordinator()
    private var freezeValues = false

    init { startMonitoring() }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                if (AppVisibilityMonitor.isForeground.value) tuningCoordinator.withObservation { publishState() }
                delay(1000L)
            }
        }
    }

    fun setGovernor(governor: String, onResult: (ApplyResult) -> Unit = {}) =
        if (GameBoostRegistry.ownsTuning()) {
            onResult(ApplyResult.Failed("Managed by Game Boost. Disable Game Boost to edit."))
        } else mutate("gpu-governor", { GpuProvider.applyGovernor(governor) }, onResult)

    fun setFrequency(freq: String, type: Int, onResult: (ApplyResult) -> Unit = {}) {
        if (GameBoostRegistry.ownsTuning()) {
            onResult(ApplyResult.Failed("Managed by Game Boost. Disable Game Boost to edit."))
            return
        }
        val value = freq.toLongOrNull()
        mutate("gpu-${when (type) { 0 -> "min"; 1 -> "max"; else -> "target" }}", {
            if (value == null) ApplyResult.Failed("Invalid GPU frequency")
            else when (type) {
                0 -> GpuProvider.applyRange(desiredMin = value)
                1 -> GpuProvider.applyRange(desiredMax = value)
                2 -> GpuProvider.applyTarget(value)
                else -> ApplyResult.Failed("Unknown GPU frequency control")
            }
        }, onResult)
    }

    private fun mutate(controlId: String, operation: () -> ApplyResult, onResult: (ApplyResult) -> Unit) {
        viewModelScope.launch {
            val result = tuningCoordinator.withMutation(controlId, { _pendingControlId.value = it }) {
                    val applied = try {
                        withContext(Dispatchers.IO) { KernelTuningCoordinator.withMutation { operation() } }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ApplyResult.Failed(error.message ?: "GPU tuning operation failed")
                    }
                    var stateRefreshed = false
                    try {
                        publishState()
                        stateRefreshed = true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.e("GpuViewModel", "Failed to refresh after GPU tuning", error)
                    }
                    if (TuningPersistencePolicy.shouldPersist(applied, _bootEnabled.value, stateRefreshed)) {
                        saveCurrentGpuSettings()
                    }
                    applied
            }
            _lastApplyResult.value = result
            onResult(result)
        }
    }

    fun toggleBootEnabled(enabled: Boolean) {
        if (GameBoostRegistry.ownsTuning()) return
        BootSettingsManager.setGpuEnabled(getApplication(), enabled)
        _bootEnabled.value = enabled
        _gpuStatus.value = _gpuStatus.value.copy(setOnBoot = enabled)
        if (enabled) saveCurrentGpuSettings()
    }

    private fun saveCurrentGpuSettings() {
        val status = _gpuStatus.value
        BootSettingsManager.saveGpuSettings(getApplication(), status.governor, status.rawMaxFreq, status.rawMinFreq, status.rawTargetFreq)
    }

    fun toggleFreezeValues(enabled: Boolean) {
        freezeValues = enabled
        _gpuStatus.value = _gpuStatus.value.copy(freezeValues = enabled)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { tuningCoordinator.withObservation { publishState() } }
            finally { _isRefreshing.value = false }
        }
    }

    /** Explicit redetection is separate from ordinary polling. */
    fun redetect() {
        GpuProvider.clearCache()
        refresh()
    }

    private suspend fun publishState() {
        _gpuStatus.value = withContext(Dispatchers.IO) {
            GpuProvider.getGpuStatus().copy(setOnBoot = _bootEnabled.value, freezeValues = freezeValues)
        }
    }
}
