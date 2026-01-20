package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.MemoryStatus
import com.ivarna.mkm.data.model.SwapStatus
import com.ivarna.mkm.utils.ShellUtils

object MemoryProvider {
    fun getMemoryStatus(): MemoryStatus {
        val memInfo = ShellUtils.readFile("/proc/meminfo")
        val total = parseValue(memInfo, "MemTotal")
        val available = parseValue(memInfo, "MemAvailable")
        val cached = parseValue(memInfo, "Cached")
        val active = parseValue(memInfo, "Active")
        val inactive = parseValue(memInfo, "Inactive")
        val buffers = parseValue(memInfo, "Buffers")
        val used = total - available
        
        return MemoryStatus(
            totalUi = ShellUtils.formatSize(total),
            usedUi = ShellUtils.formatSize(used),
            freeUi = ShellUtils.formatSize(available),
            usagePercent = if (total > 0) used.toFloat() / total else 0f,
            rawTotal = total,
            rawUsed = used,
            availableUi = ShellUtils.formatSize(available),
            cachedUi = ShellUtils.formatSize(cached),
            activeUi = ShellUtils.formatSize(active),
            inactiveUi = ShellUtils.formatSize(inactive),
            buffersUi = ShellUtils.formatSize(buffers)
        )
    }

    fun getSwapStatus(): SwapStatus {
        val memInfo = ShellUtils.readFile("/proc/meminfo")
        val total = parseValue(memInfo, "SwapTotal")
        val free = parseValue(memInfo, "SwapFree")
        val used = if (total > 0) total - free else 0L
        
        // Try to get path from /proc/swaps
        val swaps = ShellUtils.readFile("/proc/swaps").lines().filter { it.isNotBlank() }
        var path = "None"
        if (swaps.size >= 2) {
            val line = swaps[1].trim().split(Regex("\\s+"))
            if (line.size >= 1) {
                path = line[0]
            }
        }

        if (total > 0) {
            return SwapStatus(
                isActive = true,
                totalUi = ShellUtils.formatSize(total),
                usedUi = ShellUtils.formatSize(used),
                usagePercent = if (total > 0) used.toFloat() / total else 0f,
                path = path
            )
        }

        // Fallback or detailed info from /proc/swaps
        if (swaps.size < 2) return SwapStatus()
        
        val line = swaps[1].trim().split(Regex("\\s+"))
        if (line.size < 4) return SwapStatus()
        
        val sTotal = line[2].toLongOrNull() ?: 0L
        val sUsed = line[3].toLongOrNull() ?: 0L
        
        return SwapStatus(
            isActive = sTotal > 0,
            totalUi = ShellUtils.formatSize(sTotal),
            usedUi = ShellUtils.formatSize(sUsed),
            usagePercent = if (sTotal > 0) sUsed.toFloat() / sTotal else 0f,
            path = path
        )
    }

    private fun parseValue(memInfo: String, key: String): Long {
        val regex = Regex("$key:\\s+(\\d+)\\s+kB")
        return regex.find(memInfo)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
