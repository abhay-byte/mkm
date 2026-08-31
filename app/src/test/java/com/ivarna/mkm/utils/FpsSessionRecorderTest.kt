package com.ivarna.mkm.utils

import com.ivarna.mkm.data.model.FpsSample
import com.ivarna.mkm.data.model.FpsSource
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FpsSessionRecorderTest {

    @Before
    fun setUp() {
        FpsSessionRecorder.clear()
    }

    @After
    fun tearDown() {
        FpsSessionRecorder.clear()
    }

    @Test
    fun testStartAndStopLifecycle() {
        assertFalse(FpsSessionRecorder.isRecording.value)
        assertNull(FpsSessionRecorder.session.value)

        FpsSessionRecorder.start("MEDIATEK")
        assertTrue(FpsSessionRecorder.isRecording.value)
        assertNotNull(FpsSessionRecorder.session.value)
        assertEquals("MEDIATEK", FpsSessionRecorder.session.value?.platform)
        assertEquals(0, FpsSessionRecorder.session.value?.samples?.size)
        assertNull(FpsSessionRecorder.session.value?.endedAtMs)

        val sample = FpsSample(
            tMs = 1000L,
            fps = 60.0f,
            frameMs = 16.6f,
            pkg = "com.test.app",
            pid = 1234,
            source = FpsSource.MALI_DMA_FENCE,
            events = 120,
            idle = false
        )
        FpsSessionRecorder.add(sample)

        assertEquals(1, FpsSessionRecorder.session.value?.samples?.size)
        assertEquals(60.0f, FpsSessionRecorder.session.value?.samples?.first()?.fps)

        val stoppedSession = FpsSessionRecorder.stop()
        assertFalse(FpsSessionRecorder.isRecording.value)
        assertNotNull(stoppedSession)
        assertEquals(1, stoppedSession?.samples?.size)
        assertNotNull(stoppedSession?.endedAtMs)
    }

    @Test
    fun testMaxSamplesCap300() {
        FpsSessionRecorder.start("SNAPDRAGON")

        for (i in 1..350) {
            val sample = FpsSample(
                tMs = i * 1000L,
                fps = i.toFloat(),
                frameMs = 16.6f,
                pkg = "com.test.app",
                pid = 100,
                source = FpsSource.ADRENO_INFLIGHT,
                events = i,
                idle = false
            )
            FpsSessionRecorder.add(sample)
        }

        val session = FpsSessionRecorder.session.value
        assertNotNull(session)
        assertEquals(300, session?.samples?.size)
        // Earliest sample kept should be index 51 (i.e. fps = 51f)
        assertEquals(51.0f, session?.samples?.first()?.fps)
        assertEquals(350.0f, session?.samples?.last()?.fps)
    }

    @Test
    fun testSetPlatformUpdatesSession() {
        FpsSessionRecorder.start("UNKNOWN")
        assertEquals("UNKNOWN", FpsSessionRecorder.session.value?.platform)

        FpsSessionRecorder.setPlatform("MEDIATEK")
        assertEquals("MEDIATEK", FpsSessionRecorder.session.value?.platform)

        val sample = FpsSample(
            tMs = 2000L,
            fps = 120.0f,
            frameMs = 8.3f,
            pkg = "com.game",
            pid = 999,
            source = FpsSource.MALI_DMA_FENCE,
            events = 240,
            idle = false
        )
        FpsSessionRecorder.add(sample)
        assertEquals("MEDIATEK", FpsSessionRecorder.session.value?.platform)
    }

    @Test
    fun testClearResetsAllState() {
        FpsSessionRecorder.start("MEDIATEK")
        FpsSessionRecorder.add(
            FpsSample(
                tMs = 1000L,
                fps = 60f,
                frameMs = 16.6f,
                pkg = "com.test",
                pid = 1,
                source = FpsSource.FPS_MONITOR
            )
        )
        assertNotNull(FpsSessionRecorder.session.value)
        assertTrue(FpsSessionRecorder.isRecording.value)

        FpsSessionRecorder.clear()
        assertNull(FpsSessionRecorder.session.value)
        assertFalse(FpsSessionRecorder.isRecording.value)
    }
}
