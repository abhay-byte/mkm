package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.provider.GpuFrequencyDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuFrequencyDiscoveryTest {
    private fun discover(
        path: String,
        files: Map<String, String>,
        known: List<Long> = emptyList(),
        extra: List<String> = emptyList()
    ) = GpuFrequencyDiscovery.discoverGpuFrequencies(
        path = path,
        knownPoints = known,
        read = { files[it].orEmpty() },
        exists = { files.containsKey(it) },
        extraPaths = extra
    )

    @Test fun mergesStandardAndAdrenoTablesAndFindsLowerOpps() {
        val path = "/sys/class/devfreq/kgsl-3d0"
        val table = "/sys/class/kgsl/kgsl-3d0/freq_table_mhz"
        val pwr = "/sys/class/kgsl/kgsl-3d0/pwrlevels/0/freq"
        val merged = discover(
            path,
            mapOf(
                "$path/available_frequencies" to "315000000 680000000",
                table to "180 257",
                pwr to "430"
            ),
            extra = listOf(table, pwr)
        )

        assertEquals(
            FrequencyCapability.Discrete(listOf(180000000L, 257000000L, 315000000L, 430000000L, 680000000L)),
            merged.capability
        )
        assertTrue(merged.sources.map { it.source }.containsAll(listOf(table, pwr)))
        assertTrue(!merged.knownPointsOnly)
    }

    @Test fun discoversMaliAlternateOppTableAndNormalizesStats() {
        val path = "/sys/class/devfreq/13000000.mali"
        val opp = "$path/opp_table/opp-hz"
        val result = discover(
            path,
            mapOf(opp to "rate=265000000\nrate=1400000000"),
            extra = listOf(opp)
        )

        assertEquals(FrequencyCapability.Discrete(listOf(265000000L, 1400000000L)), result.capability)
        assertEquals(listOf(opp), result.sources.map { it.source })
    }

    @Test fun operatingPointsUseFrequencyColumnAndIgnoreVoltage() {
        val path = "/sys/class/devfreq/gpu"
        val operatingPoints = "$path/opp_table/operating-points"
        val result = discover(
            path,
            mapOf(operatingPoints to "315000000 700000\n680000000 800000"),
            extra = listOf(operatingPoints)
        )

        assertEquals(FrequencyCapability.Discrete(listOf(315000000L, 680000000L)), result.capability)
    }

    @Test fun malformedAndDuplicateValuesAreRemoved() {
        val path = "/sys/class/devfreq/gpu"
        val result = discover(
            path,
            mapOf("$path/time_in_state" to "1200000000 4\nbad 0\n450000000 2\n1200000000 9")
        )

        assertEquals(FrequencyCapability.Discrete(listOf(450000000L, 1200000000L)), result.capability)
    }

    @Test fun knownPointsOnlyFallbackIsMarkedAndNeverExpanded() {
        val path = "/sys/class/devfreq/gpu"
        val result = discover(path, emptyMap(), known = listOf(680000000L, 315000000L, 0L))

        assertTrue(result.knownPointsOnly)
        assertEquals(FrequencyCapability.Discrete(listOf(315000000L, 680000000L)), result.capability)
        assertTrue(result.sources.isEmpty())
    }

    @Test fun noSourceReturnsUnavailable() {
        val result = discover("/sys/class/devfreq/gpu", emptyMap())
        assertTrue(result.capability is FrequencyCapability.Unavailable)
        assertTrue(!result.knownPointsOnly)
    }
}
