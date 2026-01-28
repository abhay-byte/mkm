package com.ivarna.mkm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivarna.mkm.data.model.BenchmarkStatus
import com.ivarna.mkm.data.model.CpuEfficiencyResult
import com.ivarna.mkm.data.model.GpuEfficiencyResult
import com.ivarna.mkm.data.model.PowerStatus
import com.ivarna.mkm.data.provider.PowerCalibrationManager
import com.ivarna.mkm.data.provider.PowerProvider
import com.ivarna.mkm.shell.ShellManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PowerViewModel(application: Application) : AndroidViewModel(application) {

    private val powerProvider = PowerProvider()
    private val calibrationManager = PowerCalibrationManager(application)

    private val _powerStatus = MutableStateFlow(PowerStatus(multiplier = calibrationManager.getMultiplier()))
    val powerStatus: StateFlow<PowerStatus> = _powerStatus.asStateFlow()
    
    // CPU Bench
    private val _cpuResults = MutableStateFlow<List<CpuEfficiencyResult>>(emptyList())
    val cpuResults: StateFlow<List<CpuEfficiencyResult>> = _cpuResults.asStateFlow()
    
    private val _cpuBenchStatus = MutableStateFlow<BenchmarkStatus>(BenchmarkStatus.Idle)
    val cpuBenchStatus: StateFlow<BenchmarkStatus> = _cpuBenchStatus.asStateFlow()
    
    // GPU Bench
    private val _gpuResults = MutableStateFlow<List<GpuEfficiencyResult>>(emptyList())
    val gpuResults: StateFlow<List<GpuEfficiencyResult>> = _gpuResults.asStateFlow()
    
    private val _gpuBenchStatus = MutableStateFlow<BenchmarkStatus>(BenchmarkStatus.Idle)
    val gpuBenchStatus: StateFlow<BenchmarkStatus> = _gpuBenchStatus.asStateFlow()

    private var monitoringJob: Job? = null

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            while (true) {
                val currentMultiplier = calibrationManager.getMultiplier()
                _powerStatus.value = powerProvider.getPowerStatus(currentMultiplier)
                delay(1000) // 1 second update rate
            }
        }
    }

    fun saveCalibrationMultiplier(multiplier: Float) {
        calibrationManager.saveMultiplier(multiplier)
    }
    
    private val _realTimeLogs = MutableStateFlow("")
    val realTimeLogs: StateFlow<String> = _realTimeLogs.asStateFlow()

    fun runCpuBenchmark() {
        if (_cpuBenchStatus.value is BenchmarkStatus.Running) return
        
        _realTimeLogs.value = "Starting CPU Benchmark..."
        
        viewModelScope.launch {
            _cpuBenchStatus.value = BenchmarkStatus.Running
            try {
                val currentMultiplier = calibrationManager.getMultiplier()
                val result = powerProvider.runCpuBenchmarkKotlin(
                    onProgress = { logLine ->
                        _realTimeLogs.value = _realTimeLogs.value + "\n" + logLine
                    },
                    multiplier = currentMultiplier
                )
                _cpuResults.value = result.data
                _cpuBenchStatus.value = BenchmarkStatus.Completed(
                    "Benchmark Finished. Found ${result.data.size} points.",
                    result.logs
                )
            } catch (e: Exception) {
                _cpuBenchStatus.value = BenchmarkStatus.Error(e.message ?: "Unknown Error", e.stackTraceToString())
            }
        }
    }
    
    fun runGpuBenchmark() {
        if (_gpuBenchStatus.value is BenchmarkStatus.Running) return
        
        _realTimeLogs.value = "Starting GPU Benchmark..."
        
        viewModelScope.launch {
            _gpuBenchStatus.value = BenchmarkStatus.Running
            try {
                val currentMultiplier = calibrationManager.getMultiplier()
                val result = powerProvider.runGpuBenchmark(
                    onProgress = { logLine ->
                        _realTimeLogs.value = _realTimeLogs.value + "\n" + logLine
                    },
                    multiplier = currentMultiplier
                )
                _gpuResults.value = result.data
                _gpuBenchStatus.value = BenchmarkStatus.Completed(
                    "Benchmark Finished. Found ${result.data.size} points.",
                    result.logs
                )
            } catch (e: Exception) {
                _gpuBenchStatus.value = BenchmarkStatus.Error(e.message ?: "Unknown Error", e.stackTraceToString())
            }
        }
    }

    fun clearLogs() {
        _realTimeLogs.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        monitoringJob?.cancel()
    }
}
