package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.TuningMutationCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuTuningStateTest {
    @Test fun pollingWaitsUntilCpuMutationAndRefreshFinish() = runBlocking {
        val coordinator = TuningMutationCoordinator()
        val pending = mutableListOf<String?>()
        val events = mutableListOf<String>()
        val releaseMutation = CompletableDeferred<Unit>()

        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withMutation("cpu-policy-4-max", { pending += it }) {
                events += "mutation-start"
                releaseMutation.await()
                events += "mutation-finished"
            }
        }
        val polling = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withObservation {
                events += "polling"
            }
        }

        assertEquals(listOf("mutation-start"), events)
        assertFalse(polling.isCompleted)
        releaseMutation.complete(Unit)
        mutation.await()
        polling.await()

        assertEquals(listOf("mutation-start", "mutation-finished", "polling"), events)
        assertEquals(listOf("cpu-policy-4-max", null), pending)
    }

    @Test fun failedCpuMutationStillClearsPendingControl() = runBlocking {
        val coordinator = TuningMutationCoordinator()
        val pending = mutableListOf<String?>()

        try {
            coordinator.withMutation("cpu-policy-0-min", { pending += it }) {
                error("write failed")
            }
        } catch (error: IllegalStateException) {
            assertEquals("write failed", error.message)
        }

        assertEquals(listOf("cpu-policy-0-min", null), pending)
        assertTrue(coordinator.withObservation { true })
    }
}
