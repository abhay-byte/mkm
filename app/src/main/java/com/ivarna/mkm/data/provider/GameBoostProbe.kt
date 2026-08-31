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

/** Read-only capability assessment. It never treats privilege alone as writability. */
class GameBoostProbe(private val context: Context) {
    fun probe(): GameBoostCapabilities = buildCapabilities(
        cpu = CpuProvider.getCpuStatus(),
        gpu = GpuProvider.getGpuStatus(),
        maxLocksThermallySupported = Build.VERSION.SDK_INT >= 29 &&
            context.getSystemService(PowerManager::class.java) != null
    )

    companion object {
        fun buildCapabilities(
            cpu: CpuStatus,
            gpu: GpuStatus,
            maxLocksThermallySupported: Boolean
        ): GameBoostCapabilities {
            val cpuPolicies = cpu.clusters
            val cpuGovernorOk = cpuPolicies.isNotEmpty() && cpuPolicies.all {
                it.governorWritable && "performance" in it.availableGovernors && it.policyState != null
            }
            val cpuGovernorReason = when {
                cpuPolicies.isEmpty() -> "No cpufreq policies were discovered"
                !cpuPolicies.all { it.governorWritable } -> "At least one CPU governor node is not writable"
                !cpuPolicies.all { "performance" in it.availableGovernors } -> "performance is not advertised by every CPU policy"
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
            val gpuGovernorOk = gpuCaps != null && gpuCaps.path.isNotBlank() &&
                gpu.governorWritable && "performance" in gpu.availableGovernors
            val gpuGovernorReason = when {
                gpuCaps == null || gpuCaps.path.isBlank() -> "GPU devfreq device was not detected"
                !gpu.governorWritable -> gpu.governorReason ?: "GPU governor node is not writable"
                !("performance" in gpu.availableGovernors) -> "performance is not advertised by the GPU driver"
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

            val components = mapOf(
                GameBoostComponent.CPU_GOVERNOR to GameBoostComponentCapability(GameBoostComponent.CPU_GOVERNOR, cpuGovernorOk, cpuGovernorReason),
                GameBoostComponent.CPU_MAX_LOCK to GameBoostComponentCapability(
                    GameBoostComponent.CPU_MAX_LOCK, cpuMaxOk, cpuMaxReason,
                    cpuPolicies.mapNotNull { it.policyState?.let(::cpuTarget) }.joinToString(","),
                    cpuPolicies.mapNotNull { it.policyState?.let { state -> cpuTargetSource(state) } }.distinct().joinToString(",")
                ),
                GameBoostComponent.GPU_GOVERNOR to GameBoostComponentCapability(GameBoostComponent.GPU_GOVERNOR, gpuGovernorOk, gpuGovernorReason),
                GameBoostComponent.GPU_MAX_LOCK to GameBoostComponentCapability(
                    GameBoostComponent.GPU_MAX_LOCK, gpuMaxOk, gpuMaxReason, gpuMaxTarget?.toString(),
                    if (gpu.frequencyTableComplete) "discrete-table" else "current-scaling-max"
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
    }
}
