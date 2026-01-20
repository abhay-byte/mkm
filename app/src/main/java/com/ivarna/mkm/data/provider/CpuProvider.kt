package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuCore
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.utils.ShellUtils
import java.io.File

object CpuProvider {
    private val lastTotal = mutableMapOf<Int, Long>()
    private val lastIdle = mutableMapOf<Int, Long>()

    fun getCpuStatus(): CpuStatus {
        val cpuDir = File("/sys/devices/system/cpu")
        val cpuFiles = cpuDir.listFiles { _, name -> name.startsWith("cpu") && name.substring(3).all { it.isDigit() } }
        val coreCount = cpuFiles?.size ?: 0

        val allStats = readProcStat()
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
                
                CpuCore(
                    id = coreId,
                    currentFreq = ShellUtils.formatFreq(coreCurFreq),
                    usagePercent = calculateUsage(allStats[coreId], coreId)
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
            calculateUsage(allStats[-1], -1)
        }

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

    private data class StatData(val total: Long, val idle: Long)

    private fun readProcStat(): Map<Int, StatData> {
        val stats = mutableMapOf<Int, StatData>()
        try {
            val content = ShellUtils.readFile("/proc/stat")
            content.lines().forEach { line ->
                val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (parts.isEmpty()) return@forEach
                
                val cpuId = when {
                    parts[0] == "cpu" -> -1
                    parts[0].startsWith("cpu") -> parts[0].substring(3).toIntOrNull() ?: return@forEach
                    else -> return@forEach
                }

                if (parts.size >= 5) {
                    val user = parts[1].toLongOrNull() ?: 0L
                    val nice = parts[2].toLongOrNull() ?: 0L
                    val system = parts[3].toLongOrNull() ?: 0L
                    val idle = parts[4].toLongOrNull() ?: 0L
                    val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                    val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                    val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                    
                    val total = user + nice + system + idle + iowait + irq + softirq
                    stats[cpuId] = StatData(total, idle)
                }
            }
        } catch (e: Exception) {
            // Log or handle
        }
        return stats
    }

    private fun calculateUsage(current: StatData?, id: Int): Float {
        if (current == null) return 0f
        
        val lastT = lastTotal[id] ?: 0L
        val lastI = lastIdle[id] ?: 0L
        
        val diffTotal = current.total - lastT
        val diffIdle = current.idle - lastI
        
        lastTotal[id] = current.total
        lastIdle[id] = current.idle

        return if (diffTotal > 0) {
            val usage = (diffTotal - diffIdle).toFloat() / diffTotal
            usage.coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    fun setGovernor(policyId: Int, governor: String): Boolean {
        return ShellUtils.writeFile("/sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor", governor)
    }

    fun setFrequency(policyId: Int, freqKhz: String, isMax: Boolean): Boolean {
        val path = if (isMax) "scaling_max_freq" else "scaling_min_freq"
        return ShellUtils.writeFile("/sys/devices/system/cpu/cpufreq/policy$policyId/$path", freqKhz)
    }
}
