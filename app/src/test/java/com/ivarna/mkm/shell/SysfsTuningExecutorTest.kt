package com.ivarna.mkm.shell

import org.junit.Assert.assertFalse
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
}
