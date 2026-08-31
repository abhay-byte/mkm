package com.ivarna.mkm.data.provider

import android.util.Log
import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuCore
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.data.model.CpuPolicyState
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.FrequencyCapabilityParser
import com.ivarna.mkm.data.model.FrequencyRangePlanner
import com.ivarna.mkm.data.model.RangeReadback
import com.ivarna.mkm.data.model.RangeTransactionResult
import com.ivarna.mkm.data.model.RangeWriteTransaction
import com.ivarna.mkm.data.model.ScalarReadbackVerifier
import com.ivarna.mkm.utils.ShellUtils
import com.ivarna.mkm.shell.SysfsTuningExecutor
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
            val affectedCores = CpuPolicyMapping.parseCpuList(readSystemFile("${policy.absolutePath}/affected_cpus"))
            val relatedCores = CpuPolicyMapping.parseCpuList(readSystemFile("${policy.absolutePath}/related_cpus"))
            val policyCores = (if (affectedCores.isNotEmpty()) affectedCores else relatedCores)
                .ifEmpty { listOf(id) }
            
            val coreRange = if (policyCores.isNotEmpty()) {
                policyCores.min()..policyCores.max()
            } else {
                0..0
            }

            val governor = readSystemFile("${policy.absolutePath}/scaling_governor")
            val curFreq = readSystemFile("${policy.absolutePath}/scaling_cur_freq").toLongOrNull() ?: 0L
            val minFreq = readSystemFile("${policy.absolutePath}/scaling_min_freq").toLongOrNull() ?: 0L
            val maxFreq = readSystemFile("${policy.absolutePath}/scaling_max_freq").toLongOrNull() ?: 0L
            
            // Get hardware min/max for accurate usage calculation
            val hwMinFreq = readSystemFile("${policy.absolutePath}/cpuinfo_min_freq").toLongOrNull()
                ?.takeIf { it > 0L }
            val hwMaxFreq = readSystemFile("${policy.absolutePath}/cpuinfo_max_freq").toLongOrNull()
                ?.takeIf { it > 0L }
            // The active scaling bounds are useful telemetry, but are not
            // hardware limits. Only expose a Range capability when the kernel
            // actually reports both cpuinfo endpoints.
            val telemetryMinFreq = hwMinFreq ?: minFreq
            val telemetryMaxFreq = hwMaxFreq ?: maxFreq
            
            val rawMinFreq = readSystemFile("${policy.absolutePath}/scaling_min_freq")
            val rawMaxFreq = readSystemFile("${policy.absolutePath}/scaling_max_freq")
            
            val availableGovernors = readSystemFile("${policy.absolutePath}/scaling_available_governors")
                .split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
            
            val availableFrequenciesRaw = readSystemFile("${policy.absolutePath}/scaling_available_frequencies")
                .split(Regex("\\s+")).filter { it.isNotBlank() }
            val timeInState = readSystemFile("${policy.absolutePath}/stats/time_in_state")
                .lines().mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
            val frequencyCapability = FrequencyCapabilityParser.fromDiscreteSources(
                sources = listOf(availableFrequenciesRaw, timeInState),
                rangeMin = hwMinFreq,
                rangeMax = hwMaxFreq,
                knownPoints = listOf(curFreq, minFreq, maxFreq)
            )
            val availableFrequencies = FrequencyCapabilityParser.valuesForUi(frequencyCapability)
                .map(Long::toString)
            val governorAccess = SysfsTuningExecutor.access("${policy.absolutePath}/scaling_governor")
            val minAccess = SysfsTuningExecutor.access("${policy.absolutePath}/scaling_min_freq")
            val maxAccess = SysfsTuningExecutor.access("${policy.absolutePath}/scaling_max_freq")
            val rangeWritable = minAccess == SysfsTuningExecutor.SysfsAccess.READ_WRITE &&
                maxAccess == SysfsTuningExecutor.SysfsAccess.READ_WRITE
            val rangeReason = SysfsTuningExecutor.accessReason(maxAccess)
                ?: SysfsTuningExecutor.accessReason(minAccess)
                ?: (frequencyCapability as? FrequencyCapability.Unavailable)?.reason

            val cores = policyCores.map { coreId ->
                val coreCurFreqFile = File("/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_cur_freq")
                val coreCurFreq = if (coreCurFreqFile.exists()) {
                    readSystemFile(coreCurFreqFile.absolutePath).toLongOrNull() ?: curFreq
                } else curFreq
                
                // Get CPU usage from CpuUtilizationProvider (primary method)
                // If not available, fallback to frequency-based calculation (already handled by the provider)
                val usage = perCoreUsage[coreId] ?: run {
                    // Additional fallback if core not in map: calculate from frequency
                    if (telemetryMaxFreq > telemetryMinFreq && coreCurFreq >= telemetryMinFreq) {
                        ((coreCurFreq - telemetryMinFreq).toFloat() / (telemetryMaxFreq - telemetryMinFreq)).coerceIn(0f, 1f)
                    } else if (coreCurFreq > 0 && telemetryMaxFreq > 0) {
                        (coreCurFreq.toFloat() / telemetryMaxFreq).coerceIn(0f, 1f)
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
                    // Core cards are telemetry-only; policy controls are rendered once below.
                    availableGovernors = emptyList(),
                    availableFrequencies = emptyList(),
                    policyId = id
                )
            }

            clusters.add(CpuCluster(
                id = id,
                policyPath = policy.absolutePath,
                // Keep the kernel-reported membership exact. policyCores is
                // only the telemetry fallback when both membership files are
                // unavailable; it must not be presented as affected_cpus.
                affectedCpus = affectedCores,
                relatedCpus = relatedCores,
                coreRange = coreRange,
                governor = governor,
                currentFreq = ShellUtils.formatFreq(curFreq),
                minFreq = ShellUtils.formatFreq(minFreq),
                maxFreq = ShellUtils.formatFreq(maxFreq),
                rawMinFreq = rawMinFreq,
                rawMaxFreq = rawMaxFreq,
                hwMinFreq = hwMinFreq?.toString() ?: "",
                hwMaxFreq = hwMaxFreq?.toString() ?: "",
                availableGovernors = availableGovernors,
                availableFrequencies = availableFrequencies,
                frequencyCapability = frequencyCapability,
                governorWritable = governorAccess == SysfsTuningExecutor.SysfsAccess.READ_WRITE,
                minWritable = rangeWritable,
                maxWritable = rangeWritable,
                governorReason = SysfsTuningExecutor.accessReason(governorAccess)
                    ?: if (availableGovernors.isEmpty()) "No supported CPU governors exposed by this policy" else null,
                minReason = rangeReason,
                maxReason = rangeReason,
                policyState = CpuPolicyState(
                    policyId = id,
                    path = policy.absolutePath,
                    affectedCpus = affectedCores,
                    relatedCpus = relatedCores,
                    governor = governor,
                    supportedGovernors = availableGovernors,
                    minFreq = minFreq,
                    maxFreq = maxFreq,
                    hwMinFreq = hwMinFreq,
                    hwMaxFreq = hwMaxFreq,
                    frequencyCapability = frequencyCapability
                ),
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
        return SysfsTuningExecutor.read(path)
    }

    private fun getCpuName(): String {
        var name = "Unknown"
        
        // 1. Try Build.SOC_MODEL (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val socModel = android.os.Build.SOC_MODEL
            if (socModel != "unknown" && socModel.isNotEmpty()) {
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

    fun applyGovernor(policyId: Int, governor: String): ApplyResult {
        if (!SysfsTuningExecutor.isSafeValue(governor)) return ApplyResult.Failed("Invalid governor")
        val path = policyPath(policyId) ?: return ApplyResult.Failed("CPU policy $policyId is unavailable")
        val governors = readSystemFile("$path/scaling_available_governors").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (governors.isEmpty() || governor !in governors) {
            return ApplyResult.Failed("Governor '$governor' is not advertised by policy$policyId")
        }
        val before = readSystemFile("$path/scaling_governor")
        val result = SysfsTuningExecutor.write("$path/scaling_governor", governor)
        if (!result.isSuccess) return failed("CPU", "policy$policyId", "governor", result)
        val after = readSystemFile("$path/scaling_governor")
        val outcome = ScalarReadbackVerifier.verify(
            requested = governor,
            actual = after,
            adjustedReason = "Kernel selected a different governor."
        )
        android.util.Log.i("CpuProvider", "domain=CPU policy=policy$policyId request=governor:$governor before=$before backend=${result.backend} after=$after result=${outcome::class.simpleName}")
        return outcome
    }

    fun applyRange(policyId: Int, desiredMin: Long? = null, desiredMax: Long? = null): ApplyResult {
        if (desiredMin == null && desiredMax == null) return ApplyResult.Failed("No frequency requested")
        val path = policyPath(policyId) ?: return ApplyResult.Failed("CPU policy $policyId is unavailable")
        val currentMin = readSystemFile("$path/scaling_min_freq").toLongOrNull()
        val currentMax = readSystemFile("$path/scaling_max_freq").toLongOrNull()
        if (currentMin == null || currentMax == null || currentMin <= 0L || currentMax <= 0L) {
            return ApplyResult.Failed("CPU policy bounds are unavailable")
        }
        if (currentMin > currentMax) {
            return ApplyResult.Failed("CPU policy reports an invalid frequency range")
        }
        val capability = policyCapability(path, currentMin, currentMax)
        val requested = listOfNotNull(desiredMin, desiredMax)
        if (!requested.all { isSupportedFrequency(it, capability) }) {
            return ApplyResult.Failed("Requested CPU frequency is not advertised by policy$policyId")
        }
        val plan = when {
            desiredMin != null && desiredMax != null -> FrequencyRangePlanner.plan(currentMin, currentMax, desiredMin, desiredMax)
            desiredMax != null -> FrequencyRangePlanner.forMax(currentMin, currentMax, desiredMax)
            else -> FrequencyRangePlanner.forMin(currentMin, currentMax, desiredMin!!)
        }
        val before = "min=$currentMin,max=$currentMax"
        val writeLog = mutableListOf<String>()
        var lastWriteResult: com.ivarna.mkm.shell.ShellManager.CommandResult? = null
        val transaction = RangeWriteTransaction.execute(
            original = RangeReadback(currentMin, currentMax),
            plan = plan,
            write = { step ->
                val file = if (step.isMin) "scaling_min_freq" else "scaling_max_freq"
                lastWriteResult = SysfsTuningExecutor.write("$path/$file", step.value.toString())
                if (lastWriteResult!!.isSuccess) writeLog += "$file=OK(${lastWriteResult!!.backend})"
                lastWriteResult!!.isSuccess
            },
            readImmediate = { readRange(path) },
            readFinal = {
                Thread.sleep(150L)
                readRange(path)
            }
        )
        when (transaction) {
            is RangeTransactionResult.FailedRolledBack -> {
                return ApplyResult.Failed(
                    "CPU policy$policyId frequency change failed; original values restored " +
                        "(min=${transaction.restored.min},max=${transaction.restored.max})",
                    lastWriteResult?.stderr
                )
            }
            is RangeTransactionResult.FailedStateChanged -> {
                return ApplyResult.Failed(
                    "CPU policy$policyId frequency change failed and kernel state changed " +
                        "(current min=${transaction.actual.min},max=${transaction.actual.max}; " +
                        "original min=${transaction.original.min},max=${transaction.original.max})",
                    lastWriteResult?.stderr
                )
            }
            is RangeTransactionResult.Failed -> {
                return ApplyResult.Failed(
                    "CPU policy$policyId frequency transaction could not be verified: ${transaction.reason}",
                    lastWriteResult?.stderr ?: transaction.actual?.let { "readback=min=${it.min},max=${it.max}" }
                )
            }
            is RangeTransactionResult.Verified -> Unit
        }
        val verified = transaction as RangeTransactionResult.Verified
        val afterFirst = verified.immediate
        val after = verified.final
        val requestedText = "min=${plan.min},max=${plan.max}"
        val outcome = when {
            after.min != plan.min || after.max != plan.max -> ApplyResult.Adjusted(
                requestedText, "min=${after.min},max=${after.max}", "Kernel clamped or overrode the requested range."
            )
            plan.adjusted -> ApplyResult.Adjusted(requestedText, requestedText, plan.adjustmentReason ?: "Range adjusted")
            else -> ApplyResult.Applied(requestedText, requestedText)
        }
        android.util.Log.i("CpuProvider", "domain=CPU policy=policy$policyId request=$requestedText before=$before plan=${plan.steps.joinToString { if (it.isMin) "min=${it.value}" else "max=${it.value}" }} writes=${writeLog.joinToString()} afterFirst=$afterFirst after=$after result=${outcome::class.simpleName}")
        return outcome
    }

    fun setGovernor(policyId: Int, governor: String): Boolean = applyGovernor(policyId, governor) !is ApplyResult.Failed

    fun setFrequency(policyId: Int, freqKhz: String, isMax: Boolean): Boolean =
        (if (isMax) applyRange(policyId, desiredMax = freqKhz.toLongOrNull())
         else applyRange(policyId, desiredMin = freqKhz.toLongOrNull())) !is ApplyResult.Failed

    fun setGovernorForCore(coreId: Int, governor: String): Boolean =
        findPolicyIdForCore(coreId)?.let { setGovernor(it, governor) } ?: false

    fun setFrequencyForCore(coreId: Int, freqKhz: String, isMax: Boolean): Boolean =
        findPolicyIdForCore(coreId)?.let { setFrequency(it, freqKhz, isMax) } ?: false

    fun findPolicyForCore(coreId: Int): String? {
        val policyDir = File("/sys/devices/system/cpu/cpufreq")
        policyDir.listFiles { _, name -> name.startsWith("policy") }?.forEach { policy ->
            val affectedCores = CpuPolicyMapping.parseCpuList(readSystemFile("${policy.absolutePath}/affected_cpus"))
            val relatedCores = CpuPolicyMapping.parseCpuList(readSystemFile("${policy.absolutePath}/related_cpus"))
            if (affectedCores.contains(coreId) || relatedCores.contains(coreId)) return policy.absolutePath
        }
        return null
    }

    private fun findPolicyIdForCore(coreId: Int): Int? = findPolicyForCore(coreId)
        ?.substringAfterLast("policy")?.toIntOrNull()

    private fun policyPath(policyId: Int): String? {
        val path = "/sys/devices/system/cpu/cpufreq/policy$policyId"
        return if (SysfsTuningExecutor.read("$path/scaling_min_freq").isNotBlank() || SysfsTuningExecutor.exists(path)) path else null
    }

    private fun policyCapability(path: String, currentMin: Long, currentMax: Long): FrequencyCapability {
        val hardwareMin = readSystemFile("$path/cpuinfo_min_freq").toLongOrNull()?.takeIf { it > 0L }
        val hardwareMax = readSystemFile("$path/cpuinfo_max_freq").toLongOrNull()?.takeIf { it > 0L }
        return FrequencyCapabilityParser.fromDiscreteSources(
            sources = listOf(
                readSystemFile("$path/scaling_available_frequencies").split(Regex("\\s+")),
                readSystemFile("$path/stats/time_in_state").lines().mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
            ),
            rangeMin = hardwareMin,
            rangeMax = hardwareMax,
            knownPoints = listOf(currentMin, currentMax)
        )
    }

    private fun isSupportedFrequency(value: Long, capability: FrequencyCapability): Boolean = when (capability) {
        is FrequencyCapability.Discrete -> value in capability.values
        is FrequencyCapability.Range -> value in capability.min..capability.max
        is FrequencyCapability.Unavailable -> false
    }

    private fun readRange(path: String): RangeReadback = RangeReadback(
        min = readSystemFile("$path/scaling_min_freq").toLongOrNull() ?: 0L,
        max = readSystemFile("$path/scaling_max_freq").toLongOrNull() ?: 0L
    )

    private fun failed(domain: String, target: String, path: String, result: com.ivarna.mkm.shell.ShellManager.CommandResult): ApplyResult {
        android.util.Log.e("CpuProvider", "domain=$domain target=$target path=$path backend=${result.backend} exit=${result.exitCode} stderr=${result.stderr}")
        return ApplyResult.Failed("Write failed for $target/$path", result.stderr)
    }
}
