package com.ivarna.mkm.service

import android.content.Context
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.CpuBoostSnapshot
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GpuBoostSnapshot
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.model.StorageBoostSnapshot
import com.ivarna.mkm.data.model.UfsStatus
import com.ivarna.mkm.data.model.componentsNeedingRestore
import com.ivarna.mkm.data.provider.CpuProvider
import com.ivarna.mkm.data.provider.GameBoostGovernorPolicy
import com.ivarna.mkm.data.provider.GameBoostProbe
import com.ivarna.mkm.data.provider.GpuProvider
import com.ivarna.mkm.data.provider.UfsProvider
import com.ivarna.mkm.shell.SysfsTuningExecutor

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
    fun applyStorageGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun applyStorageMax(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreCpuRanges(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreGpuRange(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreStorageGovernor(snapshot: GameBoostSnapshot): ApplyResult
    fun restoreStorageRange(snapshot: GameBoostSnapshot): ApplyResult

    /** Reads current CPU/GPU state once for a reconciliation pass. */
    fun inspect(snapshot: GameBoostSnapshot): GameBoostHardwareState
}

/** Storage tuning reuses the UFS devfreq node via the shared sysfs executor. */
object GameBoostStorageTuning {
    fun readGovernor(path: String): String = SysfsTuningExecutor.read("$path/governor")

    fun applyGovernor(path: String, governor: String): ApplyResult {
        if (!SysfsTuningExecutor.isSafeValue(governor)) return ApplyResult.Failed("Invalid storage governor")
        val supported = SysfsTuningExecutor.read("$path/available_governors")
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        if (supported.isEmpty() || governor !in supported) {
            return ApplyResult.Failed("Governor '$governor' is not advertised by the storage driver")
        }
        val result = SysfsTuningExecutor.write("$path/governor", governor)
        if (!result.isSuccess) return ApplyResult.Failed("Write failed for storage/governor", result.stderr)
        val after = readGovernor(path)
        return com.ivarna.mkm.data.model.ScalarReadbackVerifier.verify(
            requested = governor, actual = after,
            adjustedReason = "Storage driver selected a different governor."
        )
    }

    fun applyRange(path: String, desiredMin: Long?, desiredMax: Long?): ApplyResult {
        if (desiredMin == null && desiredMax == null) return ApplyResult.Failed("No storage frequency requested")
        if (!SysfsTuningExecutor.exists("$path/min_freq") || !SysfsTuningExecutor.exists("$path/max_freq")) {
            return ApplyResult.Failed("Storage min/max frequency controls are unavailable on this kernel")
        }
        val currentMin = SysfsTuningExecutor.readLong("$path/min_freq")
        val currentMax = SysfsTuningExecutor.readLong("$path/max_freq")
        if (currentMin == null || currentMax == null) return ApplyResult.Failed("Storage frequency bounds are unavailable")
        if (currentMin > currentMax) return ApplyResult.Failed("Storage driver reports an invalid frequency range")
        val plan = when {
            desiredMin != null && desiredMax != null ->
                com.ivarna.mkm.data.model.FrequencyRangePlanner.plan(currentMin, currentMax, desiredMin, desiredMax)
            desiredMax != null -> com.ivarna.mkm.data.model.FrequencyRangePlanner.forMax(currentMin, currentMax, desiredMax)
            else -> com.ivarna.mkm.data.model.FrequencyRangePlanner.forMin(currentMin, currentMax, desiredMin!!)
        }
        val transaction = com.ivarna.mkm.data.model.RangeWriteTransaction.execute(
            original = com.ivarna.mkm.data.model.RangeReadback(currentMin, currentMax),
            plan = plan,
            write = { step ->
                val file = if (step.isMin) "min_freq" else "max_freq"
                SysfsTuningExecutor.write("$path/$file", step.value.toString()).isSuccess
            },
            readImmediate = {
                com.ivarna.mkm.data.model.RangeReadback(
                    SysfsTuningExecutor.readLong("$path/min_freq") ?: 0L,
                    SysfsTuningExecutor.readLong("$path/max_freq") ?: 0L
                )
            },
            readFinal = {
                Thread.sleep(150L)
                com.ivarna.mkm.data.model.RangeReadback(
                    SysfsTuningExecutor.readLong("$path/min_freq") ?: 0L,
                    SysfsTuningExecutor.readLong("$path/max_freq") ?: 0L
                )
            }
        )
        if (transaction !is com.ivarna.mkm.data.model.RangeTransactionResult.Verified) {
            return ApplyResult.Failed("Storage frequency transaction could not be verified")
        }
        val after = transaction.final
        val requestedText = "min=${plan.min},max=${plan.max}"
        return when {
            after.min != plan.min || after.max != plan.max -> ApplyResult.Adjusted(
                requestedText, "min=${after.min},max=${after.max}",
                "Storage driver clamped or overrode the requested range."
            )
            plan.adjusted -> ApplyResult.Adjusted(requestedText, requestedText, plan.adjustmentReason ?: "Range adjusted")
            else -> ApplyResult.Applied(requestedText, requestedText)
        }
    }
}

/** Capability-scoped snapshot capture kept pure so partial domains are testable. */
internal object GameBoostSnapshotCapture {
    fun cpu(
        status: CpuStatus,
        governorNeeded: Boolean,
        rangeNeeded: Boolean,
        targetGovernor: String? = GameBoostGovernorPolicy.BOOST_GOVERNOR
    ): List<CpuBoostSnapshot> =
        if (!governorNeeded && !rangeNeeded) emptyList() else status.clusters.map { cluster ->
            val state = requireNotNull(cluster.policyState) { "CPU policy ${cluster.id} could not be snapshotted" }
            val governor = if (governorNeeded) {
                state.governor.takeIf { it.isNotBlank() && it != "unknown" }
                    ?: error("CPU policy ${cluster.id} governor could not be snapshotted")
            } else null
            val min = if (rangeNeeded) state.minFreq.takeIf { it > 0L }
                ?: error("CPU policy ${cluster.id} minimum could not be snapshotted") else null
            val max = if (rangeNeeded) state.maxFreq.takeIf { it >= min!! }
                ?: error("CPU policy ${cluster.id} maximum could not be snapshotted") else null
            val target = if (rangeNeeded) GameBoostProbe.cpuTarget(state)
                ?: error("CPU policy ${cluster.id} maximum target could not be snapshotted") else null
            CpuBoostSnapshot(state.policyId, state.path, governor, min, max, target, targetGovernor)
        }.also { require(it.isNotEmpty()) { "No CPU policy could be snapshotted" } }

    fun gpu(
        status: GpuStatus,
        governorNeeded: Boolean,
        rangeNeeded: Boolean,
        targetGovernor: String? = GameBoostGovernorPolicy.BOOST_GOVERNOR
    ): GpuBoostSnapshot? {
        if (!governorNeeded && !rangeNeeded) return null
        val path = status.tuningCapabilities?.path?.takeIf { it.isNotBlank() }
            ?: error("GPU tuning path could not be snapshotted")
        val governor = if (governorNeeded) {
            status.governor.takeIf { it.isNotBlank() && it != "unknown" }
                ?: error("GPU governor could not be snapshotted")
        } else null
        val min = if (rangeNeeded) status.rawMinFreq.toLongOrNull()?.takeIf { it > 0L }
            ?: error("GPU minimum could not be snapshotted") else null
        val max = if (rangeNeeded) status.rawMaxFreq.toLongOrNull()?.takeIf { it >= min!! }
            ?: error("GPU maximum could not be snapshotted") else null
        val target = if (rangeNeeded) GameBoostProbe.gpuTarget(status)
            ?: error("GPU maximum target could not be snapshotted") else null
        return GpuBoostSnapshot(path, governor, min, max, target, targetGovernor)
    }

    fun storage(
        status: UfsStatus,
        governorNeeded: Boolean,
        rangeNeeded: Boolean,
        targetGovernor: String? = GameBoostGovernorPolicy.BOOST_GOVERNOR
    ): StorageBoostSnapshot? {
        if (!governorNeeded && !rangeNeeded) return null
        val path = status.controllerPath.takeIf { it.isNotBlank() }
            ?: error("Storage tuning path could not be snapshotted")
        val governor = if (governorNeeded) {
            status.currentGovernor.takeIf { it.isNotBlank() && it != "unknown" }
                ?: error("Storage governor could not be snapshotted")
        } else null
        val min = if (rangeNeeded) status.minFreq.toLongOrNull()?.takeIf { it > 0L }
            ?: error("Storage minimum could not be snapshotted") else null
        val max = if (rangeNeeded) status.maxFreq.toLongOrNull()?.takeIf { it >= min!! }
            ?: error("Storage maximum could not be snapshotted") else null
        val target = if (rangeNeeded) GameBoostProbe.storageTarget(status)
            ?: error("Storage maximum target could not be snapshotted") else null
        return StorageBoostSnapshot(path, governor, min, max, target, targetGovernor)
    }
}

/** Adapter over existing provider transactions; Game Boost has no raw sysfs stack of its own. */
class ProductionGameBoostTuningBackend(private val context: Context) : GameBoostTuningBackend {
    private val probe = GameBoostProbe(context)

    override fun probe(): GameBoostCapabilities = probe.probe()

    override fun captureSnapshot(capabilities: GameBoostCapabilities): GameBoostSnapshot {
        val cpuGovernorNeeded = capabilities.supported(GameBoostComponent.CPU_GOVERNOR)
        val cpuMaxNeeded = capabilities.supported(GameBoostComponent.CPU_MAX_LOCK)
        val cpuGovTarget = capabilities.components[GameBoostComponent.CPU_GOVERNOR]?.target
            ?: GameBoostGovernorPolicy.BOOST_GOVERNOR
        val cpu = if (cpuGovernorNeeded || cpuMaxNeeded) {
            GameBoostSnapshotCapture.cpu(CpuProvider.getCpuStatus(), cpuGovernorNeeded, cpuMaxNeeded, cpuGovTarget)
        } else emptyList()

        val gpuGovernorNeeded = capabilities.supported(GameBoostComponent.GPU_GOVERNOR)
        val gpuMaxNeeded = capabilities.supported(GameBoostComponent.GPU_MAX_LOCK)
        val gpuGovTarget = capabilities.components[GameBoostComponent.GPU_GOVERNOR]?.target
            ?: GameBoostGovernorPolicy.BOOST_GOVERNOR
        val gpu = if (gpuGovernorNeeded || gpuMaxNeeded) {
            GameBoostSnapshotCapture.gpu(GpuProvider.getGpuStatus(), gpuGovernorNeeded, gpuMaxNeeded, gpuGovTarget)
        } else null

        val storageGovernorNeeded = capabilities.supported(GameBoostComponent.STORAGE_GOVERNOR)
        val storageMaxNeeded = capabilities.supported(GameBoostComponent.STORAGE_MAX_LOCK)
        val storageGovTarget = capabilities.components[GameBoostComponent.STORAGE_GOVERNOR]?.target
            ?: GameBoostGovernorPolicy.BOOST_GOVERNOR
        val storage = if (storageGovernorNeeded || storageMaxNeeded) {
            runCatching {
                GameBoostSnapshotCapture.storage(UfsProvider.getUfsStatus(), storageGovernorNeeded, storageMaxNeeded, storageGovTarget)
            }.getOrNull()
        } else null

        val identity = GameBoostSnapshotStore(context).currentIdentity()
        return GameBoostSnapshot(bootCount = identity.first, bootId = identity.second, cpu = cpu, gpu = gpu, storage = storage)
    }

    override fun applyCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu) { CpuProvider.applyGovernor(it.policyId, it.targetGovernor ?: GameBoostGovernorPolicy.BOOST_GOVERNOR) }

    override fun applyCpuMax(snapshot: GameBoostSnapshot): ApplyResult =
        applyAll(snapshot.cpu) { policy ->
            val target = policy.targetFreq ?: return@applyAll ApplyResult.Failed("CPU maximum target was not captured")
            CpuProvider.applyRange(policy.policyId, target, target)
        }

    override fun applyGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { target ->
            if (!gpuPathMatches(target.path)) ApplyResult.Failed("GPU tuning path changed since the snapshot was captured")
            else GpuProvider.applyGovernor(target.targetGovernor ?: GameBoostGovernorPolicy.BOOST_GOVERNOR)
        } ?: ApplyResult.Failed("GPU was not detected")

    override fun applyGpuMax(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.gpu?.let { target ->
            if (!gpuPathMatches(target.path)) return ApplyResult.Failed("GPU tuning path changed since the snapshot was captured")
            val max = target.targetFreq ?: return ApplyResult.Failed("GPU maximum target was not captured")
            GpuProvider.applyRange(max, max)
        } ?: ApplyResult.Failed("GPU was not detected")

    override fun applyStorageGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.storage?.let { saved ->
            if (!storagePathMatches(saved.path)) ApplyResult.Failed("Storage tuning path changed since the snapshot was captured")
            else GameBoostStorageTuning.applyGovernor(saved.path, saved.targetGovernor ?: GameBoostGovernorPolicy.BOOST_GOVERNOR)
        } ?: ApplyResult.Failed("Storage was not detected")

    override fun applyStorageMax(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.storage?.let { saved ->
            if (!storagePathMatches(saved.path)) return ApplyResult.Failed("Storage tuning path changed since the snapshot was captured")
            val max = saved.targetFreq ?: return ApplyResult.Failed("Storage maximum target was not captured")
            GameBoostStorageTuning.applyRange(saved.path, max, max)
        } ?: ApplyResult.Failed("Storage was not detected")

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

    override fun restoreStorageGovernor(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.storage?.let { saved ->
            if (!storagePathMatches(saved.path)) ApplyResult.Failed("Storage tuning path changed before restore")
            else saved.governor?.let { exact(GameBoostStorageTuning.applyGovernor(saved.path, it), "Storage governor restore") }
                ?: ApplyResult.Applied("not captured", "not captured")
        } ?: ApplyResult.Applied("no storage", "no storage")

    override fun restoreStorageRange(snapshot: GameBoostSnapshot): ApplyResult =
        snapshot.storage?.let { saved ->
            if (!storagePathMatches(saved.path)) ApplyResult.Failed("Storage tuning path changed before restore")
            else {
                val min = saved.minFreq
                val max = saved.maxFreq
                if (min == null || max == null) ApplyResult.Applied("not captured", "not captured")
                else exact(GameBoostStorageTuning.applyRange(saved.path, min, max), "Storage range restore")
            }
        } ?: ApplyResult.Applied("no storage", "no storage")

    override fun inspect(snapshot: GameBoostSnapshot): GameBoostHardwareState {
        val dirty = snapshot.componentsNeedingRestore()
        val cpu = if (snapshot.cpu.isNotEmpty()) CpuProvider.getCpuStatus() else null
        val gpu = if (snapshot.gpu != null) GpuProvider.getGpuStatus() else null
        val storage = if (snapshot.storage != null) UfsProvider.getUfsStatus() else null
        val applied = dirty.filterTo(linkedSetOf()) { component -> isApplied(component, snapshot, cpu, gpu, storage) }
        val restored = dirty.filterTo(linkedSetOf()) { component -> isRestored(component, snapshot, cpu, gpu, storage) }
        return GameBoostHardwareState(applied, restored)
    }

    private fun isApplied(
        component: GameBoostComponent,
        snapshot: GameBoostSnapshot,
        cpu: com.ivarna.mkm.data.model.CpuStatus?,
        gpu: com.ivarna.mkm.data.model.GpuStatus?,
        storage: UfsStatus? = null
    ): Boolean = when (component) {
        GameBoostComponent.CPU_GOVERNOR -> snapshot.cpu.all { saved ->
            val want = saved.targetGovernor ?: GameBoostGovernorPolicy.BOOST_GOVERNOR
            cpu?.clusters?.firstOrNull { it.id == saved.policyId && it.policyPath == saved.path }?.governor == want
        }
        GameBoostComponent.CPU_MAX_LOCK -> snapshot.cpu.all { saved ->
            val target = saved.targetFreq ?: return@all false
            cpu?.clusters?.firstOrNull { it.id == saved.policyId && it.policyPath == saved.path }?.let {
                val curMin = it.rawMinFreq.toLongOrNull()
                val curMax = it.rawMaxFreq.toLongOrNull()
                // Strict target match, or a kernel-clamped lock at the highest allowed clock.
                (curMin != null && curMax != null && curMin.toString() == target.toString() && curMax.toString() == target.toString()) ||
                    (curMin != null && curMax != null && curMin == curMax && saved.maxFreq != null && curMax >= saved.maxFreq)
            } == true
        }
        GameBoostComponent.GPU_GOVERNOR -> snapshot.gpu?.let { saved ->
            val want = saved.targetGovernor ?: GameBoostGovernorPolicy.BOOST_GOVERNOR
            gpu != null && gpuPathMatches(saved.path, gpu) && gpu.governor == want
        } == true
        GameBoostComponent.GPU_MAX_LOCK -> snapshot.gpu?.let { saved ->
            val target = saved.targetFreq ?: return@let false
            val curMin = gpu?.rawMinFreq?.toLongOrNull()
            val curMax = gpu?.rawMaxFreq?.toLongOrNull()
            gpu != null && gpuPathMatches(saved.path, gpu) && curMin != null && curMax != null &&
                ((curMin.toString() == target.toString() && curMax.toString() == target.toString()) ||
                    (curMin == curMax && saved.maxFreq != null && curMax >= saved.maxFreq))
        } == true
        GameBoostComponent.STORAGE_GOVERNOR -> snapshot.storage?.let { saved ->
            val want = saved.targetGovernor ?: GameBoostGovernorPolicy.BOOST_GOVERNOR
            storage != null && storage.controllerPath == saved.path && storage.currentGovernor == want
        } == true
        GameBoostComponent.STORAGE_MAX_LOCK -> snapshot.storage?.let { saved ->
            val target = saved.targetFreq ?: return@let false
            val curMin = storage?.minFreq?.toLongOrNull()
            val curMax = storage?.maxFreq?.toLongOrNull()
            storage != null && storage.controllerPath == saved.path && curMin != null && curMax != null &&
                ((curMin == target && curMax == target) ||
                    (curMin == curMax && saved.maxFreq != null && curMax >= saved.maxFreq) ||
                    (kotlin.math.abs(curMin - target) <= 5000L && kotlin.math.abs(curMax - target) <= 5000L))
        } == true
    }

    private fun isRestored(
        component: GameBoostComponent,
        snapshot: GameBoostSnapshot,
        cpu: com.ivarna.mkm.data.model.CpuStatus?,
        gpu: com.ivarna.mkm.data.model.GpuStatus?
    ): Boolean = isRestored(component, snapshot, cpu, gpu, null)

    private fun isRestored(
        component: GameBoostComponent,
        snapshot: GameBoostSnapshot,
        cpu: com.ivarna.mkm.data.model.CpuStatus?,
        gpu: com.ivarna.mkm.data.model.GpuStatus?,
        storage: UfsStatus?
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
        GameBoostComponent.STORAGE_GOVERNOR -> snapshot.storage?.let { saved ->
            val governor = saved.governor ?: return@let false
            storage != null && storage.controllerPath == saved.path && storage.currentGovernor == governor
        } == true
        GameBoostComponent.STORAGE_MAX_LOCK -> snapshot.storage?.let { saved ->
            val min = saved.minFreq ?: return@let false
            val max = saved.maxFreq ?: return@let false
            val curMin = storage?.minFreq?.toLongOrNull()
            val curMax = storage?.maxFreq?.toLongOrNull()
            storage != null && storage.controllerPath == saved.path && curMin != null && curMax != null &&
                (curMin == min || kotlin.math.abs(curMin - min) <= 5000L) &&
                (curMax == max || kotlin.math.abs(curMax - max) <= 5000L)
        } == true
    }

    private fun gpuPathMatches(expected: String): Boolean =
        GpuProvider.getGpuStatus().tuningCapabilities?.path == expected

    private fun gpuPathMatches(expected: String, status: com.ivarna.mkm.data.model.GpuStatus): Boolean =
        status.tuningCapabilities?.path == expected

    private fun storagePathMatches(expected: String): Boolean =
        runCatching { UfsProvider.getUfsStatus().controllerPath }.getOrNull() == expected

    private fun applyAll(
        policies: List<CpuBoostSnapshot>,
        operation: (CpuBoostSnapshot) -> ApplyResult
    ): ApplyResult {
        // A kernel clamp (Adjusted) still leaves the policy at its highest
        // allowed clock, so it counts as boosted. Only Failed aborts enable.
        var firstAdjusted: ApplyResult.Adjusted? = null
        for (policy in policies) {
            when (val result = operation(policy)) {
                is ApplyResult.Failed -> return result
                is ApplyResult.Adjusted -> if (firstAdjusted == null) firstAdjusted = result
                is ApplyResult.Applied -> Unit
            }
        }
        return firstAdjusted ?: ApplyResult.Applied("all policies", "all policies")
    }

    private fun exact(result: ApplyResult, label: String): ApplyResult = when (result) {
        is ApplyResult.Applied -> result
        // Restores must match the saved originals exactly; a clamp there
        // means the device did not return to its previous state.
        is ApplyResult.Adjusted -> ApplyResult.Failed("$label was adjusted: ${result.message()}")
        is ApplyResult.Failed -> result
    }

    private fun GameBoostCapabilities.supported(component: GameBoostComponent): Boolean =
        components[component]?.supported == true
}
