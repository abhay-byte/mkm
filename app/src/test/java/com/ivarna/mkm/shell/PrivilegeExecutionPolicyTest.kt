package com.ivarna.mkm.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivilegeExecutionPolicyTest {
    @Test fun elevatedWritesPreferRootWhenBothBackendsExist() {
        assertEquals(
            listOf(ShellManager.ExecutionBackend.ROOT),
            PrivilegeExecutionPolicy.order(ShellManager.PrivilegeRequirement.ELEVATED_SYSFS, true, true)
        )
    }

    @Test fun elevatedReadsAndWritesUseShizukuWhenRootIsUnavailable() {
        assertEquals(
            listOf(ShellManager.ExecutionBackend.SHIZUKU),
            PrivilegeExecutionPolicy.order(ShellManager.PrivilegeRequirement.ELEVATED_SYSFS, false, true)
        )
    }

    @Test fun elevatedOperationsHaveNoUnprivilegedFallback() {
        assertEquals(
            emptyList<ShellManager.ExecutionBackend>(),
            PrivilegeExecutionPolicy.order(ShellManager.PrivilegeRequirement.ELEVATED_SYSFS, false, false)
        )
    }
}
