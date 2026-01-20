package com.ivarna.mkm.data.provider

import android.util.Log
import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuCore
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.utils.ShellUtils
import java.io.File

object CpuProvider {
    fun getCpuStatus(): CpuStatus {
        val cpuDir = File("/sys/devices/system/cpu")
        val cpuFiles = cpuDir.listFiles { _, name -> name.startsWith("cpu") && name.substring(3).all { it.isDigit() } }
        val coreCount = cpuFiles?.size ?: 0

        val clusters = mutableListOf<CpuCluster>()
        val policyDir = File("/sys/devices/system/cpu/cpufreq")
        
        val policyFiles = policyDir.listFiles { _, name -> name.startsWith("policy") }
            ?.sortedBy { it.name.removePrefix("policy").toInt() }

        policyFiles?.forEach { policy ->
            val id = policy.name.removePrefix("policy").toInt()
            val affectedCoresStr = ShellUtils.readFile("${policy.absolutePath}/affected_cpus")
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

            val governor = ShellUtils.readFile("${policy.absolutePath}/scaling_governor")
            val curFreq = ShellUtils.readFile("${policy.absolutePath}/scaling_cur_freq").toLongOrNull() ?: 0L
            val minFreq = ShellUtils.readFile("${policy.absolutePath}/scaling_min_freq").toLongOrNull() ?: 0L
            val maxFreq = ShellUtils.readFile("${policy.absolutePath}/scaling_max_freq").toLongOrNull() ?: 0L
            
            // Get hardware min/max for accurate usage calculation
            val hwMinFreq = ShellUtils.readFile("${policy.absolutePath}/cpuinfo_min_freq").toLongOrNull() ?: minFreq
            val hwMaxFreq = ShellUtils.readFile("${policy.absolutePath}/cpuinfo_max_freq").toLongOrNull() ?: maxFreq
            
            val rawMinFreq = ShellUtils.readFile("${policy.absolutePath}/scaling_min_freq")
            val rawMaxFreq = ShellUtils.readFile("${policy.absolutePath}/scaling_max_freq")
            
            val availableGovernors = ShellUtils.readFile("${policy.absolutePath}/scaling_available_governors")
                .split(Regex("\\s+")).filter { it.isNotBlank() }
            
            val availableFrequencies = ShellUtils.readFile("${policy.absolutePath}/scaling_available_frequencies")
                .split(Regex("\\s+")).filter { it.isNotBlank() }

            val cores = affectedCores.map { coreId ->
                val coreCurFreqFile = File("/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_cur_freq")
                val coreCurFreq = if (coreCurFreqFile.exists()) {
                    ShellUtils.readFile(coreCurFreqFile.absolutePath).toLongOrNull() ?: curFreq
                } else curFreq
                
                // Calculate usage based on frequency: (current - hwMin) / (hwMax - hwMin)
                val usage = if (hwMaxFreq > hwMinFreq && coreCurFreq >= hwMinFreq) {
                    ((coreCurFreq - hwMinFreq).toFloat() / (hwMaxFreq - hwMinFreq)).coerceIn(0f, 1f)
                } else if (coreCurFreq > 0 && hwMaxFreq > 0) {
                    // Fallback: simple ratio if hw min/max not available
                    (coreCurFreq.toFloat() / hwMaxFreq).coerceIn(0f, 1f)
                } else {
                    0f
                }
                
                Log.d("CpuProvider", "Core $coreId: freq=$coreCurFreq, usage=$usage (hwMin=$hwMinFreq, hwMax=$hwMaxFreq)")
                
                CpuCore(
                    id = coreId,
                    currentFreq = ShellUtils.formatFreq(coreCurFreq),
                    usagePercent = usage
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
                availableGovernors = availableGovernors,
                availableFrequencies = availableFrequencies,
                cores = cores
            ))
        }

        // Calculate overall usage
        val allCores = clusters.flatMap { it.cores }
        val overallUsage = if (allCores.isNotEmpty()) {
            allCores.map { it.usagePercent }.average().toFloat()
        } else {
            0f
        }
        
        Log.d("CpuProvider", "Overall usage: $overallUsage (from ${allCores.size} cores)")

        return CpuStatus(
            overallUsage = if (overallUsage.isNaN() || overallUsage < 0) 0f else overallUsage.coerceAtMost(1f),
            clusters = clusters,
            totalCores = coreCount
        )
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
        return ShellUtils.writeFile("/sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor", governor)
    }

    fun setFrequency(policyId: Int, freqKhz: String, isMax: Boolean): Boolean {
        val path = if (isMax) "scaling_max_freq" else "scaling_min_freq"
        return ShellUtils.writeFile("/sys/devices/system/cpu/cpufreq/policy$policyId/$path", freqKhz)
    }
}
