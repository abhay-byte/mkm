package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.FrequencyRangePlanner
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.RangeReadback
import com.ivarna.mkm.data.model.RangeTransactionResult
import com.ivarna.mkm.data.model.RangeWriteStep
import com.ivarna.mkm.data.model.RangeWriteTransaction
import com.ivarna.mkm.data.model.ScalarReadbackVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyRangePlannerTest {
    @Test fun loweringMaxBelowCurrentMinLowersMinFirst() {
        val plan = FrequencyRangePlanner.forMax(1800L, 2800L, 1200L)
        assertEquals(listOf(true, false), plan.steps.map { it.isMin })
        assertEquals(listOf(1200L, 1200L), plan.steps.map { it.value })
    }

    @Test fun loweringMaxWithLowerCurrentMinKeepsMinValid() {
        val plan = FrequencyRangePlanner.forMax(300L, 2800L, 1200L)
        assertEquals(300L, plan.min)
        assertEquals(1200L, plan.max)
        assertTrue(plan.min <= plan.max)
        assertEquals(listOf(false), plan.steps.map { it.isMin })
    }

    @Test fun raisingMinAboveCurrentMaxRaisesMaxFirst() {
        val plan = FrequencyRangePlanner.forMin(300L, 1200L, 1800L)
        assertEquals(listOf(false, true), plan.steps.map { it.isMin })
        assertEquals(listOf(1800L, 1800L), plan.steps.map { it.value })
    }

    @Test fun exactRangeAndEqualRangeAreValid() {
        assertEquals(2, FrequencyRangePlanner.plan(300L, 2800L, 500L, 1400L).steps.size)
        assertTrue(FrequencyRangePlanner.plan(300L, 2800L, 1200L, 1200L).steps.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvertedDesiredRange() {
        FrequencyRangePlanner.plan(300L, 2800L, 1800L, 1200L)
    }

    @Test fun secondWriteFailureIsReportedAfterFirstWrite() {
        val plan = FrequencyRangePlanner.forMax(1800L, 2800L, 1200L)
        val writes = mutableListOf<Long>()
        var state = RangeReadback(1800L, 2800L)
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(1800L, 2800L),
            plan = plan,
            write = { step ->
                writes += step.value
                if (writes.size == 2) {
                    false
                } else {
                    state = if (step.isMin) state.copy(min = step.value) else state.copy(max = step.value)
                    true
                }
            },
            readImmediate = { state },
            readFinal = { state }
        )

        assertEquals(listOf(1200L, 1200L, 1800L), writes)
        assertTrue(result is RangeTransactionResult.FailedRolledBack)
        assertEquals(RangeReadback(1800L, 2800L), (result as RangeTransactionResult.FailedRolledBack).restored)
    }

    @Test fun rollbackFailureReportsChangedState() {
        val plan = FrequencyRangePlanner.forMax(1800L, 2800L, 1200L)
        val writes = mutableListOf<Long>()
        var state = RangeReadback(1800L, 2800L)
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(1800L, 2800L),
            plan = plan,
            write = { step ->
                writes += step.value
                if (writes.size == 2 || writes.size == 3) {
                    false
                } else {
                    state = if (step.isMin) state.copy(min = step.value) else state.copy(max = step.value)
                    true
                }
            },
            readImmediate = { state },
            readFinal = { state }
        )

        assertTrue(result is RangeTransactionResult.FailedStateChanged)
        assertEquals(RangeReadback(1200L, 2800L), (result as RangeTransactionResult.FailedStateChanged).actual)
    }

    @Test fun verificationFailureRollsBackToOriginalRange() {
        val plan = FrequencyRangePlanner.forMax(300L, 2800L, 1200L)
        var state = RangeReadback(300L, 2800L)
        var finalReads = 0
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(300L, 2800L),
            plan = plan,
            write = { step ->
                state = if (step.isMin) state.copy(min = step.value) else state.copy(max = step.value)
                true
            },
            readImmediate = { state },
            readFinal = {
                if (finalReads++ == 0) error("vendor read-back failure")
                state
            }
        )

        assertTrue(result is RangeTransactionResult.FailedRolledBack)
        assertEquals(RangeReadback(300L, 2800L), (result as RangeTransactionResult.FailedRolledBack).restored)
    }

    @Test fun unreadableRangeStillAttemptsOriginalBounds() {
        val plan = FrequencyRangePlanner.forMax(300L, 2800L, 1200L)
        var state = RangeReadback(300L, 2800L)
        var reads = 0
        val writes = mutableListOf<RangeWriteStep>()
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(300L, 2800L),
            plan = plan,
            write = { step ->
                writes += step
                state = if (step.isMin) state.copy(min = step.value) else state.copy(max = step.value)
                true
            },
            readImmediate = {
                if (reads++ == 0) error("temporary read failure")
                state
            },
            readFinal = { state }
        )

        assertTrue(result is RangeTransactionResult.FailedRolledBack)
        assertEquals(RangeReadback(300L, 2800L), (result as RangeTransactionResult.FailedRolledBack).restored)
        assertTrue(writes.any { it.isMin && it.value == 300L })
        assertTrue(writes.any { !it.isMin && it.value == 2800L })
    }

    @Test fun clampedRollbackReportsActualChangedState() {
        val plan = FrequencyRangePlanner.forMax(1800L, 2800L, 1200L)
        var state = RangeReadback(1800L, 2800L)
        var writes = 0
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(1800L, 2800L),
            plan = plan,
            write = { step ->
                writes++
                when {
                    writes == 2 -> false
                    writes == 3 && step.isMin -> {
                        state = state.copy(min = 1700L)
                        true
                    }
                    else -> {
                        state = if (step.isMin) state.copy(min = step.value) else state.copy(max = step.value)
                        true
                    }
                }
            },
            readImmediate = { state },
            readFinal = { state }
        )

        assertTrue(result is RangeTransactionResult.FailedStateChanged)
        assertEquals(RangeReadback(1700L, 2800L), (result as RangeTransactionResult.FailedStateChanged).actual)
    }

    @Test fun vendorOverrideBeforeRollbackIsReportedAsChanged() {
        val plan = FrequencyRangePlanner.forMax(300L, 2800L, 1200L)
        var state = RangeReadback(300L, 2800L)
        var finalReads = 0
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(300L, 2800L),
            plan = plan,
            write = { step ->
                state = if (step.isMin) state.copy(min = step.value) else state.copy(max = step.value)
                true
            },
            readImmediate = { state },
            readFinal = {
                if (finalReads++ == 0) {
                    state = RangeReadback(400L, 1200L)
                    error("vendor override detected")
                }
                state
            }
        )

        assertTrue(result is RangeTransactionResult.FailedStateChanged)
        assertEquals(RangeReadback(400L, 2800L), (result as RangeTransactionResult.FailedStateChanged).actual)
    }

    @Test fun finalReadbackExposesKernelClamp() {
        val plan = FrequencyRangePlanner.forMax(300L, 2800L, 1200L)
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(300L, 2800L),
            plan = plan,
            write = { true },
            readImmediate = { RangeReadback(300L, 1200L) },
            readFinal = { RangeReadback(300L, 1000L) }
        )

        val verified = result as RangeTransactionResult.Verified
        assertEquals(RangeReadback(300L, 1000L), verified.final)
    }

    @Test fun finalReadbackExposesPostWriteOverride() {
        val plan = FrequencyRangePlanner.forMax(300L, 2800L, 1200L)
        val result = RangeWriteTransaction.execute(
            original = RangeReadback(300L, 2800L),
            plan = plan,
            write = { true },
            readImmediate = { RangeReadback(300L, 1200L) },
            readFinal = { RangeReadback(400L, 1200L) }
        )

        val verified = result as RangeTransactionResult.Verified
        assertEquals(RangeReadback(400L, 1200L), verified.final)
    }

    @Test fun missingScalarReadbackIsFailureNotAdjustment() {
        val result = ScalarReadbackVerifier.verify("performance", "", "driver adjusted")
        assertTrue(result is ApplyResult.Failed)
    }
}
