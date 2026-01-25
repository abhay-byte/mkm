package com.ivarna.mkm.shell

object GpuScripts {

    /**
     * Script to find the GPU devfreq directory.
     * Returns the directory path.
     */
    fun findGpuPath(): String {
        return """
            # 1. Search devfreq devices for common GPU names
            if [ -d "/sys/class/devfreq" ]; then
                # Priority 1: Adreno (kgsl)
                for path in /sys/class/devfreq/*; do
                     if echo "${'$'}path" | grep -q "kgsl"; then
                         echo "${'$'}path"
                         exit 0
                     fi
                done
                
                # Priority 2: Mali
                for path in /sys/class/devfreq/*; do
                     if echo "${'$'}path" | grep -q "mali"; then
                         echo "${'$'}path"
                         exit 0
                     fi
                done
                
                # Priority 3: Generic GPU
                 for path in /sys/class/devfreq/*; do
                     if echo "${'$'}path" | grep -q "gpu"; then
                         echo "${'$'}path"
                         exit 0
                     fi
                done
                
                 # Priority 4: PowerVR
                 for path in /sys/class/devfreq/*; do
                     if echo "${'$'}path" | grep -q "pvr" || echo "${'$'}path" | grep -q "rgx"; then
                         echo "${'$'}path"
                         exit 0
                     fi
                done
            fi
            
            # 2. Check legacy Adreno path
            if [ -d "/sys/class/kgsl/kgsl-3d0/devfreq" ]; then
                echo "/sys/class/kgsl/kgsl-3d0/devfreq"
                exit 0
            fi
            
            # 3. Check MediaTek specific fallback
            if [ -d "/sys/class/misc/mali0/device/devfreq/13000000.mali" ]; then
                echo "/sys/class/misc/mali0/device/devfreq/13000000.mali"
                exit 0
            fi

            echo "Not found"
            exit 1
        """.trimIndent()
    }

    /**
     * Script to get GPU info given the devfreq path.
     * Output format:
     * GOV=<current>
     * AVAIL=<space separated list>
     * CUR_FREQ=<freq>
     * MIN_FREQ=<freq>
     * MAX_FREQ=<freq>
     * TARGET_FREQ=<freq>
     * AVAIL_FREQ=<space separated list>
     * LOAD=<percent 0-100>
     */
    fun getGpuInfo(path: String): String {
        return """
            echo "GOV=$(cat "$path/governor" 2>/dev/null)"
            echo "AVAIL=$(cat "$path/available_governors" 2>/dev/null)"
            
            # Frequencies
            echo "CUR_FREQ=$(cat "$path/cur_freq" 2>/dev/null)"
            echo "MIN_FREQ=$(cat "$path/min_freq" 2>/dev/null)"
            echo "MAX_FREQ=$(cat "$path/max_freq" 2>/dev/null)"
            echo "TARGET_FREQ=$(cat "$path/target_freq" 2>/dev/null)"
            echo "AVAIL_FREQ=$(cat "$path/available_frequencies" 2>/dev/null)"
            
            # Load Calculation
            LOAD=0
            # Try standard load file
            if [ -f "$path/load" ]; then
                LOAD=$(cat "$path/load" | tr -d '%')
            else
                # Try vendor specific
                if echo "$path" | grep -q "mali"; then
                    if [ -f "/sys/kernel/ged/hal/gpu_utilization" ]; then
                         LOAD=$(cat "/sys/kernel/ged/hal/gpu_utilization")
                         # Some malis return 0-1, others 0-100. Assume if > 1 it is percent.
                         # Logic handled in provider if needed, but here let's just output raw
                    fi
                elif echo "$path" | grep -q "kgsl"; then
                     # Adreno gpubusy
                     # Usually at ../gpubusy relative to devfreq
                     PARENT=$(dirname "$path")
                     if [ -f "${'$'}PARENT/gpubusy" ]; then
                         read busy total < "${'$'}PARENT/gpubusy"
                         if [ "${'$'}total" -gt 0 ]; then
                             LOAD=${'$'}(( 100 * busy / total ))
                         fi
                     fi
                fi
            fi
            echo "LOAD=${'$'}LOAD"
        """.trimIndent()
    }

    fun setGovernor(path: String, governor: String): String {
        return "echo \"$governor\" > \"$path/governor\""
    }

    fun setFrequency(path: String, freq: String, type: String): String {
        // type should be "min_freq" or "max_freq" or "target_freq"
        return "echo \"$freq\" > \"$path/$type\""
    }
}
