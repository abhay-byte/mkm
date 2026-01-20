package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.MemoryStatus
import com.ivarna.mkm.data.model.SwapStatus
import com.ivarna.mkm.utils.ShellUtils

object MemoryProvider {
    fun getMemoryStatus(): MemoryStatus {
        val memInfo = ShellUtils.readFile("/proc/meminfo")
        val total = parseValue(memInfo, "MemTotal")
        val available = parseValue(memInfo, "MemAvailable")
        val used = total - available
        
        return MemoryStatus(
            totalUi = ShellUtils.formatSize(total),
            usedUi = ShellUtils.formatSize(used),
            freeUi = ShellUtils.formatSize(available),
            usagePercent = if (total > 0) used.toFloat() / total else 0f,
            rawTotal = total,
            rawUsed = used
        )
    }

    fun getSwapStatus(): SwapStatus {
        val memInfo = ShellUtils.readFile("/proc/meminfo")
        val total = parseValue(memInfo, "SwapTotal")
        val free = parseValue(memInfo, "SwapFree")
        val used = if (total > 0) total - free else 0L
        
        if (total > 0) {
            return SwapStatus(
                isActive = true,
                totalUi = ShellUtils.formatSize(total),
                usedUi = ShellUtils.formatSize(used),
                usagePercent = if (total > 0) used.toFloat() / total else 0f
            )
        }

        // Fallback to /proc/swaps if meminfo doesn't have it or is 0
        val swaps = ShellUtils.readFile("/proc/swaps").lines().filter { it.isNotBlank() }
        if (swaps.size < 2) return SwapStatus()
        
        val line = swaps[1].trim().split(Regex("\\s+"))
        if (line.size < 4) return SwapStatus()
        
        val sTotal = line[2].toLongOrNull() ?: 0L
        val sUsed = line[3].toLongOrNull() ?: 0L
        
        return SwapStatus(
            isActive = sTotal > 0,
            totalUi = ShellUtils.formatSize(sTotal),
            usedUi = ShellUtils.formatSize(sUsed),
            usagePercent = if (sTotal > 0) sUsed.toFloat() / sTotal else 0f
        )
    }

    private fun parseValue(memInfo: String, key: String): Long {
        val regex = Regex("$key:\\s+(\\d+)\\s+kB")
        return regex.find(memInfo)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
