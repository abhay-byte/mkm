package com.ivarna.mkm.shell

object UfsScripts {
    /**
     * Script to find the UFS devfreq directory containing 'governor' and 'available_governors'.
     * Returns the directory path.
     */
    fun findUfsDir(): String {
        return """
            # Known paths for Mediatek (Xiaomi/Poco etc)
            # 1. Deeply nested devfreq path
            if [ -e "/sys/devices/platform/soc/112b0000.ufshci/devfreq/112b0000.ufshci/governor" ]; then
                echo "/sys/devices/platform/soc/112b0000.ufshci/devfreq/112b0000.ufshci"
                exit 0
            fi
            
            # 2. Direct devfreq path
            if [ -e "/sys/class/devfreq/112b0000.ufshci/governor" ]; then
                echo "/sys/class/devfreq/112b0000.ufshci"
                exit 0
            fi

            # 3. Dynamic search
            # Try /sys/class/devfreq first
            for path in /sys/class/devfreq/*ufshci*; do
                if [ -e "${'$'}path/governor" ]; then
                    echo "${'$'}path"
                    exit 0
                fi
            done
            
            # 4. Search platform devices
            # Find all platform ufshci directories
            for p in $(find /sys/devices/platform -name "*ufshci*" 2>/dev/null); do
                # Check for direct governor
                if [ -e "${'$'}p/governor" ]; then
                    echo "${'$'}p"
                    exit 0
                fi
                
                # Check for devfreq subdir
                if [ -d "${'$'}p/devfreq" ]; then
                     # Check wildcard inside devfreq
                     for sub in "${'$'}p/devfreq/"*; do
                         if [ -e "${'$'}sub/governor" ]; then
                             echo "${'$'}sub"
                             exit 0
                         fi
                     done
                fi
            done
            
            echo "Not found"
            exit 1
        """.trimIndent()
    }

    /**
     * Script to get UFS governor info given the directory path.
     * Output format:
     * GOV=<current>
     * AVAIL=<space separated list>
     */
    fun getUfsInfo(path: String): String {
        return """
            echo "GOV=$(cat "$path/governor")"
            echo "AVAIL=$(cat "$path/available_governors")"
            echo "FREQ=$(cat "$path/cur_freq" 2>/dev/null || cat "$path/target_freq" 2>/dev/null)"
            echo "MIN_FREQ=$(cat "$path/min_freq" 2>/dev/null)"
            echo "MAX_FREQ=$(cat "$path/max_freq" 2>/dev/null)"
            echo "AVAIL_FREQ=$(cat "$path/available_frequencies" 2>/dev/null)"
        """.trimIndent()
    }

    /**
     * Script to set the UFS governor.
     */
    fun setGovernor(path: String, governor: String): String {
        return "echo \"$governor\" > \"$path/governor\""
    }

    fun setMinFreq(path: String, freq: String): String {
        return "echo \"$freq\" > \"$path/min_freq\""
    }

    fun setMaxFreq(path: String, freq: String): String {
        return "echo \"$freq\" > \"$path/max_freq\""
    }
}
