package com.ivarna.mkm.shell

object CpuScripts {
    /**
     * Script to set the governor for a specific CPU policy (cluster).
     */
    fun setGovernor(policyId: Int, governor: String): String {
        return "echo \"$governor\" > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor"
    }

    /**
     * Script to set the minimum frequency for a specific CPU policy (cluster).
     * Verify frequency availability before calling this.
     */
    fun setMinFreq(policyId: Int, freqKhz: String): String {
        return "echo \"$freqKhz\" > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_min_freq"
    }

    /**
     * Script to set the maximum frequency for a specific CPU policy (cluster).
     * Verify frequency availability before calling this.
     */
    fun setMaxFreq(policyId: Int, freqKhz: String): String {
        return "echo \"$freqKhz\" > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq"
    }
}
