package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.TuningPersistencePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuTuningStateTest {
    @Test fun verifiedGpuMutationCanPersistOnlyAfterRefresh() {
        assertTrue(TuningPersistencePolicy.shouldPersist(
            ApplyResult.Applied("min=265000000", "min=265000000"),
            bootEnabled = true,
            stateRefreshed = true
        ))
        assertTrue(TuningPersistencePolicy.shouldPersist(
            ApplyResult.Adjusted("min=265000000", "min=284000000", "driver clamp"),
            bootEnabled = true,
            stateRefreshed = true
        ))
        assertFalse(TuningPersistencePolicy.shouldPersist(
            ApplyResult.Failed("GPU write failed"),
            bootEnabled = true,
            stateRefreshed = true
        ))
        assertFalse(TuningPersistencePolicy.shouldPersist(
            ApplyResult.Applied("min=265000000", "min=265000000"),
            bootEnabled = true,
            stateRefreshed = false
        ))
    }
}
