package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.TuningPersistencePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningPersistencePolicyTest {
    @Test fun persistsOnlyVerifiedOrAdjustedStateAfterSuccessfulRefresh() {
        assertTrue(TuningPersistencePolicy.shouldPersist(ApplyResult.Applied("a", "a"), true, true))
        assertTrue(TuningPersistencePolicy.shouldPersist(ApplyResult.Adjusted("a", "b", "clamped"), true, true))
        assertFalse(TuningPersistencePolicy.shouldPersist(ApplyResult.Failed("write failed"), true, true))
        assertFalse(TuningPersistencePolicy.shouldPersist(ApplyResult.Applied("a", "a"), false, true))
        assertFalse(TuningPersistencePolicy.shouldPersist(ApplyResult.Applied("a", "a"), true, false))
    }
}
