package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.FrequencyCapabilityParser
import org.junit.Assert.assertEquals
import org.junit.Test

class GpuCapabilityTest {
    @Test fun usesStatsTableWhenGpuAvailableFrequenciesIsMissing() {
        val capability = FrequencyCapabilityParser.fromDiscreteSources(
            // The GPU adapter extracts the first time-in-state column before
            // handing it to the generic numeric normalizer.
            sources = listOf(emptyList(), listOf("1200", "450", "0")),
            rangeMin = 300L,
            rangeMax = 1400L,
            knownPoints = listOf(900L)
        )
        assertEquals(FrequencyCapability.Discrete(listOf(450L, 1200L)), capability)
    }

    @Test fun knownGpuPointsAreUsedWithoutFabricatingAnOppList() {
        val capability = FrequencyCapabilityParser.fromDiscreteSources(
            sources = listOf(emptyList(), emptyList()),
            rangeMin = null,
            rangeMax = null,
            knownPoints = listOf(1400L, 450L, 0L, 450L)
        )
        assertEquals(FrequencyCapability.Discrete(listOf(450L, 1400L)), capability)
    }
}
