package com.ivarna.mkm.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostState
import com.ivarna.mkm.data.model.componentsNeedingRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

sealed interface GameBoostTransitionResult {
    data class Success(val warnings: List<String> = emptyList()) : GameBoostTransitionResult
    data class Failure(val reason: String) : GameBoostTransitionResult
}

private val APPLY_ORDER = listOf(
    GameBoostComponent.CPU_GOVERNOR,
    GameBoostComponent.CPU_MAX_LOCK,
    GameBoostComponent.GPU_GOVERNOR,
    GameBoostComponent.GPU_MAX_LOCK,
    GameBoostComponent.STORAGE_GOVERNOR,
    GameBoostComponent.STORAGE_MAX_LOCK
)

private val RESTORE_ORDER = listOf(
    GameBoostComponent.STORAGE_MAX_LOCK,
    GameBoostComponent.STORAGE_GOVERNOR,
    GameBoostComponent.GPU_MAX_LOCK,
    GameBoostComponent.GPU_GOVERNOR,
    GameBoostComponent.CPU_MAX_LOCK,
    GameBoostComponent.CPU_GOVERNOR
)

/** Serialized, recoverable global tuning session. */
class GameBoostManager(
    context: Context,
    private val backend: GameBoostTuningBackend = ProductionGameBoostTuningBackend(context),
    private val sessionStore: GameBoostSessionStore = GameBoostSnapshotStore(context.applicationContext)
) {
    private val appContext = context.applicationContext
    private val store = sessionStore
    private val mutex = Mutex()
    private val reconciliationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reconciliationStarted = AtomicBoolean(false)

    init {
        // SharedPreferences access is intentionally the only constructor work.
        // Provider/root/sysfs reconciliation is scheduled asynchronously.
        if (store.hasSnapshot()) {
            GameBoostRegistry.publish(GameBoostState.RecoveryRequired("Reconciling saved Game Boost state", emptyList()))
        } else {
            GameBoostRegistry.publish(GameBoostState.Off)
        }
    }

    fun startReconciliation() {
        if (reconciliationStarted.compareAndSet(false, true)) {
            reconciliationScope.launch { reconcile() }
        }
    }

    fun currentState(): GameBoostState = GameBoostRegistry.state.value

    fun capabilities(): GameBoostCapabilities = backend.probe()

    suspend fun enable(): GameBoostTransitionResult = mutex.withLock {
        KernelTuningCoordinator.withMutation { enableLocked() }
    }

    private fun enableLocked(): GameBoostTransitionResult {
        if (GameBoostRegistry.ownsTuning()) {
            return GameBoostTransitionResult.Failure("Game Boost is already active or recovering")
        }
        val capabilities = runCatching { backend.probe() }.getOrElse {
            return failBeforeMutation("Capability probe failed: ${it.message}")
        }
        if (!capabilities.boostPossible) {
            return failBeforeMutation(
                capabilities.components.values.mapNotNull { it.reason }.distinct().joinToString("; ")
                    .ifBlank { "No safe Game Boost component is supported" }
            )
        }

        // Claim ownership before snapshot capture so manual tuning cannot race
        // the preflight window before the first component mutation.
        GameBoostRegistry.publish(GameBoostState.Enabling("capturing original state"))
        val snapshot = runCatching { backend.captureSnapshot(capabilities) }.getOrElse {
            return failBeforeMutation("Could not capture the original tuning state: ${it.message}")
        }
        val supported = APPLY_ORDER.filter { capabilities.components[it]?.supported == true }.toSet()
        var working = snapshot.copy(phase = "ENABLING", attempted = supported)
        if (!store.save(working)) return failBeforeMutation("Could not durably save the original tuning state")

        val applied = linkedSetOf<GameBoostComponent>()
        val warnings = mutableListOf<String>()
        val unsupported = APPLY_ORDER.filterNot { it in supported }.toSet()
        for (component in APPLY_ORDER) {
            val capability = capabilities.components.getValue(component)
            if (!capability.supported) continue

            if (component.isMaxLock() && isSevereThermal()) {
                working = working.copy(thermallyReleased = working.thermallyReleased + component)
                if (!store.save(working)) {
                    return abortWithRollback("Could not persist thermal limitation", working, working.componentsNeedingRestore())
                }
                continue
            }

            GameBoostRegistry.publish(GameBoostState.Enabling(component.name))
            working = working.copy(attempted = working.attempted + component)
            if (!store.save(working)) {
                return abortWithRollback("Could not persist Game Boost ownership before mutation", working, working.componentsNeedingRestore())
            }

            val result = runCatching { apply(component, working) }.getOrElse {
                ApplyResult.Failed(it.message ?: "Game Boost mutation failed")
            }
            // A kernel clamp (Adjusted) still boosts to the highest allowed
            // clock, so it counts as applied and surfaces as a warning like
            // the "Kernel clamped ..." note instead of aborting the session.
            if (result is ApplyResult.Failed) {
                // The current component may have changed before its failure was
                // observed, so it is always part of the conservative rollback.
                return abortWithRollback(result.message(), working, applied + component)
            }
            if (result is ApplyResult.Adjusted) warnings += result.message()

            applied += component
            working = working.copy(applied = applied.toSet())
            if (!store.save(working)) {
                return abortWithRollback("Could not persist Game Boost state after mutation", working, working.componentsNeedingRestore())
            }
        }

        val finalState = if (working.thermallyReleased.isNotEmpty()) {
            GameBoostState.ThermalLimited(applied.toSet(), working.thermallyReleased, warnings.toList())
        } else {
            GameBoostState.Active(applied.toSet(), unsupported, warnings.toList())
        }
        working = working.copy(
            phase = if (finalState is GameBoostState.ThermalLimited) "THERMAL_LIMITED" else "ACTIVE"
        )
        if (!store.save(working)) {
            return abortWithRollback("Could not durably commit active Game Boost state", working, working.componentsNeedingRestore())
        }
        GameBoostRegistry.publish(finalState)
        return GameBoostTransitionResult.Success(warnings.toList())
    }

    suspend fun disable(): GameBoostTransitionResult = mutex.withLock {
        KernelTuningCoordinator.withMutation { disableLocked() }
    }

    private fun disableLocked(): GameBoostTransitionResult {
        val snapshot = store.load()
        if (snapshot == null) {
            GameBoostRegistry.publish(GameBoostState.Off)
            return GameBoostTransitionResult.Success()
        }
        GameBoostRegistry.publish(GameBoostState.Disabling)
        val dirty = snapshot.componentsNeedingRestore()
        val failed = restore(dirty, snapshot)
        if (failed != null) {
            return enterRecovery(snapshot, dirty, "${failed.message()} while restoring Game Boost")
        }
        if (!store.clear()) {
            return enterRecovery(snapshot, dirty, "Hardware was restored, but Game Boost ownership metadata could not be cleared")
        }
        GameBoostRegistry.publish(GameBoostState.Off)
        return GameBoostTransitionResult.Success()
    }

    suspend fun retryRecovery(): GameBoostTransitionResult = disable()

    /** Called by the foreground service when the system reports severe thermal stress. */
    suspend fun onThermalStatus(status: Int): Unit = mutex.withLock {
        KernelTuningCoordinator.withMutation { onThermalStatusLocked(status) }
    }

    private fun onThermalStatusLocked(status: Int) {
        if (status < PowerManager.THERMAL_STATUS_SEVERE) return
        val snapshot = store.load() ?: return
        val maxComponents = snapshot.componentsNeedingRestore()
            .filter { it.isMaxLock() }
            .toSet()
        if (maxComponents.isEmpty()) return

        for (component in RESTORE_ORDER.filter { it in maxComponents }) {
            val result = when (component) {
                GameBoostComponent.CPU_MAX_LOCK -> backend.restoreCpuRanges(snapshot)
                GameBoostComponent.GPU_MAX_LOCK -> backend.restoreGpuRange(snapshot)
                GameBoostComponent.STORAGE_MAX_LOCK -> backend.restoreStorageRange(snapshot)
                else -> ApplyResult.Applied("not a max lock", "not a max lock")
            }
            if (result !is ApplyResult.Applied) {
                enterRecovery(snapshot, snapshot.componentsNeedingRestore(), "${result.message()} during thermal release")
                return
            }
        }

        val released = snapshot.thermallyReleased + maxComponents
        val updated = snapshot.copy(phase = "THERMAL_LIMITED", thermallyReleased = released)
        if (!store.save(updated)) {
            enterRecovery(snapshot, snapshot.componentsNeedingRestore(), "Could not persist thermal release state")
            return
        }
        GameBoostRegistry.publish(GameBoostState.ThermalLimited(snapshot.applied - released, released))
    }

    /** Called by the service when API 29+ thermal monitoring is unavailable. */
    suspend fun onThermalMonitoringUnavailable(): Unit = mutex.withLock {
        KernelTuningCoordinator.withMutation {
            val snapshot = store.load() ?: return@withMutation
            val maxComponents = snapshot.componentsNeedingRestore().filter { it.isMaxLock() }.toSet()
            if (maxComponents.isEmpty()) return@withMutation
            for (component in RESTORE_ORDER.filter { it in maxComponents }) {
                val result = when (component) {
                    GameBoostComponent.CPU_MAX_LOCK -> backend.restoreCpuRanges(snapshot)
                    GameBoostComponent.GPU_MAX_LOCK -> backend.restoreGpuRange(snapshot)
                    GameBoostComponent.STORAGE_MAX_LOCK -> backend.restoreStorageRange(snapshot)
                    else -> ApplyResult.Applied("not a max lock", "not a max lock")
                }
                if (result !is ApplyResult.Applied) {
                    enterRecovery(snapshot, snapshot.componentsNeedingRestore(), "${result.message()} while thermal monitoring was unavailable")
                    return@withMutation
                }
            }
            val released = snapshot.thermallyReleased + maxComponents
            val updated = snapshot.copy(phase = "THERMAL_LIMITED", thermallyReleased = released)
            if (!store.save(updated)) {
                enterRecovery(snapshot, snapshot.componentsNeedingRestore(), "Could not persist fail-safe thermal release")
                return@withMutation
            }
            GameBoostRegistry.publish(GameBoostState.ThermalLimited(snapshot.applied - released, released))
        }
    }

    /** Reconciles persisted ownership without doing provider I/O on the caller thread. */
    suspend fun reconcile() = mutex.withLock {
        KernelTuningCoordinator.withMutation { reconcileLocked() }
    }

    private fun reconcileLocked() {
        val snapshot = store.load() ?: return GameBoostRegistry.publish(GameBoostState.Off)
        if (!store.isSameBoot(snapshot)) {
            if (!store.clear()) {
                GameBoostRegistry.publish(GameBoostState.RecoveryRequired("Stale Game Boost session metadata could not be cleared", snapshot.componentsNeedingRestore().map { it.name }))
            } else GameBoostRegistry.publish(GameBoostState.Off)
            return
        }

        val hardware = runCatching { backend.inspect(snapshot) }.getOrElse {
            GameBoostRegistry.publish(GameBoostState.RecoveryRequired("Could not reconcile Game Boost state: ${it.message}", snapshot.componentsNeedingRestore().map { c -> c.name }))
            return
        }
        val dirty = snapshot.componentsNeedingRestore()
        val alreadyRestored = dirty.isNotEmpty() && dirty.all { it in hardware.restored } &&
            snapshot.applied.none { it in hardware.applied }
        if (alreadyRestored && store.clear()) {
            GameBoostRegistry.publish(GameBoostState.Off)
            return
        }
        if (alreadyRestored) {
            GameBoostRegistry.publish(GameBoostState.RecoveryRequired("Hardware was restored, but Game Boost ownership metadata could not be cleared", dirty.map { it.name }))
            return
        }

        val expectedActive = snapshot.applied - snapshot.thermallyReleased
        if (snapshot.phase == "RECOVERY" || snapshot.phase == "ENABLING" ||
            expectedActive.any { it !in hardware.applied }) {
            GameBoostRegistry.publish(GameBoostState.RecoveryRequired("Saved Game Boost state does not match the device", dirty.map { it.name }))
        } else if (snapshot.phase == "THERMAL_LIMITED") {
            GameBoostRegistry.publish(GameBoostState.ThermalLimited(expectedActive, snapshot.thermallyReleased))
        } else {
            GameBoostRegistry.publish(GameBoostState.Active(snapshot.applied, emptySet()))
        }
    }

    private fun apply(component: GameBoostComponent, snapshot: GameBoostSnapshot): ApplyResult = when (component) {
        GameBoostComponent.CPU_GOVERNOR -> backend.applyCpuGovernor(snapshot)
        GameBoostComponent.CPU_MAX_LOCK -> backend.applyCpuMax(snapshot)
        GameBoostComponent.GPU_GOVERNOR -> backend.applyGpuGovernor(snapshot)
        GameBoostComponent.GPU_MAX_LOCK -> backend.applyGpuMax(snapshot)
        GameBoostComponent.STORAGE_GOVERNOR -> backend.applyStorageGovernor(snapshot)
        GameBoostComponent.STORAGE_MAX_LOCK -> backend.applyStorageMax(snapshot)
    }

    private fun restore(dirty: Set<GameBoostComponent>, snapshot: GameBoostSnapshot): ApplyResult? {
        for (component in RESTORE_ORDER.filter { it in dirty }) {
            val result = when (component) {
                GameBoostComponent.CPU_GOVERNOR -> backend.restoreCpuGovernor(snapshot)
                GameBoostComponent.CPU_MAX_LOCK -> backend.restoreCpuRanges(snapshot)
                GameBoostComponent.GPU_GOVERNOR -> backend.restoreGpuGovernor(snapshot)
                GameBoostComponent.GPU_MAX_LOCK -> backend.restoreGpuRange(snapshot)
                GameBoostComponent.STORAGE_GOVERNOR -> backend.restoreStorageGovernor(snapshot)
                GameBoostComponent.STORAGE_MAX_LOCK -> backend.restoreStorageRange(snapshot)
            }
            if (result !is ApplyResult.Applied) return result
        }
        return null
    }

    private fun abortWithRollback(
        reason: String,
        snapshot: GameBoostSnapshot,
        dirty: Set<GameBoostComponent>
    ): GameBoostTransitionResult.Failure {
        val rollbackFailure = restore(dirty, snapshot)
        if (rollbackFailure != null) {
            return enterRecovery(snapshot, snapshot.componentsNeedingRestore() + dirty, "$reason; rollback failed: ${rollbackFailure.message()}")
        }
        if (!store.clear()) {
            return enterRecovery(snapshot, dirty, "$reason; hardware rollback succeeded but ownership metadata could not be cleared")
        }
        GameBoostRegistry.publish(GameBoostState.Off)
        return GameBoostTransitionResult.Failure(reason)
    }

    private fun enterRecovery(
        snapshot: GameBoostSnapshot,
        dirty: Set<GameBoostComponent>,
        reason: String
    ): GameBoostTransitionResult.Failure {
        val recovery = snapshot.copy(
            phase = "RECOVERY",
            attempted = snapshot.attempted + dirty,
            applied = snapshot.applied + dirty
        )
        val persisted = store.save(recovery)
        val finalReason = if (persisted) reason else "$reason; recovery state could not be persisted"
        GameBoostRegistry.publish(GameBoostState.RecoveryRequired(finalReason, dirty.map { it.name }))
        return GameBoostTransitionResult.Failure(finalReason)
    }

    private fun failBeforeMutation(reason: String): GameBoostTransitionResult.Failure {
        if (store.load() != null) {
            GameBoostRegistry.publish(GameBoostState.RecoveryRequired(reason, emptyList()))
        } else {
            GameBoostRegistry.publish(GameBoostState.Off)
        }
        return GameBoostTransitionResult.Failure(reason)
    }

    private fun isSevereThermal(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true
        val power = appContext.getSystemService(PowerManager::class.java) ?: return true
        return power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    private fun GameBoostComponent.isMaxLock() =
        this == GameBoostComponent.CPU_MAX_LOCK || this == GameBoostComponent.GPU_MAX_LOCK ||
            this == GameBoostComponent.STORAGE_MAX_LOCK
}
