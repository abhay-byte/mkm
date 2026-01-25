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
            
            # Clean up old file to ensure fresh attributes
            rm -f "$path"
            
            # Create empty file
            touch "$path"
            
            # Try to pin file (required for F2FS swap, ignore failure on other FS)
            # +P = Project quota / Pinned (F2FS uses this for contiguous alloc)
            chattr +P "$path" 2>/dev/null || true
            
            # Allocate file (dd works better with pinned files than fallocate on some Android versions)
            dd if=/dev/zero of="$path" bs=1M count=$sizeMb
            
            # Set permissions
            chmod 600 "$path"
            
            # Setup swap
            mkswap "$path"
            
            # Enable swap
            if ! swapon "$path"; then
                echo "Direct swapon failed. Attempting loop device fallback..."
                # Clean up loop devices (optional: simplistic, might kill other loops)
                # losetup -D 2>/dev/null || true 
                
                # Find free loop device
                LOOP_DEV=$(losetup -f)
                if [ -z "${'$'}LOOP_DEV" ]; then
                     # If find failed, try creating one? (Requires verifying if /dev/loop exists)
                     echo "No free loop device found."
                     exit 1
                fi
                
                # Attach file to loop device
                losetup "${'$'}LOOP_DEV" "$path"
                
                # Initialize swap on loop device
                mkswap "${'$'}LOOP_DEV"
                
                # Enable swap on loop device
                swapon "${'$'}LOOP_DEV"
            fi
            
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
            
            # Check if this file is used by any loop device
            LOOP_DEV=$(losetup -j "$path" | cut -d: -f1)
            
            if [ ! -z "${'$'}LOOP_DEV" ]; then
                # It's mounted as a loop, stop swap on the LOOP DEVICE first
                swapoff "${'$'}LOOP_DEV" 2>/dev/null || true
                # Detach the loop device
                losetup -d "${'$'}LOOP_DEV" 2>/dev/null || true
            else
                 # Normal file swapoff
                 swapoff "$path" 2>/dev/null || true
            fi

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
