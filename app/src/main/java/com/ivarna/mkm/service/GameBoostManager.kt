package com.ivarna.mkm.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface GameBoostTransitionResult {
    data object Success : GameBoostTransitionResult
    data class Failure(val reason: String) : GameBoostTransitionResult
}

/** Serialized, recoverable global tuning session. */
class GameBoostManager(
    context: Context,
    private val backend: GameBoostTuningBackend = ProductionGameBoostTuningBackend(context)
) {
    private val appContext = context.applicationContext
    private val store = GameBoostSnapshotStore(appContext)
    private val mutex = Mutex()

    init { reconcile() }

    fun currentState(): GameBoostState = GameBoostRegistry.state.value

    fun capabilities(): GameBoostCapabilities = backend.probe()

    suspend fun enable(): GameBoostTransitionResult = mutex.withLock {
        if (GameBoostRegistry.ownsTuning()) return@withLock GameBoostTransitionResult.Failure("Game Boost is already active or recovering")
        val capabilities = runCatching { backend.probe() }.getOrElse {
            return@withLock fail("Capability probe failed: ${it.message}")
        }
        if (!capabilities.boostPossible) return@withLock fail(
            capabilities.components.values.mapNotNull { it.reason }.distinct().joinToString("; ").ifBlank { "No safe Game Boost component is supported" }
        )
        val snapshot = runCatching { backend.captureSnapshot(capabilities) }.getOrElse {
            return@withLock fail("Could not capture the original tuning state: ${it.message}")
        }
        if (!store.save(snapshot)) return@withLock fail("Could not durably save the original tuning state")

        var working = snapshot
        val applied = linkedSetOf<GameBoostComponent>()
        val unsupported = linkedSetOf<GameBoostComponent>()
        val order = listOf(
            GameBoostComponent.CPU_GOVERNOR,
            GameBoostComponent.CPU_MAX_LOCK,
            GameBoostComponent.GPU_GOVERNOR,
            GameBoostComponent.GPU_MAX_LOCK
        )
        for (component in order) {
            val capability = capabilities.components.getValue(component)
            if (!capability.supported) {
                unsupported += component
                continue
            }
            if (component.isMaxLock() && isSevereThermal()) {
                unsupported += component
                working = working.copy(thermallyReleased = working.thermallyReleased + component)
                continue
            }
            GameBoostRegistry.publish(GameBoostState.Enabling(component.name))
            working = working.copy(
                phase = "ENABLING", attempted = working.attempted + component
            )
            store.save(working)
            val result = apply(component, working)
            if (result !is ApplyResult.Applied) {
                val rollback = rollback(applied.toList().asReversed(), working)
                return@withLock if (rollback != null) {
                    working = working.copy(phase = "RECOVERY", attempted = working.attempted, applied = applied)
                    store.save(working)
                    GameBoostRegistry.publish(GameBoostState.RecoveryRequired("${result.message()}; rollback failed: ${rollback.message()}", applied.map { it.name }))
                    GameBoostTransitionResult.Failure("${result.message()}; recovery is required")
                } else {
                    store.clear()
                    GameBoostRegistry.publish(GameBoostState.Off)
                    GameBoostTransitionResult.Failure(result.message())
                }
            }
            applied += component
            working = working.copy(applied = applied.toSet())
            store.save(working)
        }
        val finalState = if (working.thermallyReleased.isNotEmpty()) {
            GameBoostState.ThermalLimited(applied.toSet(), working.thermallyReleased)
        } else GameBoostState.Active(applied.toSet(), unsupported)
        working = working.copy(phase = if (finalState is GameBoostState.ThermalLimited) "THERMAL_LIMITED" else "ACTIVE")
        store.save(working)
        GameBoostRegistry.publish(finalState)
        GameBoostTransitionResult.Success
    }

    suspend fun disable(): GameBoostTransitionResult = mutex.withLock {
        val snapshot = store.load()
        if (snapshot == null) {
            GameBoostRegistry.publish(GameBoostState.Off)
            return@withLock GameBoostTransitionResult.Success
        }
        GameBoostRegistry.publish(GameBoostState.Disabling)
        val toRestore = snapshot.applied.filterNot { it in snapshot.thermallyReleased }.asReversed()
        val failed = restore(toRestore, snapshot)
        if (failed != null) {
            store.save(snapshot.copy(phase = "RECOVERY"))
            GameBoostRegistry.publish(GameBoostState.RecoveryRequired(failed.message(), toRestore.map { it.name }))
            return@withLock GameBoostTransitionResult.Failure(failed.message())
        }
        store.clear()
        GameBoostRegistry.publish(GameBoostState.Off)
        GameBoostTransitionResult.Success
    }

    suspend fun retryRecovery(): GameBoostTransitionResult = disable()

    /** Called by the foreground service when the system reports severe thermal stress. */
    suspend fun onThermalStatus(status: Int): Unit = mutex.withLock {
        if (status < PowerManager.THERMAL_STATUS_SEVERE) return@withLock
        val snapshot = store.load() ?: return@withLock
        val maxComponents = snapshot.applied.filter { it.isMaxLock() && it !in snapshot.thermallyReleased }
        if (maxComponents.isEmpty()) return@withLock
        val released = snapshot.thermallyReleased.toMutableSet()
        maxComponents.forEach { component ->
            val result = when (component) {
                GameBoostComponent.CPU_MAX_LOCK -> backend.restoreCpuRanges(snapshot)
                GameBoostComponent.GPU_MAX_LOCK -> backend.restoreGpuRange(snapshot)
                else -> ApplyResult.Applied("not a max lock", "not a max lock")
            }
            if (result is ApplyResult.Applied) released += component
            else {
                GameBoostRegistry.publish(GameBoostState.RecoveryRequired(result.message(), maxComponents.map { it.name }))
                store.save(snapshot.copy(phase = "RECOVERY"))
                return@withLock
            }
        }
        val updated = snapshot.copy(phase = "THERMAL_LIMITED", thermallyReleased = released)
        store.save(updated)
        GameBoostRegistry.publish(GameBoostState.ThermalLimited(snapshot.applied, released))
    }

    private fun reconcile() {
        val snapshot = store.load() ?: return GameBoostRegistry.publish(GameBoostState.Off)
        if (!store.isSameBoot(snapshot)) {
            store.clear()
            return GameBoostRegistry.publish(GameBoostState.Off)
        }
        val checkable = snapshot.applied - snapshot.thermallyReleased
        val mismatch = checkable.firstOrNull { !runCatching { backend.isApplied(snapshot, it) }.getOrDefault(false) }
        if (mismatch != null || snapshot.phase == "RECOVERY" || snapshot.phase == "ENABLING") {
            GameBoostRegistry.publish(GameBoostState.RecoveryRequired("Saved Game Boost state does not match the device", snapshot.applied.map { it.name }))
        } else if (snapshot.phase == "THERMAL_LIMITED") {
            GameBoostRegistry.publish(GameBoostState.ThermalLimited(snapshot.applied, snapshot.thermallyReleased))
        } else {
            GameBoostRegistry.publish(GameBoostState.Active(snapshot.applied, emptySet()))
        }
    }

    private fun apply(component: GameBoostComponent, snapshot: GameBoostSnapshot): ApplyResult = when (component) {
        GameBoostComponent.CPU_GOVERNOR -> backend.applyCpuGovernor(snapshot)
        GameBoostComponent.CPU_MAX_LOCK -> backend.applyCpuMax(snapshot)
        GameBoostComponent.GPU_GOVERNOR -> backend.applyGpuGovernor(snapshot)
        GameBoostComponent.GPU_MAX_LOCK -> backend.applyGpuMax(snapshot)
    }

    private fun rollback(components: List<GameBoostComponent>, snapshot: GameBoostSnapshot): ApplyResult? {
        for (component in components) {
            val result = restoreOne(component, snapshot)
            if (result !is ApplyResult.Applied) return result
        }
        return null
    }

    private fun restore(components: List<GameBoostComponent>, snapshot: GameBoostSnapshot): ApplyResult? = rollback(components, snapshot)

    private fun restoreOne(component: GameBoostComponent, snapshot: GameBoostSnapshot): ApplyResult = when (component) {
        GameBoostComponent.CPU_GOVERNOR -> backend.restoreCpuGovernor(snapshot)
        GameBoostComponent.CPU_MAX_LOCK -> backend.restoreCpuRanges(snapshot)
        GameBoostComponent.GPU_GOVERNOR -> backend.restoreGpuGovernor(snapshot)
        GameBoostComponent.GPU_MAX_LOCK -> backend.restoreGpuRange(snapshot)
    }

    private fun isSevereThermal(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true
        val power = appContext.getSystemService(PowerManager::class.java) ?: return true
        return power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    private fun fail(reason: String): GameBoostTransitionResult.Failure {
        GameBoostRegistry.publish(GameBoostState.Off)
        return GameBoostTransitionResult.Failure(reason)
    }

    private fun GameBoostComponent.isMaxLock() = this == GameBoostComponent.CPU_MAX_LOCK || this == GameBoostComponent.GPU_MAX_LOCK
}
