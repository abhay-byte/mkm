package com.ivarna.mkm.shell

object DevfreqScripts {

    /**
     * Script to find the main RAM/Interconnect devfreq directory.
     * Searches for common controller names like 'bimc', 'ddr', 'm4m', 'mtk-dvfsrc'.
     */
    fun findDevfreqDir(): String {
        return """
            # Known specific paths
            possible_names="mtk-dvfsrc-devfreq soc:qcom,bimc soc:qcom,ddr_bw soc:qcom,m4m soc:qcom,cpu-cpu-llcc-bw"
            
            for name in ${'$'}possible_names; do
                if [ -e "/sys/class/devfreq/${'$'}name/governor" ]; then
                    echo "/sys/class/devfreq/${'$'}name"
                    exit 0
                fi
            done
            
            # General search in /sys/class/devfreq/
            # Look for controllers that look like memory controllers
            for path in /sys/class/devfreq/*; do
                if [ -d "${'$'}path" ]; then
                    name=${'$'}(basename "${'$'}path")
                    if echo "${'$'}name" | grep -qE "bimc|ddr|m4m|dvfsrc|llcc"; then
                         if [ -e "${'$'}path/governor" ]; then
                            echo "${'$'}path"
                            exit 0
                        fi
                    fi
                fi
            done
            
            # If nothing found, just return the first one that has available_governors and frequencies,
            # assuming it might be the main one or at least *a* tunable one.
            # (Fallback)
             for path in /sys/class/devfreq/*; do
                if [ -e "${'$'}path/available_frequencies" ] && [ -e "${'$'}path/available_governors" ]; then
                    echo "${'$'}path"
                    exit 0
                fi
            done
            
            echo "Not found"
            exit 1
        """.trimIndent()
    }

    /**
     * Script to get Devfreq info.
     */
    fun getDevfreqInfo(path: String): String {
        return """
            echo "GOV=$(cat "$path/governor" 2>/dev/null)"
            echo "AVAIL=$(cat "$path/available_governors" 2>/dev/null)"
            echo "FREQ=$(cat "$path/cur_freq" 2>/dev/null || cat "$path/target_freq" 2>/dev/null)"
            echo "AVAIL_FREQ=$(cat "$path/available_frequencies" 2>/dev/null)"
        """.trimIndent()
    }

    fun setGovernor(path: String, governor: String): String {
        return "echo \"$governor\" > \"$path/governor\""
    }

    /**
     * Sets frequency. Often requires userspace governor, but some kernels allow it directly.
     * We will try to set userspace governor first in the UI/ViewModel if needed, 
     * but this script just writes to the freq file.
     * Note: 'userspace/set_freq' is the standard for the 'userspace' governor.
     */
    fun setFreq(path: String, freq: String): String {
        return """
            if [ -w "$path/userspace/set_freq" ]; then
                echo "$freq" > "$path/userspace/set_freq"
            elif [ -w "$path/ondemand/set_freq" ]; then
                 echo "$freq" > "$path/ondemand/set_freq"
            else
                 # Try writing to min/max if direct set is not available, though this is less precise
                 echo "$freq" > "$path/min_freq"
                 echo "$freq" > "$path/max_freq"
            fi
        """.trimIndent()
    }
}
