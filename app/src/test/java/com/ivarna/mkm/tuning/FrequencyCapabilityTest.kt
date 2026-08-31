package com.ivarna.mkm.tuning

import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.FrequencyCapabilityParser
import com.ivarna.mkm.data.model.SelectionOrdering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyCapabilityTest {
    @Test fun normalizesUnorderedDuplicateMalformedAndZeroValues() {
        assertEquals(listOf(300L, 800L, 1200L), FrequencyCapabilityParser.normalize(listOf("1200 0 bad 300", "800", "300")))
    }

    @Test fun usesTimeInStateBeforeRangeAndDoesNotInventValues() {
        val capability = FrequencyCapabilityParser.fromDiscreteSources(
            sources = listOf(emptyList(), listOf("800", "1200")),
            rangeMin = 300L,
            rangeMax = 2800L
        )
        assertEquals(FrequencyCapability.Discrete(listOf(800L, 1200L)), capability)
    }

    @Test fun exposesRangeOrUnavailableWhenNoDiscreteTableExists() {
        assertEquals(
            FrequencyCapability.Range(300L, 2800L),
            FrequencyCapabilityParser.fromDiscreteSources(emptyList(), 300L, 2800L)
        )
        assertTrue(FrequencyCapabilityParser.fromDiscreteSources(emptyList(), null, null) is FrequencyCapability.Unavailable)
    }

    @Test fun ordersNumericSelectionsWithoutChangingTextLabels() {
        assertEquals(
            listOf("300", "1200", "2800"),
            SelectionOrdering.order(listOf("2800", "300", "1200", "300"))
        )
        assertEquals(
            listOf("schedutil", "performance"),
            SelectionOrdering.order(listOf("schedutil", "performance"))
        )
    }
}
