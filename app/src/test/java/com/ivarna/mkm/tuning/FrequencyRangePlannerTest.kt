package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.FrequencyRangePlanner
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
}
