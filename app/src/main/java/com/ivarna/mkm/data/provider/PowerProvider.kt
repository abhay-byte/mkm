package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.CpuEfficiencyResult
import com.ivarna.mkm.data.model.GpuEfficiencyResult
import com.ivarna.mkm.data.model.PowerStatus
import com.ivarna.mkm.shell.PowerScripts
import com.ivarna.mkm.shell.ShellManager
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

class PowerProvider {

    suspend fun getPowerStatus(multiplier: Float = 1.0f): PowerStatus = withContext(Dispatchers.IO) {
        val output = ShellManager.exec(PowerScripts.getPowerAndVoltage()).stdout
        if (output.isBlank()) return@withContext PowerStatus(multiplier = multiplier)

        try {
            val parts = output.trim().split(" ")
            if (parts.size >= 2) {
                val currentRaw = parts[0].toLongOrNull() ?: 0L
                val voltageRaw = parts[1].toLongOrNull() ?: 0L
                
                // Current is often negative (discharging), take absolute
                val currentUa = abs(currentRaw)
                val voltageUv = voltageRaw
                
                val powerUw = (currentUa * voltageUv) / 1_000_000L
                val powerW = powerUw / 1_000_000f
                val calibratedPowerW = powerW * multiplier
                
                return@withContext PowerStatus(
                    voltageUv = voltageUv, 
                    currentUa = currentUa, 
                    powerUw = powerUw, 
                    powerW = powerW,
                    calibratedPowerW = calibratedPowerW,
                    multiplier = multiplier
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext PowerStatus(multiplier = multiplier)
    }

    data class BenchmarkResult<T>(val data: List<T>, val logs: String)

    suspend fun runCpuBenchmarkKotlin(onProgress: (String) -> Unit, multiplier: Float = 1.0f): BenchmarkResult<CpuEfficiencyResult> = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val results = mutableListOf<CpuEfficiencyResult>()
        
        sb.appendLine("Starting Pure Kotlin CPU Benchmark...")
        onProgress("Initializing Benchmark...")

        // 1. Discovery
        val cpuStatus = CpuProvider.getCpuStatus()
        val clusters = cpuStatus.clusters
        val totalCores = cpuStatus.totalCores
        
        sb.appendLine("Detected ${clusters.size} clusters, $totalCores cores.")
        onProgress("Found $totalCores cores.")

        // Store original settings to restore later
        // Map: PolicyID -> Triple(Gov, Min, Max)
        val originalSettings = mutableMapOf<Int, Triple<String, String, String>>()
        
        try {
            // 2. Setup: Backup and Set Performance
            onProgress("Preparing Clusters...")
            clusters.forEach { cluster ->
                originalSettings[cluster.id] = Triple(cluster.governor, cluster.rawMinFreq, cluster.rawMaxFreq)
                
                sb.appendLine("Cluster ${cluster.id}: cores ${cluster.coreRange}, current gov: ${cluster.governor}")
                
                // Set to performance to allow manual freq control
                val govSet = CpuProvider.setGovernor(cluster.id, "performance")
                if (!govSet) {
                    sb.appendLine("WARNING: Failed to set governor for cluster ${cluster.id}")
                }
            }

            // 3. Build frequency arrays for each cluster (highest to lowest)
            // Use map with cluster.id as key to avoid index mismatch
            val clusterFreqArrays = clusters.associate { cluster ->
                cluster.id to cluster.availableFrequencies
                    .mapNotNull { it.toLongOrNull() }
                    .sortedDescending()
            }
            
            // Find maximum number of steps (skipping every other step)
            val maxSteps = (clusterFreqArrays.values.maxOfOrNull { it.size } ?: 0)
            val actualSteps = (maxSteps + 1) / 2  // Skip every other step
            
            if (maxSteps == 0) {
                sb.appendLine("No available frequencies found!")
                return@withContext BenchmarkResult(
                    data = emptyList(),
                    logs = sb.toString()
                )
            }
            
            sb.appendLine("Testing $actualSteps frequency steps (skipping every other)")
            clusters.forEach { cluster ->
                val freqs = clusterFreqArrays[cluster.id] ?: emptyList()
                if (freqs.isNotEmpty()) {
                    sb.appendLine("Cluster ${cluster.id}: ${freqs.first()/1000}MHz → ${freqs.last()/1000}MHz (${freqs.size} available, testing ${(freqs.size + 1) / 2})")
                }
            }
            
            // 4. Iterate through steps, skipping every other frequency
            for (step in 0 until maxSteps step 2) {  // Step by 2 to skip every other
                val stepFreqs = mutableMapOf<Int, Long>()
                
                // Set frequency for each cluster
                clusters.forEach { cluster ->
                    val freqArray = clusterFreqArrays[cluster.id] ?: emptyList()
                    // If cluster has frequency at this step, use it; otherwise use last (minimum)
                    val targetFreq = if (step < freqArray.size) {
                        freqArray[step]
                    } else {
                        freqArray.lastOrNull() ?: 0L
                    }
                    
                    if (targetFreq > 0) {
                        stepFreqs[cluster.id] = targetFreq
                        sb.appendLine("Setting Cluster ${cluster.id} to ${targetFreq/1000}MHz")
                        
                        // Set frequency for each core in the cluster (this works, policy-based doesn't)
                        var allSuccess = true
                        var failedCores = mutableListOf<Int>()
                        cluster.cores.forEach { core ->
                            // Log the exact command being executed
                            val maxCmd = "printf '%s' '$targetFreq' > ${CpuProvider.findPolicyForCore(core.id) ?: "/sys/devices/system/cpu/cpu${core.id}/cpufreq"}/scaling_max_freq"
                            val minCmd = "printf '%s' '$targetFreq' > ${CpuProvider.findPolicyForCore(core.id) ?: "/sys/devices/system/cpu/cpu${core.id}/cpufreq"}/scaling_min_freq"
                            
                            sb.appendLine("Core ${core.id} MAX cmd: $maxCmd")
                            val maxResult = CpuProvider.setFrequencyForCore(core.id, targetFreq.toString(), isMax = true)
                            sb.appendLine("Core ${core.id} MAX result: $maxResult")
                            
                            sb.appendLine("Core ${core.id} MIN cmd: $minCmd")
                            val minResult = CpuProvider.setFrequencyForCore(core.id, targetFreq.toString(), isMax = false)
                            sb.appendLine("Core ${core.id} MIN result: $minResult")
                            
                            if (!maxResult || !minResult) {
                                allSuccess = false
                                failedCores.add(core.id)
                            }
                        }
                        
                        if (!allSuccess) {
                            sb.appendLine("WARNING: Failed to set frequency for cores: ${failedCores.joinToString(",")}")
                        }
                    }
                }
                
                val freqLabel = stepFreqs.values.joinToString("|") { "${it/1000}" }
                val currentStep = (step / 2) + 1
                onProgress("Benchmarking ${freqLabel}MHz ($currentStep/$actualSteps)...")
                sb.appendLine("--- Step $currentStep: $freqLabel MHz ---")
                
                // Stabilize to let frequency settle - INCREASED delay for proper application
                delay(1000)
                
                // Read REAL Per-Core Frequencies
                val stepCoreFreqs = mutableMapOf<Int, Long>()
                val stepClusterFreqs = mutableMapOf<Int, Long>()
                var avgFreqSum = 0L
                
                clusters.forEach { cluster ->
                    val coreFreqs = mutableListOf<Long>()
                    cluster.cores.forEach { core ->
                        val path = "/sys/devices/system/cpu/cpu${core.id}/cpufreq/scaling_cur_freq"
                        val realFreqStr = ShellManager.exec("cat $path").stdout.trim()
                        val realFreq = realFreqStr.toLongOrNull() ?: 0L
                        
                        if (realFreq > 0) {
                            stepCoreFreqs[core.id] = realFreq
                            coreFreqs.add(realFreq)
                        }
                    }
                    
                    // Calculate cluster average from actual core readings
                    if (coreFreqs.isNotEmpty()) {
                        val clusterAvg = coreFreqs.average().toLong()
                        stepClusterFreqs[cluster.id] = clusterAvg
                        avgFreqSum += clusterAvg
                    }
                }
                
                val avgFreqKHz = if (clusters.isNotEmpty()) avgFreqSum / clusters.size else 0L
                
                // Run Load - MAXIMUM INTENSITY Multi-threaded Workload
                // Designed to push CPU to thermal/power limits (7W target)
                // Uses matrix multiplication + prime generation + hash computation + FPU stress
                val startTime = System.nanoTime()
                
                // Power sampling during workload (multiple samples for accuracy)
                val powerSamples = mutableListOf<Float>()
                var keepSampling = true
                
                // Launch power monitoring coroutine
                val powerMonitorJob = launch(Dispatchers.IO) {
                    while (keepSampling) {
                        val sample = getPowerStatus(multiplier)
                        powerSamples.add(sample.calibratedPowerW)
                        delay(200) // Sample every 200ms
                    }
                }
                
                // Spawn threads equal to core count, each with HEAVY workload
                coroutineScope {
                    repeat(totalCores) { threadId ->
                        launch(Dispatchers.Default) {
                            var checksum = 0L // Prevent optimization
                            
                            // PHASE 1: Large Matrix Multiplication (10x more intensive)
                            val matrixSize = 256  // Increased from 128
                            val matrixA = Array(matrixSize) { DoubleArray(matrixSize) { (it + threadId).toDouble() } }
                            val matrixB = Array(matrixSize) { DoubleArray(matrixSize) { (it * 2 + threadId).toDouble() } }
                            val result = Array(matrixSize) { DoubleArray(matrixSize) }
                            
                            // 10 iterations instead of 3
                            repeat(10) { iteration ->
                                for (i in 0 until matrixSize) {
                                    for (j in 0 until matrixSize) {
                                        var sum = 0.0
                                        for (k in 0 until matrixSize) {
                                            sum += matrixA[i][k] * matrixB[k][j]
                                        }
                                        result[i][j] = sum + iteration
                                    }
                                }
                                checksum += result[0][0].toLong()
                            }
                            
                            // PHASE 2: Larger Prime Generation (5x more intensive)
                            val primeLimit = 500_000  // Increased from 100K
                            val isPrime = BooleanArray(primeLimit + 1) { true }
                            isPrime[0] = false
                            isPrime[1] = false
                            
                            for (p in 2..sqrt(primeLimit.toDouble()).toInt()) {
                                if (isPrime[p]) {
                                    var multiple = p * p
                                    while (multiple <= primeLimit) {
                                        isPrime[multiple] = false
                                        multiple += p
                                    }
                                }
                            }
                            
                            var primeCount = 0
                            for (i in 2..primeLimit) {
                                if (isPrime[i]) primeCount++
                            }
                            checksum += primeCount
                            
                            // PHASE 3: Intensive Hash + Trigonometric Operations (4x more)
                            var hash = threadId.toLong()
                            val goldenRatio = 0x9e3779b9L
                            repeat(200_000) { iteration ->  // Increased from 50K
                                // SHA-like mixing
                                hash = hash xor (hash shl 13)
                                hash = hash xor (hash ushr 17)
                                hash = hash xor (hash shl 5)
                                hash += (iteration * goldenRatio)
                                
                                // Heavy FPU operations (CPU intensive)
                                val temp = (hash and 0xFFFF).toDouble()
                                val fpuResult = sin(temp / 1000.0) * cos(temp / 2000.0) * sqrt(temp)
                                hash += (fpuResult * 1000000).toLong()
                            }
                            checksum += hash
                            
                            // PHASE 4: Aggressive Memory Thrashing (5x more)
                            val arraySize = 65_536  // Increased to 512KB per thread
                            val data = LongArray(arraySize) { (it + threadId).toLong() }
                            val temp = LongArray(arraySize)
                            
                            repeat(50_000) { iteration ->  // Increased from 10K
                                val idx = (iteration * 1597 + 51749) % arraySize
                                // Heavy memory operations
                                data[idx] = data[idx] xor (iteration.toLong() + checksum)
                                temp[idx] = data[(idx + 1) % arraySize] + data[(idx + arraySize - 1) % arraySize]
                                checksum += temp[idx] and 0xFF
                            }
                            
                            // PHASE 5: Sustained FPU Load (NEW - push thermal limits)
                            var fpuSum = threadId.toDouble()
                            repeat(100_000) { iteration ->
                                fpuSum = sqrt(abs(fpuSum) + iteration) * sin(fpuSum / 1000.0) * cos(fpuSum / 2000.0)
                                fpuSum += sqrt(iteration.toDouble()) * sin(iteration / 100.0)
                                if (iteration % 1000 == 0) checksum += fpuSum.toLong()
                            }
                            
                            // Prevent dead code elimination
                            if (checksum == -999999999L || fpuSum < -999999999.0) {
                                print("$checksum $fpuSum")
                            }
                        }
                    }
                }
                
                val endTime = System.nanoTime()
                val durationSec = (endTime - startTime) / 1_000_000_000f
                
                // Stop power monitoring
                keepSampling = false
                powerMonitorJob.join()
                
                sb.appendLine("Duration: $durationSec s")
                sb.appendLine("Cluster Freqs: ${stepClusterFreqs.values.joinToString("|") { "${it/1000}" }}")
                
                // Calculate average power from all samples
                val avgPowerW = if (powerSamples.isNotEmpty()) {
                    powerSamples.average().toFloat()
                } else {
                    // Fallback to single measurement if no samples
                    getPowerStatus(multiplier).calibratedPowerW
                }
                
                sb.appendLine("Power samples: ${powerSamples.size}, Avg: $avgPowerW W")
                
                // Score Calculation - based on MAXIMUM INTENSITY workload
                // Each thread performs:
                // - 10 matrix multiplications (256³ operations each) = ~168M ops
                // - 1 prime sieve up to 500K = ~500K ops
                // - 200K hash iterations with FPU mixing = ~800K ops
                // - 50K memory access operations = ~50K ops
                // - 100K FPU stress operations = ~300K ops
                // Total per thread: ~169.7M operations
                val opsPerThread = 169_650_000L
                val totalOps = totalCores * opsPerThread
                val score = (totalOps / durationSec) / 1_000_000f  // Ops per second in millions
                
                val efficiency = if (avgPowerW > 0) score / avgPowerW else 0f
                
                sb.appendLine("Score: $score, Power: $avgPowerW W, Eff: $efficiency")
                
                results.add(CpuEfficiencyResult(
                    policy = freqLabel,
                    frequencyKHz = avgFreqKHz,
                    durationSec = durationSec,
                    score = score,
                    powerW = avgPowerW,
                    efficiency = efficiency,
                    clusterFrequencies = stepClusterFreqs
                ))
            }
            
        } catch (e: Exception) {
            sb.appendLine("Error: ${e.message}")
            e.printStackTrace()
            onProgress("Error: ${e.message}")
        } finally {
            // 4. Restore
            onProgress("Restoring Settings...")
            originalSettings.forEach { (policyId, settings) ->
                val (gov, min, max) = settings
                // Restore range first then governor?
                CpuProvider.setFrequency(policyId, min, isMax = false)
                CpuProvider.setFrequency(policyId, max, isMax = true)
                CpuProvider.setGovernor(policyId, gov)
            }
        }
        
        BenchmarkResult(results, sb.toString())
    }
    
    suspend fun runGpuBenchmark(onProgress: (String) -> Unit, multiplier: Float = 1.0f): BenchmarkResult<GpuEfficiencyResult> = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val scriptPath = "/data/local/tmp/mkm_gpu_bench.sh"
        val outputPath = "gpu_efficiency_results.csv"
        
        val scriptContent = PowerScripts.getGpuScriptContent()
        val scriptBase64 = android.util.Base64.encodeToString(scriptContent.toByteArray(), android.util.Base64.NO_WRAP)
        
        onProgress("Deploying GPU Benchmark Script to $scriptPath...")
        sb.appendLine("Deploying GPU Benchmark Script...")
        ShellManager.exec("echo \"$scriptBase64\" | base64 -d > $scriptPath")
        ShellManager.exec("chmod +x $scriptPath")
        
        onProgress("Starting Benchmark...")
        sb.appendLine("Running GPU Benchmark...")
        
        val execResult = ShellManager.execStreaming("cd /data/local/tmp && ${PowerScripts.executeGpuBenchmark(scriptPath)}") { line ->
            onProgress(line)
            sb.appendLine(line)
        }
        
        sb.appendLine("Exit Code: ${execResult.exitCode}")
        
        onProgress("Reading Results...")
        val fileContent = ShellManager.exec("cat /data/local/tmp/$outputPath").stdout
        
        val results = parseGpuCsv(fileContent, multiplier)
        BenchmarkResult(results, sb.toString())
    }
    
    private fun parseGpuCsv(content: String, multiplier: Float): List<GpuEfficiencyResult> {
        val lines = content.lines().drop(1)
        val results = mutableListOf<GpuEfficiencyResult>()
        
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split(",")
             if (parts.size >= 5) {
                try {
                    // Frequency_Hz,Duration_Sec,Score,Power_W,Efficiency_ScorePerWatt
                    val freq = parts[0].toLongOrNull() ?: 0L
                    val util = parts[2].toFloatOrNull() ?: 0f 
                    val rawPower = parts[3].toFloatOrNull() ?: 0f
                    val power = rawPower * multiplier
                    
                    val efficiency = if (power > 0) util / power else 0f
                    results.add(GpuEfficiencyResult(freq, util, power, efficiency))
                } catch (e: Exception) {
                    continue
                }
             }
        }
        return results
    }
}

