package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.FrequencyRangePlanner
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.RangeReadback
import com.ivarna.mkm.data.model.RangeTransactionResult
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
        val result = RangeWriteTransaction.execute(
            plan = plan,
            write = { step ->
                writes += step.value
                writes.size == 1
            },
            readImmediate = { RangeReadback(1200L, 2800L) },
            readFinal = { RangeReadback(1200L, 1200L) }
        )

        assertEquals(listOf(1200L, 1200L), writes)
        assertTrue(result is RangeTransactionResult.Failed)
        assertEquals(false, (result as RangeTransactionResult.Failed).failedStep?.isMin)
    }

    @Test fun finalReadbackExposesKernelClamp() {
        val plan = FrequencyRangePlanner.forMax(300L, 2800L, 1200L)
        val result = RangeWriteTransaction.execute(
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
