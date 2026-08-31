package com.ivarna.mkm.service

import android.content.Context
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.CpuBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GpuBoostSnapshot
import com.ivarna.mkm.data.provider.CpuProvider
import com.ivarna.mkm.data.provider.GameBoostProbe
import com.ivarna.mkm.data.provider.GpuProvider

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
    fun isApplied(snapshot: GameBoostSnapshot, component: GameBoostComponent): Boolean
}

/** Adapter over the existing provider transactions; Game Boost has no raw sysfs stack of its own. */
class ProductionGameBoostTuningBackend(private val context: Context) : GameBoostTuningBackend {
    private val probe = GameBoostProbe(context)

    override fun probe(): GameBoostCapabilities = probe.probe()

    override fun captureSnapshot(capabilities: GameBoostCapabilities): GameBoostSnapshot {
        val cpu = CpuProvider.getCpuStatus().clusters.map { cluster ->
            val state = requireNotNull(cluster.policyState) { "CPU policy ${cluster.id} could not be snapshotted" }
            require(state.governor.isNotBlank() && state.minFreq > 0L && state.maxFreq >= state.minFreq) { "CPU policy ${cluster.id} has invalid original state" }
            CpuBoostSnapshot(state.policyId, state.path, state.governor, state.minFreq, state.maxFreq, GameBoostProbe.cpuTarget(state))
        }
        require(cpu.isNotEmpty()) { "No CPU policy could be snapshotted" }
        val gpuStatus = GpuProvider.getGpuStatus()
        val gpu = gpuStatus.tuningCapabilities?.path?.takeIf { it.isNotBlank() }?.let { path ->
            val min = gpuStatus.rawMinFreq.toLongOrNull() ?: error("GPU minimum could not be snapshotted")
            val max = gpuStatus.rawMaxFreq.toLongOrNull() ?: error("GPU maximum could not be snapshotted")
            require(gpuStatus.governor.isNotBlank() && min > 0L && max >= min) { "GPU has invalid original state" }
            GpuBoostSnapshot(path, gpuStatus.governor, min, max, GameBoostProbe.gpuTarget(gpuStatus))
        }
        val identity = GameBoostSnapshotStore(context).currentIdentity()
        return GameBoostSnapshot(bootCount = identity.first, bootId = identity.second, cpu = cpu, gpu = gpu)
    }

    override fun applyCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu, operation = { CpuProvider.applyGovernor(it.policyId, "performance") }, rollback = { restoreCpuGovernor(snapshot) })

    override fun applyCpuMax(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu, operation = { p ->
            val target = p.targetFreq ?: p.maxFreq
            CpuProvider.applyRange(p.policyId, target, target)
        }, rollback = { restoreCpuRanges(snapshot) })

    override fun applyGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        if (snapshot.gpu == null) ApplyResult.Failed("GPU was not detected")
        else exact(GpuProvider.applyGovernor("performance"), "GPU governor")

    override fun applyGpuMax(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { target ->
            val max = target.targetFreq ?: target.maxFreq
            exact(GpuProvider.applyRange(max, max), "GPU maximum")
        }
            ?: ApplyResult.Failed("GPU was not detected")

    override fun restoreCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu, operation = { CpuProvider.applyGovernor(it.policyId, it.governor) })

    override fun restoreCpuRanges(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu, operation = { CpuProvider.applyRange(it.policyId, it.minFreq, it.maxFreq) })

    override fun restoreGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { exact(GpuProvider.applyGovernor(it.governor), "GPU governor restore") }
            ?: ApplyResult.Applied("no GPU", "no GPU")

    override fun restoreGpuRange(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { exact(GpuProvider.applyRange(it.minFreq, it.maxFreq), "GPU range restore") }
            ?: ApplyResult.Applied("no GPU", "no GPU")

    override fun isApplied(snapshot: GameBoostSnapshot, component: GameBoostComponent): Boolean {
        val cpu = CpuProvider.getCpuStatus()
        val gpu = GpuProvider.getGpuStatus()
        return when (component) {
            GameBoostComponent.CPU_GOVERNOR -> snapshot.cpu.all { saved -> cpu.clusters.firstOrNull { it.id == saved.policyId }?.governor == "performance" }
            GameBoostComponent.CPU_MAX_LOCK -> snapshot.cpu.all { saved ->
                val target = saved.targetFreq ?: saved.maxFreq
                cpu.clusters.firstOrNull { it.id == saved.policyId }?.let { it.rawMinFreq == target.toString() && it.rawMaxFreq == target.toString() } == true
            }
            GameBoostComponent.GPU_GOVERNOR -> snapshot.gpu?.let { gpu.governor == "performance" } == true
            GameBoostComponent.GPU_MAX_LOCK -> snapshot.gpu?.let { target ->
                val max = target.targetFreq ?: target.maxFreq
                gpu.rawMinFreq == max.toString() && gpu.rawMaxFreq == max.toString()
            } == true
        }
    }

    private fun applyAll(
        policies: List<CpuBoostSnapshot>,
        operation: (CpuBoostSnapshot) -> ApplyResult,
        rollback: () -> ApplyResult = { ApplyResult.Applied("none", "none") }
    ): ApplyResult {
        for (policy in policies) {
            val result = operation(policy)
            if (result !is ApplyResult.Applied) {
                val restored = rollback() is ApplyResult.Applied
                return ApplyResult.Failed("${result.message()}; CPU component rollback ${if (restored) "succeeded" else "failed"}")
            }
        }
        return ApplyResult.Applied("all policies", "all policies")
    }

    private fun exact(result: ApplyResult, label: String): ApplyResult = when (result) {
        is ApplyResult.Applied -> result
        is ApplyResult.Adjusted -> ApplyResult.Failed("$label was adjusted: ${result.message()}")
        is ApplyResult.Failed -> result
    }
}
