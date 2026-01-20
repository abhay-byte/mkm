package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.utils.ShellUtils
import java.io.File

object CpuProvider {
    private var lastTotal = 0L
    private var lastIdle = 0L

    fun getCpuStatus(): CpuStatus {
        val curFreq = ShellUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").toLongOrNull() ?: 0L
        val maxFreq = ShellUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq").toLongOrNull() ?: 0L
        val governor = ShellUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        
        // Basic CPU usage calculation
        val stat = ShellUtils.readFile("/proc/stat").lines().firstOrNull() ?: ""
        val parts = stat.split(Regex("\\s+")).filter { it.isNotBlank() }
        var usage = 0.45f // default fallback
        
        if (parts.size >= 5 && parts[0] == "cpu") {
            val user = parts[1].toLongOrNull() ?: 0L
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: 0L
            val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            
            val total = user + nice + system + idle + iowait + irq + softirq
            val diffTotal = total - lastTotal
            val diffIdle = idle - lastIdle
            
            if (diffTotal > 0) {
                usage = (diffTotal - diffIdle).toFloat() / diffTotal
            }
            
            lastTotal = total
            lastIdle = idle
        }

        return CpuStatus(
            currentFreq = ShellUtils.formatFreq(curFreq),
            maxFreq = ShellUtils.formatFreq(maxFreq),
            governor = governor,
            usagePercent = usage
        )
    }
}
