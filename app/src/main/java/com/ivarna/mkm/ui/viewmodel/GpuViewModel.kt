package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.provider.GpuProvider
import com.ivarna.mkm.service.BootSettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GpuViewModel(application: Application) : AndroidViewModel(application) {
    private val _gpuStatus = MutableStateFlow(GpuStatus())
    val gpuStatus = _gpuStatus.asStateFlow()

    private var freezeValues = false

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _bootEnabled = MutableStateFlow(BootSettingsManager.isGpuEnabled(application))
    val bootEnabled = _bootEnabled.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                val status = GpuProvider.getGpuStatus()
                _gpuStatus.value = status.copy(
                    setOnBoot = _bootEnabled.value,
                    freezeValues = freezeValues
                )
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

    fun toggleBootEnabled(enabled: Boolean) {
        BootSettingsManager.setGpuEnabled(getApplication(), enabled)
        _bootEnabled.value = enabled
        _gpuStatus.value = _gpuStatus.value.copy(setOnBoot = enabled)
        if (enabled) {
            saveCurrentGpuSettings()
        }
    }

    private fun saveCurrentGpuSettings() {
        val status = _gpuStatus.value
        BootSettingsManager.saveGpuSettings(
            getApplication(),
            status.governor,
            status.rawMaxFreq,
            status.rawMinFreq,
            status.rawTargetFreq
        )
    }

    fun toggleFreezeValues(enabled: Boolean) {
        freezeValues = enabled
        _gpuStatus.value = _gpuStatus.value.copy(freezeValues = enabled)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            GpuProvider.clearCache()
            _gpuStatus.value = GpuProvider.getGpuStatus().copy(
                setOnBoot = _bootEnabled.value,
                freezeValues = freezeValues
            )
            delay(500)
            _isRefreshing.value = false
        }
    }
}
