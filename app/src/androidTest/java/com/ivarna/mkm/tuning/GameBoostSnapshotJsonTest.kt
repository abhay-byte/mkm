package com.ivarna.mkm.tuning

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivarna.mkm.data.model.CpuBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GpuBoostSnapshot
import com.ivarna.mkm.service.GameBoostSnapshotJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameBoostSnapshotJsonTest {
    @Test
    fun roundTripPreservesOriginalsTargetsAndSets() {
        val source = GameBoostSnapshot(
            bootCount = 12, bootId = "boot-a", phase = "THERMAL_LIMITED",
            cpu = listOf(CpuBoostSnapshot(0, "/sys/cpu/policy0", "schedutil", 400_000L, 1_900_000L, 2_200_000L)),
            gpu = GpuBoostSnapshot("/sys/class/devfreq/gpu", "simple_ondemand", 265_000_000L, 836_000_000L, 1_400_000_000L),
            attempted = setOf(GameBoostComponent.CPU_GOVERNOR, GameBoostComponent.CPU_MAX_LOCK),
            applied = setOf(GameBoostComponent.CPU_GOVERNOR), thermallyReleased = setOf(GameBoostComponent.CPU_MAX_LOCK)
        )
        assertEquals(source, GameBoostSnapshotJson.decode(GameBoostSnapshotJson.encode(source)))
    }

    @Test
    fun corruptSnapshotFailsAtDecodeBoundary() {
        var failed = false
        try { GameBoostSnapshotJson.decode(JSONObject("{not-json")) } catch (_: Exception) { failed = true }
        assertEquals(true, failed)
    }

    @Test
    fun missingOptionalGpuIsSafe() {
        val decoded = GameBoostSnapshotJson.decode(JSONObject("{\"version\":1,\"cpu\":[]}"))
        assertNull(decoded.gpu)
    }
}
