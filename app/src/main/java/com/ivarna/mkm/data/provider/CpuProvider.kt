package com.ivarna.mkm.data.provider

import android.util.Log
import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuCore
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.utils.ShellUtils
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.CpuScripts
import java.io.File

object CpuProvider {
    fun getCpuStatus(): CpuStatus {
        // Get CPU usage using the new modular CpuUtilizationProvider
        val perCoreUsage = CpuUtilizationProvider.getPerCoreCpuUsage(useFrequencyFallback = true)
        val cpuDir = File("/sys/devices/system/cpu")
        val cpuFiles = cpuDir.listFiles { _, name -> name.startsWith("cpu") && name.substring(3).all { it.isDigit() } }
        val coreCount = cpuFiles?.size ?: 0

        val clusters = mutableListOf<CpuCluster>()
        val policyDir = File("/sys/devices/system/cpu/cpufreq")
        
        val policyFiles = policyDir.listFiles { _, name -> name.startsWith("policy") }
            ?.sortedBy { it.name.removePrefix("policy").toInt() }

        policyFiles?.forEach { policy ->
            val id = policy.name.removePrefix("policy").toInt()
            val affectedCoresStr = readSystemFile("${policy.absolutePath}/affected_cpus")
            val affectedCores = if (affectedCoresStr.isNotEmpty()) {
                parseCoreList(affectedCoresStr)
            } else {
                listOf(id)
            }
            
            val coreRange = if (affectedCores.isNotEmpty()) {
                affectedCores.min()..affectedCores.max()
            } else {
                0..0
            }

            val governor = readSystemFile("${policy.absolutePath}/scaling_governor")
            val curFreq = readSystemFile("${policy.absolutePath}/scaling_cur_freq").toLongOrNull() ?: 0L
            val minFreq = readSystemFile("${policy.absolutePath}/scaling_min_freq").toLongOrNull() ?: 0L
            val maxFreq = readSystemFile("${policy.absolutePath}/scaling_max_freq").toLongOrNull() ?: 0L
            
            // Get hardware min/max for accurate usage calculation
            val hwMinFreq = readSystemFile("${policy.absolutePath}/cpuinfo_min_freq").toLongOrNull() ?: minFreq
            val hwMaxFreq = readSystemFile("${policy.absolutePath}/cpuinfo_max_freq").toLongOrNull() ?: maxFreq
            
            val rawMinFreq = readSystemFile("${policy.absolutePath}/scaling_min_freq")
            val rawMaxFreq = readSystemFile("${policy.absolutePath}/scaling_max_freq")
            
            val availableGovernors = readSystemFile("${policy.absolutePath}/scaling_available_governors")
                .split(Regex("\\s+")).filter { it.isNotBlank() }
            
            val availableFrequencies = readSystemFile("${policy.absolutePath}/scaling_available_frequencies")
                .split(Regex("\\s+")).filter { it.isNotBlank() }

            val cores = affectedCores.map { coreId ->
                val coreCurFreqFile = File("/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_cur_freq")
                val coreCurFreq = if (coreCurFreqFile.exists()) {
                    readSystemFile(coreCurFreqFile.absolutePath).toLongOrNull() ?: curFreq
                } else curFreq
                
                // Get CPU usage from CpuUtilizationProvider (primary method)
                // If not available, fallback to frequency-based calculation (already handled by the provider)
                val usage = perCoreUsage[coreId] ?: run {
                    // Additional fallback if core not in map: calculate from frequency
                    if (hwMaxFreq > hwMinFreq && coreCurFreq >= hwMinFreq) {
                        ((coreCurFreq - hwMinFreq).toFloat() / (hwMaxFreq - hwMinFreq)).coerceIn(0f, 1f)
                    } else if (coreCurFreq > 0 && hwMaxFreq > 0) {
                        (coreCurFreq.toFloat() / hwMaxFreq).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
                
                Log.d("CpuProvider", "Core $coreId: freq=$coreCurFreq, usage=$usage (hwMin=$hwMinFreq, hwMax=$hwMaxFreq)")
                
                CpuCore(
                    id = coreId,
                    currentFreq = ShellUtils.formatFreq(coreCurFreq),
                    usagePercent = usage,
                    governor = governor,
                    minFreq = ShellUtils.formatFreq(minFreq),
                    maxFreq = ShellUtils.formatFreq(maxFreq),
                    rawMinFreq = rawMinFreq,
                    rawMaxFreq = rawMaxFreq,
                    availableGovernors = availableGovernors,
                    availableFrequencies = availableFrequencies
                )
            }

            clusters.add(CpuCluster(
                id = id,
                coreRange = coreRange,
                governor = governor,
                currentFreq = ShellUtils.formatFreq(curFreq),
                minFreq = ShellUtils.formatFreq(minFreq),
                maxFreq = ShellUtils.formatFreq(maxFreq),
                rawMinFreq = rawMinFreq,
                rawMaxFreq = rawMaxFreq,
                hwMinFreq = hwMinFreq.toString(),
                hwMaxFreq = hwMaxFreq.toString(),
                availableGovernors = availableGovernors,
                availableFrequencies = availableFrequencies,
                cores = cores
            ))
        }

        // Calculate overall usage using CpuUtilizationProvider
        // This will use /proc/stat if available (more accurate), or frequency-based as fallback
        val overallUsage = CpuUtilizationProvider.getOverallCpuUsage(useFrequencyFallback = true)

        // Calculate average frequency across all clusters
        val avgFreqKhz = if (clusters.isNotEmpty()) {
            clusters.map { it.currentFreq.split(" ")[0].replace(",", ".").toDouble() * (if (it.currentFreq.contains("GHz")) 1000000 else 1000) }.average().toLong()
        } else {
            0L
        }
        
        Log.d("CpuProvider", "Overall usage: $overallUsage (from $coreCount cores), Avg Freq: $avgFreqKhz")

        return CpuStatus(
            cpuName = getCpuName(),
            overallUsage = if (overallUsage.isNaN() || overallUsage < 0) 0f else overallUsage.coerceAtMost(1f),
            clusters = clusters,
            totalCores = coreCount,
            avgFreq = ShellUtils.formatFreq(avgFreqKhz)
        )
    }
    
    /**
     * Reads a system file. execution fails with permission errors, falls back to SU.
     */
    private fun readSystemFile(path: String): String {
        // 1. Try normal read first (faster)
        val content = ShellUtils.readFile(path)
        if (content.isNotEmpty()) return content
        
        // 2. Fallback to SU if empty (assuming file exists but not readable)
        // Check if file exists first to avoid unnecessary SU calls? No, ShellUtils.readFile checks existence too.
        // But if it exists and is just empty, we might waste SU call.
        // However, sysfs files usually return something if readable.
        
        val result = ShellManager.exec("cat \"$path\"")
        return if (result.isSuccess) result.stdout else ""
    }

    private fun getCpuName(): String {
        var name = "Unknown"
        
        // 1. Try Build.SOC_MODEL (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val socModel = android.os.Build.SOC_MODEL
            if (socModel != null && socModel != "unknown" && socModel.isNotEmpty()) {
                name = socModel
            }
        }
        
        // 2. Try /proc/cpuinfo Hardware line
        if (name == "Unknown") {
            val cpuInfo = ShellUtils.readFile("/proc/cpuinfo")
            if (cpuInfo.isNotEmpty()) {
                val lines = cpuInfo.split("\n")
                for (line in lines) {
                    if (line.trim().startsWith("Hardware", ignoreCase = true)) {
                        val parts = line.split(":")
                        if (parts.size > 1) {
                            name = parts[1].trim()
                            break
                        }
                    }
                }
            }
        }
        
        // 3. Fallback to model name if still unknown (some kernels put it there)
        if (name == "Unknown") {
            val cpuInfo = ShellUtils.readFile("/proc/cpuinfo")
            if (cpuInfo.isNotEmpty()) {
                val lines = cpuInfo.split("\n")
                for (line in lines) {
                    if (line.trim().startsWith("model name", ignoreCase = true)) {
                        val parts = line.split(":")
                        if (parts.size > 1) {
                            name = parts[1].trim()
                            break
                        }
                    }
                }
            }
        }
        
        // 4. Last fallback Build.HARDWARE
        if (name == "Unknown") {
            name = android.os.Build.HARDWARE
        }
        
        return name
    }

    private fun parseCoreList(content: String): List<Int> {
        val cores = mutableListOf<Int>()
        content.split(Regex("[,\\s+]")).filter { it.isNotBlank() }.forEach { part ->
            if (part.contains("-")) {
                val range = part.split("-")
                if (range.size == 2) {
                    val start = range[0].trim().toIntOrNull()
                    val end = range[1].trim().toIntOrNull()
                    if (start != null && end != null) {
                        for (i in start..end) cores.add(i)
                    }
                }
            } else {
                part.trim().toIntOrNull()?.let { cores.add(it) }
            }
        }
        return cores.distinct().sorted()
    }

    fun setGovernor(policyId: Int, governor: String): Boolean {
        return ShellManager.exec(CpuScripts.setGovernor(policyId, governor)).isSuccess
    }

    fun setFrequency(policyId: Int, freqKhz: String, isMax: Boolean): Boolean {
        return if (isMax) {
            ShellManager.exec(CpuScripts.setMaxFreq(policyId, freqKhz)).isSuccess
        } else {
            ShellManager.exec(CpuScripts.setMinFreq(policyId, freqKhz)).isSuccess
        }
    }

    fun setGovernorForCore(coreId: Int, governor: String): Boolean {
        // Find which policy this core belong to
        val policyPath = findPolicyForCore(coreId) ?: "/sys/devices/system/cpu/cpu$coreId/cpufreq"
        return ShellManager.exec("echo \"$governor\" > \"$policyPath/scaling_governor\"").isSuccess
    }

    fun setFrequencyForCore(coreId: Int, freqKhz: String, isMax: Boolean): Boolean {
        val policyPath = findPolicyForCore(coreId) ?: "/sys/devices/system/cpu/cpu$coreId/cpufreq"
        val file = if (isMax) "scaling_max_freq" else "scaling_min_freq"
        val result = ShellManager.exec("printf '%s' '$freqKhz' > $policyPath/$file")
        
        // Log error if failed
        if (!result.isSuccess) {
            android.util.Log.e("CpuProvider", "Failed to set freq for core $coreId: ${result.stderr}")
        }
        
        return result.isSuccess
    }

    fun findPolicyForCore(coreId: Int): String? {
        val policyDir = File("/sys/devices/system/cpu/cpufreq")
        policyDir.listFiles { _, name -> name.startsWith("policy") }?.forEach { policy ->
            val affectedCores = parseCoreList(ShellUtils.readFile("${policy.absolutePath}/affected_cpus"))
            if (affectedCores.contains(coreId)) return policy.absolutePath
        }
        return null
    }
}
