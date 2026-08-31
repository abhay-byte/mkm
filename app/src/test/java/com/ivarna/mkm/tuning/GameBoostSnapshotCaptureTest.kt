package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuPolicyState
import com.ivarna.mkm.data.model.CpuStatus
import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.model.GpuTuningCapabilities
import com.ivarna.mkm.service.GameBoostSnapshotCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameBoostSnapshotCaptureTest {
    private val cpuStatus = CpuStatus(
        clusters = listOf(
            CpuCluster(
                id = 0,
                governor = "schedutil",
                rawMinFreq = "400000",
                rawMaxFreq = "1900000",
                policyState = CpuPolicyState(
                    policyId = 0,
                    path = "/sys/devices/system/cpu/cpufreq/policy0",
                    affectedCpus = emptyList(),
                    relatedCpus = emptyList(),
                    governor = "schedutil",
                    supportedGovernors = listOf("schedutil", "performance"),
                    minFreq = 400_000L,
                    maxFreq = 1_900_000L,
                    hwMinFreq = 400_000L,
                    hwMaxFreq = 2_200_000L,
                    frequencyCapability = FrequencyCapability.Discrete(listOf(400_000L, 2_200_000L))
                )
            )
        )
    )

    private val readOnlyGpu = GpuStatus(
        governor = "simple_ondemand",
        rawMinFreq = "",
        rawMaxFreq = "",
        tuningCapabilities = GpuTuningCapabilities(
            path = "/sys/class/devfreq/gpu",
            governors = listOf("simple_ondemand"),
            frequencies = FrequencyCapability.Unavailable("read-only"),
            governorWritable = false,
            minWritable = false,
            maxWritable = false,
            targetWritable = false,
            requiresRoot = true
        )
    )

    @Test
    fun cpuOnlyCaptureDoesNotRequireDetectedReadOnlyGpu() {
        val cpu = GameBoostSnapshotCapture.cpu(cpuStatus, governorNeeded = true, rangeNeeded = false)
        val gpu = GameBoostSnapshotCapture.gpu(readOnlyGpu, governorNeeded = false, rangeNeeded = false)

        assertEquals("schedutil", cpu.single().governor)
        assertNull(cpu.single().minFreq)
        assertNull(gpu)
    }

    @Test
    fun governorOnlyCaptureLeavesFrequencyFieldsOptional() {
        val gpu = GameBoostSnapshotCapture.gpu(readOnlyGpu.copy(
            governor = "schedutil",
            tuningCapabilities = readOnlyGpu.tuningCapabilities?.copy(governorWritable = true, governors = listOf("schedutil", "performance"))
        ), governorNeeded = true, rangeNeeded = false)

        assertEquals("schedutil", gpu?.governor)
        assertNull(gpu?.minFreq)
        assertNull(gpu?.maxFreq)
        assertNull(gpu?.targetFreq)
    }
}
