package com.ivarna.mkm.service

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.CpuBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostCapabilities
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostComponentCapability
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostState
import com.ivarna.mkm.data.model.GpuBoostSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameBoostManagerTest {
    private lateinit var context: Context

    @Before
    fun clearSession() {
        context = ApplicationProvider.getApplicationContext()
        GameBoostSnapshotStore(context).clear()
        // Creating the manager reconciles the cleared store to Off.
        GameBoostManager(context, FakeBackend()).currentState()
    }

    @Test
    fun allSupportedComponentsEnableAndDisableExactly() {
        runBlocking {
        val backend = FakeBackend()
        val manager = GameBoostManager(context, backend)
        assertTrue(manager.enable() is GameBoostTransitionResult.Success)
        assertTrue(GameBoostRegistry.state.value is GameBoostState.Active)
        assertTrue(manager.disable() is GameBoostTransitionResult.Success)
        assertTrue(GameBoostRegistry.state.value is GameBoostState.Off)
        assertEquals(
            listOf("cpuGovernor", "cpuMax", "gpuGovernor", "gpuMax", "gpuRangeRestore", "gpuGovernorRestore", "cpuRangeRestore", "cpuGovernorRestore"),
            backend.calls
        )
        }
    }

    @Test
    fun unsupportedComponentIsSkippedWithoutFailingSession() {
        runBlocking {
        val backend = FakeBackend().apply { unsupported += GameBoostComponent.GPU_MAX_LOCK }
        val manager = GameBoostManager(context, backend)
        assertTrue(manager.enable() is GameBoostTransitionResult.Success)
        val state = GameBoostRegistry.state.value as GameBoostState.Active
        assertTrue(GameBoostComponent.GPU_MAX_LOCK in state.unsupported)
        assertTrue("gpuMax" !in backend.calls)
        manager.disable()
        }
    }

    @Test
    fun adjustedApplyRollsBackAndLeavesOff() {
        runBlocking {
        val backend = FakeBackend().apply { cpuMaxResult = ApplyResult.Adjusted("requested", "actual", "clamped") }
        val manager = GameBoostManager(context, backend)
        assertTrue(manager.enable() is GameBoostTransitionResult.Failure)
        assertTrue(GameBoostRegistry.state.value is GameBoostState.Off)
        assertTrue("cpuGovernorRestore" in backend.calls)
        assertEquals(null, GameBoostSnapshotStore(context).load())
        }
    }

    @Test
    fun rollbackFailureRequiresRecoveryThenRetryRestores() {
        runBlocking {
        val backend = FakeBackend().apply {
            cpuMaxResult = ApplyResult.Failed("write failed")
            cpuGovernorRestoreResult = ApplyResult.Failed("restore failed")
        }
        val manager = GameBoostManager(context, backend)
        assertTrue(manager.enable() is GameBoostTransitionResult.Failure)
        assertTrue(GameBoostRegistry.state.value is GameBoostState.RecoveryRequired)
        backend.cpuGovernorRestoreResult = ApplyResult.Applied("restore", "restore")
        assertTrue(manager.retryRecovery() is GameBoostTransitionResult.Success)
        assertTrue(GameBoostRegistry.state.value is GameBoostState.Off)
        assertEquals(null, GameBoostSnapshotStore(context).load())
        }
    }

    @Test
    fun restoreFailureRetainsSnapshotUntilRetrySucceeds() {
        runBlocking {
            val backend = FakeBackend().apply { cpuGovernorRestoreResult = ApplyResult.Failed("restore failed") }
            val manager = GameBoostManager(context, backend)
            assertTrue(manager.enable() is GameBoostTransitionResult.Success)

            assertTrue(manager.disable() is GameBoostTransitionResult.Failure)
            assertTrue(GameBoostRegistry.state.value is GameBoostState.RecoveryRequired)
            assertTrue(GameBoostSnapshotStore(context).load() != null)

            backend.cpuGovernorRestoreResult = ApplyResult.Applied("restore", "restore")
            assertTrue(manager.retryRecovery() is GameBoostTransitionResult.Success)
            assertEquals(null, GameBoostSnapshotStore(context).load())
        }
    }

    @Test
    fun severeThermalReleasesOnlyMaximumLocksWithoutRelocking() {
        runBlocking {
            val backend = FakeBackend()
            val manager = GameBoostManager(context, backend)
            assertTrue(manager.enable() is GameBoostTransitionResult.Success)
            manager.onThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
            val state = GameBoostRegistry.state.value as GameBoostState.ThermalLimited
            assertEquals(setOf(GameBoostComponent.CPU_GOVERNOR, GameBoostComponent.GPU_GOVERNOR), state.stillApplied)
            assertEquals(setOf(GameBoostComponent.CPU_MAX_LOCK, GameBoostComponent.GPU_MAX_LOCK), state.released)
            assertTrue("cpuMax" !in backend.calls.drop(4))
            assertTrue(manager.disable() is GameBoostTransitionResult.Success)
        }
    }

    @Test
    fun sameBootSnapshotRehydratesActiveState() {
        val backend = FakeBackend()
        val snapshot = backend.captureSnapshot(backend.probe()).copy(
            bootCount = GameBoostSnapshotStore(context).currentIdentity().first,
            bootId = GameBoostSnapshotStore(context).currentIdentity().second,
            phase = "ACTIVE",
            applied = setOf(GameBoostComponent.CPU_GOVERNOR)
        )
        assumeTrue(snapshot.bootCount != null || snapshot.bootId != null)
        GameBoostSnapshotStore(context).save(snapshot)

        GameBoostManager(context, backend)

        val state = GameBoostRegistry.state.value as GameBoostState.Active
        assertEquals(setOf(GameBoostComponent.CPU_GOVERNOR), state.applied)
        assertTrue(GameBoostSnapshotStore(context).load() != null)
    }

    @Test
    fun sameBootAlreadyRestoredSnapshotIsClearedSafely() {
        val backend = FakeBackend().apply { restored = true }
        val identity = GameBoostSnapshotStore(context).currentIdentity()
        val snapshot = backend.captureSnapshot(backend.probe()).copy(
            bootCount = identity.first,
            bootId = identity.second,
            phase = "ACTIVE",
            attempted = setOf(GameBoostComponent.CPU_GOVERNOR),
            applied = setOf(GameBoostComponent.CPU_GOVERNOR)
        )
        assumeTrue(snapshot.bootCount != null || snapshot.bootId != null)
        GameBoostSnapshotStore(context).save(snapshot)

        GameBoostManager(context, backend)

        assertTrue(GameBoostRegistry.state.value is GameBoostState.Off)
        assertEquals(null, GameBoostSnapshotStore(context).load())
    }

    @Test
    fun newBootSnapshotIsDiscardedWithoutReapplying() {
        val backend = FakeBackend()
        val identity = GameBoostSnapshotStore(context).currentIdentity()
        val snapshot = backend.captureSnapshot(backend.probe()).copy(
            bootCount = identity.first?.plus(1),
            bootId = "different-boot",
            phase = "ACTIVE",
            applied = setOf(GameBoostComponent.CPU_GOVERNOR)
        )
        GameBoostSnapshotStore(context).save(snapshot)

        GameBoostManager(context, backend)

        assertTrue(GameBoostRegistry.state.value is GameBoostState.Off)
        assertEquals(null, GameBoostSnapshotStore(context).load())
        assertFalse(backend.calls.any { it.startsWith("apply") })
    }

    private class FakeBackend : GameBoostTuningBackend {
        val calls = mutableListOf<String>()
        val unsupported = mutableSetOf<GameBoostComponent>()
        var cpuMaxResult: ApplyResult = ApplyResult.Applied("cpu max", "cpu max")
        var cpuGovernorRestoreResult: ApplyResult = ApplyResult.Applied("restore", "restore")
        var restored: Boolean = false

        override fun probe() = GameBoostCapabilities(
            GameBoostComponent.values().associateWith { component ->
                GameBoostComponentCapability(component, component !in unsupported)
            }, boostPossible = true, maxLocksThermallySupported = true
        )

        override fun captureSnapshot(capabilities: GameBoostCapabilities) = GameBoostSnapshot(
            cpu = listOf(CpuBoostSnapshot(0, "/sys/cpu/policy0", "schedutil", 400L, 1_000L, 1_200L)),
            gpu = GpuBoostSnapshot("/sys/gpu", "simple_ondemand", 100L, 800L, 1_000L)
        )

        override fun applyCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult { calls += "cpuGovernor"; return ApplyResult.Applied("cpu", "cpu") }
        override fun applyCpuMax(snapshot: GameBoostSnapshot): ApplyResult { calls += "cpuMax"; return cpuMaxResult }
        override fun applyGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult { calls += "gpuGovernor"; return ApplyResult.Applied("gpu", "gpu") }
        override fun applyGpuMax(snapshot: GameBoostSnapshot): ApplyResult { calls += "gpuMax"; return ApplyResult.Applied("gpuMax", "gpuMax") }
        override fun restoreCpuGovernor(snapshot: GameBoostSnapshot): ApplyResult { calls += "cpuGovernorRestore"; return cpuGovernorRestoreResult }
        override fun restoreCpuRanges(snapshot: GameBoostSnapshot): ApplyResult { calls += "cpuRangeRestore"; return ApplyResult.Applied("range", "range") }
        override fun restoreGpuGovernor(snapshot: GameBoostSnapshot): ApplyResult { calls += "gpuGovernorRestore"; return ApplyResult.Applied("gpu", "gpu") }
        override fun restoreGpuRange(snapshot: GameBoostSnapshot): ApplyResult { calls += "gpuRangeRestore"; return ApplyResult.Applied("range", "range") }
        override fun isApplied(snapshot: GameBoostSnapshot, component: GameBoostComponent) = true
        override fun isRestored(snapshot: GameBoostSnapshot, component: GameBoostComponent) = restored
    }
}
