package com.ivarna.mkm.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SysfsTuningExecutorTest {
    @Test fun acceptsOnlySysfsPaths() {
        assertTrue(SysfsTuningExecutor.isSafeSysfsPath("/sys/devices/system/cpu/policy0/scaling_max_freq"))
        assertFalse(SysfsTuningExecutor.isSafeSysfsPath("/data/local/tmp/scaling_max_freq"))
        assertFalse(SysfsTuningExecutor.isSafeSysfsPath("/sys/devices/foo; touch /tmp/pwned"))
    }

    @Test fun rejectsShellSyntaxInValues() {
        assertTrue(SysfsTuningExecutor.isSafeValue("1200000"))
        assertTrue(SysfsTuningExecutor.isSafeValue("schedutil"))
        assertFalse(SysfsTuningExecutor.isSafeValue("1200000;id"))
        assertFalse(SysfsTuningExecutor.isSafeValue("performance governor"))
    }

    @Test fun classifiesExactElevatedAccessProbe() {
        assertEquals(SysfsTuningExecutor.SysfsAccess.READ_WRITE,
            SysfsTuningExecutor.classifyAccessProbe(0, "READ_WRITE"))
        assertEquals(SysfsTuningExecutor.SysfsAccess.READ_ONLY,
            SysfsTuningExecutor.classifyAccessProbe(0, "READ_ONLY"))
        assertEquals(SysfsTuningExecutor.SysfsAccess.UNAVAILABLE,
            SysfsTuningExecutor.classifyAccessProbe(0, "UNAVAILABLE"))
        assertEquals(SysfsTuningExecutor.SysfsAccess.UNKNOWN,
            SysfsTuningExecutor.classifyAccessProbe(-1, ""))
    }
}
