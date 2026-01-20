package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.utils.ShellUtils
import java.io.File

object GpuProvider {
    private val maliPath = "/sys/class/misc/mali0/device/devfreq/13000000.mali"
    private val adrenoPath = "/sys/class/kgsl/kgsl-3d0"
    
    private fun getGpuPath(): String? {
        if (File(maliPath).exists()) return maliPath
        if (File(adrenoPath).exists()) return adrenoPath
        return null
    }

    fun getGpuStatus(): GpuStatus {
        val path = getGpuPath()
        
        // Load
        var load = 0f
        if (path == maliPath) {
            load = ShellUtils.readFile("/sys/kernel/ged/hal/gpu_utilization").toFloatOrNull() ?: 0f
        } else if (path == adrenoPath) {
            load = ShellUtils.readFile("$adrenoPath/gpubusy").split(Regex("\\s+"))
                .getOrNull(0)?.toFloatOrNull() ?: 0f
        }

        // Frequencies and Governor
        val devfreqPath = if (path == adrenoPath) "$adrenoPath/devfreq" else path
        
        var curFreq = ShellUtils.readFile(if (path == adrenoPath) "$adrenoPath/gpuclk" else "$devfreqPath/cur_freq").toLongOrNull() ?: 0L
        var minFreq = ShellUtils.readFile("$devfreqPath/min_freq").toLongOrNull() ?: 0L
        var maxFreq = ShellUtils.readFile("$devfreqPath/max_freq").toLongOrNull() ?: 0L
        
        val rawMinFreq = ShellUtils.readFile("$devfreqPath/min_freq")
        val rawMaxFreq = ShellUtils.readFile("$devfreqPath/max_freq")
        
        val governor = ShellUtils.readFile("$devfreqPath/governor")
        val availableGovernors = ShellUtils.readFile("$devfreqPath/available_governors")
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        
        val availableFrequencies = ShellUtils.readFile("$devfreqPath/available_frequencies")
            .split(Regex("\\s+")).filter { it.isNotBlank() }

        // Frequency normalization (Hz vs KHz vs MHz)
        if (curFreq > 10000000) curFreq /= 1000
        if (minFreq > 10000000) minFreq /= 1000
        if (maxFreq > 10000000) maxFreq /= 1000

        // Model
        val model = if (path == maliPath) "Mali-G72 (MediaTek)" else if (path == adrenoPath) "Adreno 640 (Qualcomm)" else "Unknown GPU"

        return GpuStatus(
            loadPercent = load / 100f,
            currentFreq = ShellUtils.formatFreq(curFreq),
            minFreq = ShellUtils.formatFreq(minFreq),
            maxFreq = ShellUtils.formatFreq(maxFreq),
            rawMinFreq = rawMinFreq,
            rawMaxFreq = rawMaxFreq,
            governor = if (governor.isEmpty()) "unknown" else governor,
            availableGovernors = if (availableGovernors.isEmpty()) listOf("dummy", "performance", "powersave") else availableGovernors,
            availableFrequencies = if (availableFrequencies.isEmpty()) listOf("300000", "500000", "800000") else availableFrequencies,
            model = model
        )
    }

    fun setGovernor(governor: String): Boolean {
        val path = getGpuPath() ?: return false
        val devfreqPath = if (path == adrenoPath) "$adrenoPath/devfreq" else path
        return ShellUtils.writeFile("$devfreqPath/governor", governor)
    }

    fun setFrequency(freq: String, isMax: Boolean): Boolean {
        val path = getGpuPath() ?: return false
        val devfreqPath = if (path == adrenoPath) "$adrenoPath/devfreq" else path
        val file = if (isMax) "max_freq" else "min_freq"
        return ShellUtils.writeFile("$devfreqPath/$file", freq)
    }
}
