package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuPolicyState
import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.model.GpuTuningCapabilities
import com.ivarna.mkm.data.provider.GameBoostProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBoostProbeTest {
    private fun cpuCluster(id: Int, performance: Boolean = true, writable: Boolean = true, capability: FrequencyCapability = FrequencyCapability.Discrete(listOf(400_000L, 1_900_000L, 2_200_000L))) =
        CpuCluster(
            id = id, governor = "schedutil", rawMinFreq = "400000", rawMaxFreq = "1900000",
            availableGovernors = if (performance) listOf("schedutil", "performance") else listOf("schedutil"),
            governorWritable = writable, minWritable = writable, maxWritable = writable,
            policyState = CpuPolicyState(id, "/sys/devices/system/cpu/cpufreq/policy$id", emptyList(), emptyList(), "schedutil", emptyList(), 400_000L, 1_900_000L, 400_000L, 2_200_000L, capability)
        )

    @Test
    fun usesHighestDiscreteCpuAndGpuTargets() {
        val cpu = com.ivarna.mkm.data.model.CpuStatus(clusters = listOf(cpuCluster(0)))
        val gpu = GpuStatus(
            governor = "simple_ondemand", availableGovernors = listOf("simple_ondemand", "performance"),
            rawMinFreq = "265000000", rawMaxFreq = "836000000", governorWritable = true, minWritable = true, maxWritable = true,
            tuningCapabilities = GpuTuningCapabilities("/sys/class/devfreq/gpu", emptyList(), FrequencyCapability.Discrete(listOf(265_000_000L, 1_400_000_000L)), true, true, true, false, false),
            frequencyCapability = FrequencyCapability.Discrete(listOf(265_000_000L, 1_400_000_000L)), frequencyTableComplete = true
        )
        val result = GameBoostProbe.buildCapabilities(cpu, gpu, maxLocksThermallySupported = true)
        assertEquals("2200000", result.components[GameBoostComponent.CPU_MAX_LOCK]?.target)
        assertEquals("1400000000", result.components[GameBoostComponent.GPU_MAX_LOCK]?.target)
        assertTrue(result.components.getValue(GameBoostComponent.CPU_MAX_LOCK).supported)
        assertTrue(result.components.getValue(GameBoostComponent.GPU_MAX_LOCK).supported)
        assertEquals(null, result.components.getValue(GameBoostComponent.CPU_MAX_LOCK).reason)
    }

    @Test
    fun globalCpuGovernorIsUnsupportedWhenOnePolicyCannotPerform() {
        val cpu = com.ivarna.mkm.data.model.CpuStatus(clusters = listOf(cpuCluster(0), cpuCluster(4, performance = false, writable = false)))
        val result = GameBoostProbe.buildCapabilities(cpu, GpuStatus(), maxLocksThermallySupported = true)
        assertFalse(result.components.getValue(GameBoostComponent.CPU_GOVERNOR).supported)
        assertFalse(result.components.getValue(GameBoostComponent.GPU_GOVERNOR).supported)
        assertTrue(result.boostPossible.not())
    }

    @Test
    fun oldApiDisablesOnlyMaximumLocks() {
        val cpu = com.ivarna.mkm.data.model.CpuStatus(clusters = listOf(cpuCluster(0)))
        val result = GameBoostProbe.buildCapabilities(cpu, GpuStatus(), maxLocksThermallySupported = false)
        assertTrue(result.components.getValue(GameBoostComponent.CPU_GOVERNOR).supported)
        assertFalse(result.components.getValue(GameBoostComponent.CPU_MAX_LOCK).supported)
    }
}
