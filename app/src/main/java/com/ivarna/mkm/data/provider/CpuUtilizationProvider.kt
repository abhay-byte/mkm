package com.ivarna.mkm.data.provider

import android.util.Log
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.CpuUtilizationScripts
import com.ivarna.mkm.utils.ShellUtils
import java.io.File

/**
 * Modular and reusable CPU utilization provider.
 * 
 * This component provides CPU usage calculation with two methods:
 * 1. Direct /proc/stat reading (primary, root access recommended)
 * 2. Frequency-based calculation (fallback)
 * 
 * Usage:
 * ```
 * val utilization = CpuUtilizationProvider.getOverallCpuUsage()
 * val perCoreUtilization = CpuUtilizationProvider.getPerCoreCpuUsage()
 * ```
 */
object CpuUtilizationProvider {
    private const val TAG = "CpuUtilizationProvider"
    
    // Store previous measurements for delta calculation
    private data class CpuStat(
        val user: Long,
        val nice: Long,
        val system: Long,
        val idle: Long,
        val iowait: Long,
        val irq: Long,
        val softirq: Long,
        val steal: Long,
        val guest: Long,
        val guestNice: Long
    ) {
        val totalTime: Long get() = user + nice + system + idle + iowait + irq + softirq + steal
        val idleTime: Long get() = idle + iowait
        val activeTime: Long get() = totalTime - idleTime
    }
    
    private var lastOverallStat: CpuStat? = null
    private var lastPerCoreStat: Map<Int, CpuStat> = emptyMap()
    
    // Throttled calculation caches
    private var cachedOverallUsage: Float = 0f
    private var cachedPerCoreUsage: Map<Int, Float> = emptyMap()
    private var lastCalculationTime: Long = 0
    private const val MIN_UPDATE_INTERVAL = 900L // 900ms minimum between new measurements
    
    /**
     * Get overall CPU usage percentage (0.0 to 1.0).
     * Uses /proc/stat if available via root, otherwise falls back to frequency-based calculation.
     * 
     * @param useFrequencyFallback If true, uses frequency-based calculation when /proc/stat fails
     * @return CPU usage as a float between 0.0 and 1.0
     */
    fun getOverallCpuUsage(useFrequencyFallback: Boolean = true): Float {
        updateUsageIfNeeded(useFrequencyFallback)
        return cachedOverallUsage
    }
    
    /**
     * Get per-core CPU usage percentages.
     * Uses /proc/stat if available, otherwise falls back to frequency-based calculation per core.
     * 
     * @return Map of core ID to usage percentage (0.0 to 1.0)
     */
    fun getPerCoreCpuUsage(useFrequencyFallback: Boolean = true): Map<Int, Float> {
        updateUsageIfNeeded(useFrequencyFallback)
        return cachedPerCoreUsage
    }
    
    /**
     * Update CPU usage values if the cache has expired.
     */
    @Synchronized
    private fun updateUsageIfNeeded(useFrequencyFallback: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastCalculationTime < MIN_UPDATE_INTERVAL && (cachedOverallUsage > 0 || cachedPerCoreUsage.isNotEmpty())) {
            return
        }
        
        // Try /proc/stat method for both per-core and overall at once (more efficient)
        val procStatSuccess = updateFromProcStat()
        
        if (!procStatSuccess && useFrequencyFallback) {
            // Fallback to frequency-based calculation
            cachedOverallUsage = getFrequencyBasedCpuUsage()
            cachedPerCoreUsage = getFrequencyBasedPerCoreUsage()
            Log.d(TAG, "CPU usage updated from frequency (fallback): ${(cachedOverallUsage * 100).toInt()}%")
        } else if (procStatSuccess) {
            Log.d(TAG, "CPU usage updated from /proc/stat: ${(cachedOverallUsage * 100).toInt()}%")
        }
        
        lastCalculationTime = now
    }

    private fun updateFromProcStat(): Boolean {
        try {
            val result = ShellManager.exec(CpuUtilizationScripts.getCpuStatAll())
            
            if (!result.isSuccess || result.stdout.isBlank()) {
                Log.w(TAG, "Failed to read /proc/stat: ${result.stderr}")
                return false
            }
            
            val lines = result.stdout.trim().split("\n")
            val newPerCoreStats = mutableMapOf<Int, CpuStat>()
            val newPerCoreUsage = mutableMapOf<Int, Float>()
            var newOverallUsage = -1f
            
            lines.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.isEmpty()) return@forEach
                
                if (parts[0] == "cpu") {
                    // Overall CPU stat
                    val stat = parseCpuStatLine(line) ?: return@forEach
                    val prevStat = lastOverallStat
                    lastOverallStat = stat
                    
                    if (prevStat != null) {
                        val totalDelta = stat.totalTime - prevStat.totalTime
                        val idleDelta = stat.idleTime - prevStat.idleTime
                        if (totalDelta > 0) {
                            newOverallUsage = (1f - (idleDelta.toFloat() / totalDelta.toFloat())).coerceIn(0f, 1f)
                        }
                    } else {
                        newOverallUsage = 0f
                    }
                } else if (parts[0].startsWith("cpu")) {
                    // Per-core CPU stat
                    val coreId = parts[0].substring(3).toIntOrNull() ?: return@forEach
                    val stat = parseCpuStatLine(line) ?: return@forEach
                    newPerCoreStats[coreId] = stat
                    
                    val prevStat = lastPerCoreStat[coreId]
                    if (prevStat != null) {
                        val totalDelta = stat.totalTime - prevStat.totalTime
                        val idleDelta = stat.idleTime - prevStat.idleTime
                        if (totalDelta > 0) {
                            newPerCoreUsage[coreId] = (1f - (idleDelta.toFloat() / totalDelta.toFloat())).coerceIn(0f, 1f)
                        }
                    } else {
                        newPerCoreUsage[coreId] = 0f
                    }
                }
            }
            
            if (newOverallUsage < 0 && newPerCoreUsage.isEmpty()) return false
            
            if (newOverallUsage >= 0) cachedOverallUsage = newOverallUsage
            if (newPerCoreUsage.isNotEmpty()) {
                cachedPerCoreUsage = newPerCoreUsage
                lastPerCoreStat = newPerCoreStats
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating from /proc/stat", e)
            return false
        }
    }
    
    /**
     * Parse a CPU stat line from /proc/stat.
     * Format: cpu<id> user nice system idle iowait irq softirq steal guest guest_nice
     */
    private fun parseCpuStatLine(line: String): CpuStat? {
        try {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) return null
            
            // Skip the first part (cpu/cpu0/cpu1/etc)
            val user = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val nice = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val system = parts.getOrNull(3)?.toLongOrNull() ?: 0L
            val idle = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L
            val guest = parts.getOrNull(9)?.toLongOrNull() ?: 0L
            val guestNice = parts.getOrNull(10)?.toLongOrNull() ?: 0L
            
            return CpuStat(user, nice, system, idle, iowait, irq, softirq, steal, guest, guestNice)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CPU stat line: $line", e)
            return null
        }
    }
    
    /**
     * Fallback: Calculate CPU usage based on frequency.
     * This is less accurate but works without root access.
     */
    private fun getFrequencyBasedCpuUsage(): Float {
        try {
            val cpuDir = File("/sys/devices/system/cpu")
            val cpuFiles = cpuDir.listFiles { _, name -> 
                name.startsWith("cpu") && name.substring(3).all { it.isDigit() } 
            }
            
            if (cpuFiles.isNullOrEmpty()) return 0f
            
            var totalUsage = 0f
            var coreCount = 0
            
            cpuFiles.forEach { cpuFile ->
                val coreId = cpuFile.name.substring(3).toIntOrNull() ?: return@forEach
                val freqPath = "${cpuFile.absolutePath}/cpufreq/scaling_cur_freq"
                val minFreqPath = "${cpuFile.absolutePath}/cpufreq/cpuinfo_min_freq"
                val maxFreqPath = "${cpuFile.absolutePath}/cpufreq/cpuinfo_max_freq"
                
                val curFreq = ShellUtils.readFile(freqPath).toLongOrNull() ?: return@forEach
                val minFreq = ShellUtils.readFile(minFreqPath).toLongOrNull() ?: 0L
                val maxFreq = ShellUtils.readFile(maxFreqPath).toLongOrNull() ?: return@forEach
                
                if (maxFreq > minFreq && curFreq >= minFreq) {
                    val usage = ((curFreq - minFreq).toFloat() / (maxFreq - minFreq)).coerceIn(0f, 1f)
                    totalUsage += usage
                    coreCount++
                } else if (curFreq > 0 && maxFreq > 0) {
                    val usage = (curFreq.toFloat() / maxFreq).coerceIn(0f, 1f)
                    totalUsage += usage
                    coreCount++
                }
            }
            
            return if (coreCount > 0) totalUsage / coreCount else 0f
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating frequency-based CPU usage", e)
            return 0f
        }
    }
    
    /**
     * Fallback: Calculate per-core CPU usage based on frequency.
     */
    private fun getFrequencyBasedPerCoreUsage(): Map<Int, Float> {
        try {
            val cpuDir = File("/sys/devices/system/cpu")
            val cpuFiles = cpuDir.listFiles { _, name -> 
                name.startsWith("cpu") && name.substring(3).all { it.isDigit() } 
            }
            
            if (cpuFiles.isNullOrEmpty()) return emptyMap()
            
            val usageMap = mutableMapOf<Int, Float>()
            
            cpuFiles.forEach { cpuFile ->
                val coreId = cpuFile.name.substring(3).toIntOrNull() ?: return@forEach
                val freqPath = "${cpuFile.absolutePath}/cpufreq/scaling_cur_freq"
                val minFreqPath = "${cpuFile.absolutePath}/cpufreq/cpuinfo_min_freq"
                val maxFreqPath = "${cpuFile.absolutePath}/cpufreq/cpuinfo_max_freq"
                
                val curFreq = ShellUtils.readFile(freqPath).toLongOrNull() ?: return@forEach
                val minFreq = ShellUtils.readFile(minFreqPath).toLongOrNull() ?: 0L
                val maxFreq = ShellUtils.readFile(maxFreqPath).toLongOrNull() ?: return@forEach
                
                val usage = if (maxFreq > minFreq && curFreq >= minFreq) {
                    ((curFreq - minFreq).toFloat() / (maxFreq - minFreq)).coerceIn(0f, 1f)
                } else if (curFreq > 0 && maxFreq > 0) {
                    (curFreq.toFloat() / maxFreq).coerceIn(0f, 1f)
                } else {
                    0f
                }
                
                usageMap[coreId] = usage
            }
            
            return usageMap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating frequency-based per-core CPU usage", e)
            return emptyMap()
        }
    }
    
    /**
     * Reset cached measurements. Call this when you want to force a fresh calculation.
     */
    fun reset() {
        lastOverallStat = null
        lastPerCoreStat = emptyMap()
        lastCalculationTime = 0
        cachedOverallUsage = 0f
        cachedPerCoreUsage = emptyMap()
        Log.d(TAG, "CPU utilization cache reset")
    }
}
