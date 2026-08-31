package com.ivarna.mkm.service

import android.content.Context
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.CpuBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GpuBoostSnapshot
import com.ivarna.mkm.data.model.componentsNeedingRestore
import com.ivarna.mkm.data.provider.CpuProvider
import com.ivarna.mkm.data.provider.GameBoostProbe
import com.ivarna.mkm.data.provider.GpuProvider

data class GameBoostHardwareState(
    val applied: Set<GameBoostComponent>,
    val restored: Set<GameBoostComponent>
)

interface GameBoostTuningBackend {
    fun probe(): GameBoostCapabilities
    fun captureSnapshot(capabilities: GameBoostCapabilities): GameBoostSnapshot
    fun applyCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun applyCpuMax(snapshot: GameBoostSnapshot): ApplyResult
    fun applyGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun applyGpuMax(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreCpuRanges(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreGpuRange(snapshot: GameBoostSnapshot): ApplyResult

    /** Reads current CPU/GPU state once for a reconciliation pass. */
    fun inspect(snapshot: GameBoostSnapshot): GameBoostHardwareState
}

/** Adapter over existing provider transactions; Game Boost has no raw sysfs stack of its own. */
class ProductionGameBoostTuningBackend(private val context: Context) : GameBoostTuningBackend {
    private val probe = GameBoostProbe(context)

    override fun probe(): GameBoostCapabilities = probe.probe()

    override fun captureSnapshot(capabilities: GameBoostCapabilities): GameBoostSnapshot {
        val cpuGovernorNeeded = capabilities.supported(GameBoostComponent.CPU_GOVERNOR)
        val cpuMaxNeeded = capabilities.supported(GameBoostComponent.CPU_MAX_LOCK)
        val cpu = if (cpuGovernorNeeded || cpuMaxNeeded) {
            CpuProvider.getCpuStatus().clusters.map { cluster ->
                val state = requireNotNull(cluster.policyState) { "CPU policy ${cluster.id} could not be snapshotted" }
                val governor = if (cpuGovernorNeeded) {
                    state.governor.takeIf { it.isNotBlank() && it != "unknown" }
                        ?: error("CPU policy ${cluster.id} governor could not be snapshotted")
                } else null
                val min = if (cpuMaxNeeded) state.minFreq.takeIf { it > 0L }
                    ?: error("CPU policy ${cluster.id} minimum could not be snapshotted") else null
                val max = if (cpuMaxNeeded) state.maxFreq.takeIf { it >= min!! }
                    ?: error("CPU policy ${cluster.id} maximum could not be snapshotted") else null
                val target = if (cpuMaxNeeded) GameBoostProbe.cpuTarget(state)
                    ?: error("CPU policy ${cluster.id} maximum target could not be snapshotted") else null
                CpuBoostSnapshot(state.policyId, state.path, governor, min, max, target)
            }.also { require(it.isNotEmpty()) { "No CPU policy could be snapshotted" } }
        } else emptyList()

        val gpuGovernorNeeded = capabilities.supported(GameBoostComponent.GPU_GOVERNOR)
        val gpuMaxNeeded = capabilities.supported(GameBoostComponent.GPU_MAX_LOCK)
        val gpu = if (gpuGovernorNeeded || gpuMaxNeeded) {
            val status = GpuProvider.getGpuStatus()
            val path = status.tuningCapabilities?.path?.takeIf { it.isNotBlank() }
                ?: error("GPU tuning path could not be snapshotted")
            val governor = if (gpuGovernorNeeded) {
                status.governor.takeIf { it.isNotBlank() && it != "unknown" }
                    ?: error("GPU governor could not be snapshotted")
            } else null
            val min = if (gpuMaxNeeded) status.rawMinFreq.toLongOrNull()?.takeIf { it > 0L }
                ?: error("GPU minimum could not be snapshotted") else null
            val max = if (gpuMaxNeeded) status.rawMaxFreq.toLongOrNull()?.takeIf { it >= min!! }
                ?: error("GPU maximum could not be snapshotted") else null
            val target = if (gpuMaxNeeded) GameBoostProbe.gpuTarget(status)
                ?: error("GPU maximum target could not be snapshotted") else null
            GpuBoostSnapshot(path, governor, min, max, target)
        } else null

        val identity = GameBoostSnapshotStore(context).currentIdentity()
        return GameBoostSnapshot(bootCount = identity.first, bootId = identity.second, cpu = cpu, gpu = gpu)
    }

    override fun applyCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu) { CpuProvider.applyGovernor(it.policyId, "performance") }

    override fun applyCpuMax(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu) { policy ->
            val target = policy.targetFreq ?: return@applyAll ApplyResult.Failed("CPU maximum target was not captured")
            CpuProvider.applyRange(policy.policyId, target, target)
        }

    override fun applyGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { target ->
            if (!gpuPathMatches(target.path)) ApplyResult.Failed("GPU tuning path changed since the snapshot was captured")
            else exact(GpuProvider.applyGovernor("performance"), "GPU governor")
        } ?: ApplyResult.Failed("GPU was not detected")

    override fun applyGpuMax(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { target ->
            if (!gpuPathMatches(target.path)) return ApplyResult.Failed("GPU tuning path changed since the snapshot was captured")
            val max = target.targetFreq ?: return ApplyResult.Failed("GPU maximum target was not captured")
            exact(GpuProvider.applyRange(max, max), "GPU maximum")
        } ?: ApplyResult.Failed("GPU was not detected")

    override fun restoreCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu) { policy ->
            policy.governor?.let { CpuProvider.applyGovernor(policy.policyId, it) }
                ?: ApplyResult.Applied("not captured", "not captured")
        }

    override fun restoreCpuRanges(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu) { policy ->
            val min = policy.minFreq
            val max = policy.maxFreq
            if (min == null || max == null) ApplyResult.Applied("not captured", "not captured")
            else CpuProvider.applyRange(policy.policyId, min, max)
        }

    override fun restoreGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { saved ->
            if (!gpuPathMatches(saved.path)) ApplyResult.Failed("GPU tuning path changed before restore")
            else saved.governor?.let { exact(GpuProvider.applyGovernor(it), "GPU governor restore") }
                ?: ApplyResult.Applied("not captured", "not captured")
        } ?: ApplyResult.Applied("no GPU", "no GPU")

    override fun restoreGpuRange(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { saved ->
            if (!gpuPathMatches(saved.path)) ApplyResult.Failed("GPU tuning path changed before restore")
            else {
                val min = saved.minFreq
                val max = saved.maxFreq
                if (min == null || max == null) ApplyResult.Applied("not captured", "not captured")
                else exact(GpuProvider.applyRange(min, max), "GPU range restore")
            }
        } ?: ApplyResult.Applied("no GPU", "no GPU")

    override fun inspect(snapshot: GameBoostSnapshot): GameBoostHardwareState {
        val dirty = snapshot.componentsNeedingRestore()
        val cpu = if (snapshot.cpu.isNotEmpty()) CpuProvider.getCpuStatus() else null
        val gpu = if (snapshot.gpu != null) GpuProvider.getGpuStatus() else null
        val applied = dirty.filterTo(linkedSetOf()) { component -> isApplied(component, snapshot, cpu, gpu) }
        val restored = dirty.filterTo(linkedSetOf()) { component -> isRestored(component, snapshot, cpu, gpu) }
        return GameBoostHardwareState(applied, restored)
    }

    private fun isApplied(
        component: GameBoostComponent,
        snapshot: GameBoostSnapshot,
        cpu: com.ivarna.mkm.data.model.CpuStatus?,
        gpu: com.ivarna.mkm.data.model.GpuStatus?
    ): Boolean = when (component) {
        GameBoostComponent.CPU_GOVERNOR -> snapshot.cpu.all { saved ->
            cpu?.clusters?.firstOrNull { it.id == saved.policyId && it.policyPath == saved.path }?.governor == "performance"
        }
        GameBoostComponent.CPU_MAX_LOCK -> snapshot.cpu.all { saved ->
            val target = saved.targetFreq ?: return@all false
            cpu?.clusters?.firstOrNull { it.id == saved.policyId && it.policyPath == saved.path }?.let {
                it.rawMinFreq == target.toString() && it.rawMaxFreq == target.toString()
            } == true
        }
        GameBoostComponent.GPU_GOVERNOR -> snapshot.gpu?.let { saved -> gpu != null && gpuPathMatches(saved.path, gpu) && gpu.governor == "performance" } == true
        GameBoostComponent.GPU_MAX_LOCK -> snapshot.gpu?.let { saved ->
            val target = saved.targetFreq ?: return@let false
            gpu != null && gpuPathMatches(saved.path, gpu) && gpu.rawMinFreq == target.toString() && gpu.rawMaxFreq == target.toString()
        } == true
    }

    private fun isRestored(
        component: GameBoostComponent,
        snapshot: GameBoostSnapshot,
        cpu: com.ivarna.mkm.data.model.CpuStatus?,
        gpu: com.ivarna.mkm.data.model.GpuStatus?
    ): Boolean = when (component) {
        GameBoostComponent.CPU_GOVERNOR -> snapshot.cpu.all { saved ->
            val governor = saved.governor ?: return@all false
            cpu?.clusters?.firstOrNull { it.id == saved.policyId && it.policyPath == saved.path }?.governor == governor
        }
        GameBoostComponent.CPU_MAX_LOCK -> snapshot.cpu.all { saved ->
            val min = saved.minFreq ?: return@all false
            val max = saved.maxFreq ?: return@all false
            cpu?.clusters?.firstOrNull { it.id == saved.policyId && it.policyPath == saved.path }?.let {
                it.rawMinFreq == min.toString() && it.rawMaxFreq == max.toString()
            } == true
        }
        GameBoostComponent.GPU_GOVERNOR -> snapshot.gpu?.let { saved ->
            val governor = saved.governor ?: return@let false
            gpu != null && gpuPathMatches(saved.path, gpu) && gpu.governor == governor
        } == true
        GameBoostComponent.GPU_MAX_LOCK -> snapshot.gpu?.let { saved ->
            val min = saved.minFreq ?: return@let false
            val max = saved.maxFreq ?: return@let false
            gpu != null && gpuPathMatches(saved.path, gpu) && gpu.rawMinFreq == min.toString() && gpu.rawMaxFreq == max.toString()
        } == true
    }

    private fun gpuPathMatches(expected: String): Boolean =
        GpuProvider.getGpuStatus().tuningCapabilities?.path == expected

    private fun gpuPathMatches(expected: String, status: com.ivarna.mkm.data.model.GpuStatus): Boolean =
        status.tuningCapabilities?.path == expected

    private fun applyAll(
        policies: List<CpuBoostSnapshot>,
        operation: (CpuBoostSnapshot) -> ApplyResult
    ): ApplyResult {
        for (policy in policies) {
            val result = operation(policy)
            if (result !is ApplyResult.Applied) return result
        }
        return ApplyResult.Applied("all policies", "all policies")
    }

    private fun exact(result: ApplyResult, label: String): ApplyResult = when (result) {
        is ApplyResult.Applied -> result
        is ApplyResult.Adjusted -> ApplyResult.Failed("$label was adjusted: ${result.message()}")
        is ApplyResult.Failed -> result
    }

    private fun GameBoostCapabilities.supported(component: GameBoostComponent): Boolean =
        components[component]?.supported == true
}
