package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.utils.ShellUtils

object GpuProvider {
    fun getGpuStatus(): GpuStatus {
        // Common Adreno paths
        var freq = ShellUtils.readFile("/sys/class/kgsl/kgsl-3d0/gpuclk").toLongOrNull() 
            ?: ShellUtils.readFile("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq").toLongOrNull() ?: 0L
        
        // Convert to KHz if it seems to be in Hz
        if (freq > 10000000) freq /= 1000
        
        val load = ShellUtils.readFile("/sys/class/kgsl/kgsl-3d0/gpubusy").split(Regex("\\s+"))
            .getOrNull(0)?.toFloatOrNull() ?: 0f
            
        return GpuStatus(
            currentFreq = ShellUtils.formatFreq(freq),
            loadPercent = load / 100f
        )
    }
}
