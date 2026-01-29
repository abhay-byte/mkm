package com.ivarna.mkm.shell

object CpuUtilizationScripts {
    /**
     * Script to get overall CPU utilization from /proc/stat.
     * Output format: user nice system idle iowait irq softirq steal guest guest_nice
     * Example: cpu  964800 78931 728531 5865986 18001 102980 5440 0 0 0
     */
    fun getCpuStatOverall(): String {
        return "grep '^cpu ' /proc/stat"
    }
    
    /**
     * Script to get per-core CPU utilization from /proc/stat.
     * Output format: multiple lines, one per core
     * Example: 
     * cpu0 100000 1000 50000 500000 1000 10000 500 0 0 0
     * cpu1 110000 1100 51000 510000 1100 11000 510 0 0 0
     */
    fun getCpuStatPerCore(): String {
        return "grep '^cpu[0-9]' /proc/stat"
    }
    
    /**
     * Script to get both overall and per-core CPU stats in one call.
     * More efficient than calling separately.
     */
    fun getCpuStatAll(): String {
        return "grep '^cpu' /proc/stat"
    }
}
