package com.ivarna.mkm.shell

object ShellScripts {

    /**
     * Script to set CPU frequency scaling governor and frequencies.
     */
    fun setCpuConfig(cluster: Int, governor: String, minFreq: String, maxFreq: String): String {
        return """
            echo "$governor" > /sys/devices/system/cpu/cpufreq/policy$cluster/scaling_governor
            echo "$minFreq" > /sys/devices/system/cpu/cpufreq/policy$cluster/scaling_min_freq
            echo "$maxFreq" > /sys/devices/system/cpu/cpufreq/policy$cluster/scaling_max_freq
        """.trimIndent()
    }

    /**
     * Script to create and enable a swap file.
     */
    fun createSwap(path: String, sizeMb: Int): String {
        return """
            set -e # fail on error
            
            # Disable existing swap if any
            swapoff "$path" 2>/dev/null || true
            
            # Create file
            dd if=/dev/zero of="$path" bs=1M count=$sizeMb
            
            # Set permissions
            chmod 600 "$path"
            
            # Setup swap
            mkswap "$path"
            
            # Enable swap
            swapon "$path"
            
            # Set swappiness (optional, default 60)
            echo 60 > /proc/sys/vm/swappiness
        """.trimIndent()
    }

    /**
     * Script to disable swap.
     */
    fun disableSwap(path: String): String {
        return "set -e; swapoff \"$path\""
    }

    /**
     * Script to check if swap is active.
     */
    fun checkSwap(): String {
        return "free -m | grep -i swap"
    }

    /**
     * Script to remove/delete a swap file.
     */
    fun removeSwap(path: String): String {
        return """
            set -e # fail on error
            swapoff "$path" 2>/dev/null || true
            rm -f "$path"
        """.trimIndent()
    }



    /**
     * Script to get current CPU info.
     */
    fun getCpuInfo(): String {
        return "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq"
    }
}
