package com.ivarna.mkm.tuning

import com.ivarna.mkm.service.GameBoostSnapshotStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBoostBootIdentityTest {
    @Test
    fun matchingBootIdentityIsAcceptedWithBothValues() {
        assertTrue(GameBoostSnapshotStore.matchesBoot(4, "boot-a", 4, "boot-a"))
    }

    @Test
    fun changedBootIdentityIsRejected() {
        assertFalse(GameBoostSnapshotStore.matchesBoot(4, "boot-a", 5, "boot-b"))
    }

    @Test
    fun bootCountFallbackWorksWhenBootIdIsUnavailable() {
        assertTrue(GameBoostSnapshotStore.matchesBoot(4, null, 4, "boot-a"))
        assertFalse(GameBoostSnapshotStore.matchesBoot(4, null, 5, "boot-b"))
    }
}
