package com.ivarna.mkm.data.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuPolicyMappingTest {
    @Test fun parsesNonContiguousAffectedCpuLists() {
        assertEquals(listOf(0, 1, 3, 7, 8), CpuPolicyMapping.parseCpuList("0-1, 3 7-8"))
    }

    @Test fun removesDuplicatesAndKeepsPolicyMembershipSorted() {
        assertEquals(listOf(0, 1, 2, 4, 6), CpuPolicyMapping.parseCpuList("2,0-2,6,4,6"))
    }
}
