package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.MemoryStatus
import com.ivarna.mkm.data.model.SwapDeviceInfo
import com.ivarna.mkm.data.model.SwapStatus
import com.ivarna.mkm.shell.ShellManager
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

        // Get detailed swap info from /proc/swaps using ShellManager (root/shizuku compatible)
        val devices = mutableListOf<SwapDeviceInfo>()
        val swapsOutput = ShellManager.exec("cat /proc/swaps").stdout
        if (swapsOutput.isNotEmpty()) {
            val lines = swapsOutput.lines()
            if (lines.size > 1) { // Skip header
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isBlank()) continue
                    
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 5) {
                        var path = parts[0]
                        // Fix for some devices reporting /local/tmp instead of /data/local/tmp
                        if (path.startsWith("/local/")) {
                            path = "/data" + path
                        } else if (path.startsWith("/dev/block/loop") || path.startsWith("/dev/loop")) {
                            // Resolve backing file for loop devices
                            val backingFile = ShellManager.exec("losetup $path").stdout
                            // Output format: /dev/block/loop0: [64768]:52523 (/data/local/tmp/swapfile)
                            val match = Regex("\\((.*?)\\)").find(backingFile)
                            if (match != null) {
                                path = match.groupValues[1]
                            }
                        }
                        
                        val type = parts[1]
                        val sizeParam = parts[2].toLongOrNull() ?: 0L // in KB
                        val usedParam = parts[3].toLongOrNull() ?: 0L // in KB
                        val priority = parts[4].toIntOrNull() ?: -1
                        
                        devices.add(
                            SwapDeviceInfo(
                                path = path,
                                type = type,
                                sizeUi = ShellUtils.formatSize(sizeParam),
                                usedUi = ShellUtils.formatSize(usedParam),
                                priority = priority
                            )
                        )
                    }
                }
            }
        }

        // Determine main path (first non-zram file if possible, else first device)
        val mainPath = devices.firstOrNull { it.type == "file" }?.path 
            ?: devices.firstOrNull()?.path 
            ?: "None"

        return SwapStatus(
            isActive = total > 0,
            totalUi = ShellUtils.formatSize(total),
            usedUi = ShellUtils.formatSize(used),
            usagePercent = if (total > 0) used.toFloat() / total else 0f,
            path = mainPath,
            devices = devices
        )
    }

    private fun parseValue(memInfo: String, key: String): Long {
        val regex = Regex("$key:\\s+(\\d+)\\s+kB")
        return regex.find(memInfo)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
