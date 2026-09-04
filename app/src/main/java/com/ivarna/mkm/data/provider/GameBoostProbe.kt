package com.ivarna.mkm.data.provider

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostComponentCapability
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.model.UfsStatus
import com.ivarna.mkm.shell.SysfsTuningExecutor

/** Shared boost-governor selection across Snapdragon / MediaTek / generic kernels. */
object GameBoostGovernorPolicy {
    const val BOOST_GOVERNOR = "performance"

    /** Common boost governor across all CPU policies, or null when any policy lacks it. */
    fun cpuBoostGovernor(policies: List<List<String>>): String? =
        if (policies.isNotEmpty() && policies.all { BOOST_GOVERNOR in it }) BOOST_GOVERNOR else null

    fun gpuBoostGovernor(available: List<String>): String? =
        BOOST_GOVERNOR.takeIf { it in available }

    fun storageBoostGovernor(available: List<String>): String? =
        BOOST_GOVERNOR.takeIf { it in available }
}

/** Read-only capability assessment. It never treats privilege alone as writability. */
class GameBoostProbe(private val context: Context) {
    fun probe(): GameBoostCapabilities {
        val cpu = CpuProvider.getCpuStatus()
        val gpu = GpuProvider.getGpuStatus()
        val storage = UfsProvider.getUfsStatus()
        // Reuse the same elevated access facade the tuning writes use, so
        // Snapdragon / MediaTek / generic UFS nodes share one writability signal.
        val storageGovWritable = storage.controllerPath.isNotBlank() &&
            SysfsTuningExecutor.access("${storage.controllerPath}/governor") == SysfsTuningExecutor.SysfsAccess.READ_WRITE
        val storageMinWritable = storage.controllerPath.isNotBlank() &&
            SysfsTuningExecutor.access("${storage.controllerPath}/min_freq") == SysfsTuningExecutor.SysfsAccess.READ_WRITE
        val storageMaxWritable = storage.controllerPath.isNotBlank() &&
            SysfsTuningExecutor.access("${storage.controllerPath}/max_freq") == SysfsTuningExecutor.SysfsAccess.READ_WRITE
        return buildCapabilities(
            cpu = cpu,
            gpu = gpu,
            maxLocksThermallySupported = Build.VERSION.SDK_INT >= 29 &&
                context.getSystemService(PowerManager::class.java) != null,
            storage = storage,
            storageGovernorWritable = storageGovWritable,
            storageMinWritable = storageMinWritable,
            storageMaxWritable = storageMaxWritable
        )
    }

    companion object {
        fun buildCapabilities(
            cpu: CpuStatus,
            gpu: GpuStatus,
            maxLocksThermallySupported: Boolean,
            storage: UfsStatus = UfsStatus(),
            storageGovernorWritable: Boolean = false,
            storageMinWritable: Boolean = false,
            storageMaxWritable: Boolean = false
        ): GameBoostCapabilities {
            val cpuPolicies = cpu.clusters
            val boostGovernor = GameBoostGovernorPolicy.cpuBoostGovernor(cpuPolicies.map { it.availableGovernors })
            val cpuGovernorOk = cpuPolicies.isNotEmpty() && boostGovernor != null && cpuPolicies.all {
                it.governorWritable && it.policyState != null
            }
            val cpuGovernorReason = when {
                cpuPolicies.isEmpty() -> "No cpufreq policies were discovered"
                !cpuPolicies.all { it.governorWritable } -> "At least one CPU governor node is not writable"
                boostGovernor == null -> "performance is not advertised by every CPU policy"
                else -> null
            }

            val cpuMaxOk = maxLocksThermallySupported && cpuPolicies.isNotEmpty() && cpuPolicies.all {
                val state = it.policyState
                it.minWritable && it.maxWritable &&
                    state != null && state.minFreq > 0L && state.maxFreq >= state.minFreq &&
                    cpuTarget(state) != null
            }
            val cpuMaxReason = when {
                cpuMaxOk -> null
                !maxLocksThermallySupported -> "Maximum-clock locks require the API 29+ thermal guard"
                cpuPolicies.isEmpty() -> "No cpufreq policies were discovered"
                !cpuPolicies.all { it.minWritable && it.maxWritable } -> "At least one CPU range is not writable"
                else -> "At least one CPU policy has no verified maximum frequency"
            }

            val gpuCaps = gpu.tuningCapabilities
            val gpuBoostGovernor = GameBoostGovernorPolicy.gpuBoostGovernor(gpu.availableGovernors)
            val gpuGovernorOk = gpuCaps != null && gpuCaps.path.isNotBlank() &&
                gpu.governorWritable && gpuBoostGovernor != null
            val gpuGovernorReason = when {
                gpuCaps == null || gpuCaps.path.isBlank() -> "GPU devfreq device was not detected"
                !gpu.governorWritable -> gpu.governorReason ?: "GPU governor node is not writable"
                gpuBoostGovernor == null -> "performance is not advertised by the GPU driver"
                else -> null
            }

            val gpuMaxTarget = gpuTarget(gpu)
            val gpuMaxOk = maxLocksThermallySupported && gpuCaps != null && gpuCaps.path.isNotBlank() &&
                gpu.minWritable && gpu.maxWritable && gpu.rawMinFreq.toLongOrNull()?.let { it > 0L } == true &&
                gpu.rawMaxFreq.toLongOrNull()?.let { it >= gpu.rawMinFreq.toLongOrNull()!! } == true &&
                gpuMaxTarget != null
            val gpuMaxReason = when {
                !maxLocksThermallySupported -> "Maximum-clock locks require the API 29+ thermal guard"
                gpuCaps == null || gpuCaps.path.isBlank() -> "GPU devfreq device was not detected"
                !gpu.minWritable || !gpu.maxWritable -> gpu.maxReason ?: "GPU frequency range is not writable"
                gpuMaxTarget == null -> "No verified GPU maximum frequency was exposed"
                else -> null
            }

            // Storage (UFS devfreq) reuses UfsProvider detection, so Snapdragon
            // (qcom ufshc), MediaTek (112b0000.ufshci) and generic nodes share one path.
            val storagePath = storage.controllerPath.takeIf { it.isNotBlank() }
            val storageBoostGovernor = GameBoostGovernorPolicy.storageBoostGovernor(storage.availableGovernors)
            val storageGovernorOk = storage.isSupported && storagePath != null &&
                storageGovernorWritable && storageBoostGovernor != null
            val storageGovernorReason = when {
                !storage.isSupported || storagePath == null -> "UFS storage governor was not detected"
                !storageGovernorWritable -> "Storage governor node is not writable"
                storageBoostGovernor == null -> "performance is not advertised by the storage driver"
                else -> null
            }
            val storageMaxTarget = storageTarget(storage)
            val storageMaxSource = if (storage.availableFrequencies.isNotEmpty()) "discrete-table" else "current-scaling-max"
            val storageMaxOk = maxLocksThermallySupported && storage.isSupported && storagePath != null &&
                storageMinWritable && storageMaxWritable && storageMaxTarget != null
            val storageMaxReason = when {
                !maxLocksThermallySupported -> "Maximum-clock locks require the API 29+ thermal guard"
                !storage.isSupported || storagePath == null -> "UFS storage device was not detected"
                !storageMinWritable || !storageMaxWritable -> "Storage frequency range is not writable"
                storageMaxTarget == null -> "No verified storage maximum frequency was exposed"
                else -> null
            }

            val components = mapOf(
                GameBoostComponent.CPU_GOVERNOR to GameBoostComponentCapability(GameBoostComponent.CPU_GOVERNOR, cpuGovernorOk, cpuGovernorReason, boostGovernor),
                GameBoostComponent.CPU_MAX_LOCK to GameBoostComponentCapability(
                    GameBoostComponent.CPU_MAX_LOCK, cpuMaxOk, cpuMaxReason,
                    cpuPolicies.mapNotNull { it.policyState?.let(::cpuTarget) }.joinToString(","),
                    cpuPolicies.mapNotNull { it.policyState?.let { state -> cpuTargetSource(state) } }.distinct().joinToString(",")
                ),
                GameBoostComponent.GPU_GOVERNOR to GameBoostComponentCapability(GameBoostComponent.GPU_GOVERNOR, gpuGovernorOk, gpuGovernorReason, gpuBoostGovernor),
                GameBoostComponent.GPU_MAX_LOCK to GameBoostComponentCapability(
                    GameBoostComponent.GPU_MAX_LOCK, gpuMaxOk, gpuMaxReason, gpuMaxTarget?.toString(),
                    if (gpu.frequencyTableComplete) "discrete-table" else "current-scaling-max"
                ),
                GameBoostComponent.STORAGE_GOVERNOR to GameBoostComponentCapability(
                    GameBoostComponent.STORAGE_GOVERNOR, storageGovernorOk, storageGovernorReason, storageBoostGovernor
                ),
                GameBoostComponent.STORAGE_MAX_LOCK to GameBoostComponentCapability(
                    GameBoostComponent.STORAGE_MAX_LOCK, storageMaxOk, storageMaxReason,
                    storageMaxTarget?.toString(), storageMaxSource
                )
            )
            return GameBoostCapabilities(
                components = components,
                boostPossible = components.values.any { it.supported },
                maxLocksThermallySupported = maxLocksThermallySupported
            )
        }

        fun cpuTarget(state: com.ivarna.mkm.data.model.CpuPolicyState): Long? = when (val capability = state.frequencyCapability) {
            is FrequencyCapability.Discrete -> capability.values.maxOrNull()
            is FrequencyCapability.Range -> state.hwMaxFreq?.takeIf { it > 0L } ?: state.maxFreq.takeIf { it > 0L }
            is FrequencyCapability.Unavailable -> state.hwMaxFreq?.takeIf { it > 0L } ?: state.maxFreq.takeIf { it > 0L }
        }

        fun cpuTargetSource(state: com.ivarna.mkm.data.model.CpuPolicyState): String = when (state.frequencyCapability) {
            is FrequencyCapability.Discrete -> "discrete-table"
            is FrequencyCapability.Range -> if (state.hwMaxFreq != null) "cpuinfo-max" else "current-scaling-max"
            is FrequencyCapability.Unavailable -> if (state.hwMaxFreq != null) "cpuinfo-max" else "current-scaling-max"
        }

        fun gpuTarget(status: GpuStatus): Long? {
            val currentMax = status.rawMaxFreq.toLongOrNull()?.takeIf { it > 0L } ?: return null
            return if (status.frequencyTableComplete) {
                when (val capability = status.frequencyCapability) {
                    is FrequencyCapability.Discrete -> capability.values.maxOrNull()
                    is FrequencyCapability.Range -> capability.max
                    is FrequencyCapability.Unavailable -> currentMax
                }
            } else currentMax
        }

        fun storageTarget(storage: UfsStatus): Long? {
            val tableMax = storage.availableFrequencies.mapNotNull { it.toLongOrNull() }.filter { it > 0L }.maxOrNull()
            if (tableMax != null) return tableMax
            // Kernel feature fallback: use the current scaling max when no OPP table is exposed.
            return storage.maxFreq.toLongOrNull()?.takeIf { it > 0L }
        }
    }
}
