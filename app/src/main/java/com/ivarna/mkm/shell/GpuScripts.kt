package com.ivarna.mkm.shell

object GpuScripts {

    /** Lists real vendor frequency-table nodes for the discovery adapter. */
    fun discoverGpuFrequencyPaths(path: String): String {
        return """
            for root in "$path" "/sys/class/kgsl/kgsl-3d0" "/sys/class/misc/mali0/device"; do
                if [ -e "${'$'}root" ]; then
                    find "${'$'}root" -type f \( \
                        -name 'available_frequencies' -o -name 'time_in_state' -o \
                        -name 'freq_table_mhz' -o -name 'gpu_available_frequencies' -o \
                        -name 'frequency_table' -o -name 'operating-points' -o \
                        -name 'opp-hz' -o -name 'opp-frequency' -o -name 'freq' -o \
                        -name 'frequency' -o -name 'clock_mhz' \
                    \) -print 2>/dev/null
                fi
            done
        """.trimIndent()
    }

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
                     if [ -d "${'$'}path" ] && { [ -e "${'$'}path/governor" ] || [ -e "${'$'}path/cur_freq" ] || [ -e "${'$'}path/available_frequencies" ]; }; then
                       if echo "${'$'}path" | grep -q "kgsl"; then
                         echo "${'$'}path"
                         exit 0
                       fi
                     fi
                done
                
                # Priority 2: Mali
                for path in /sys/class/devfreq/*; do
                     if [ -d "${'$'}path" ] && { [ -e "${'$'}path/governor" ] || [ -e "${'$'}path/cur_freq" ] || [ -e "${'$'}path/available_frequencies" ]; } && echo "${'$'}path" | grep -q "mali"; then
                         echo "${'$'}path"
                         exit 0
                     fi
                done
                
                # Priority 3: Generic GPU
                 for path in /sys/class/devfreq/*; do
                     if [ -d "${'$'}path" ] && { [ -e "${'$'}path/governor" ] || [ -e "${'$'}path/cur_freq" ] || [ -e "${'$'}path/available_frequencies" ]; } && echo "${'$'}path" | grep -q "gpu"; then
                         echo "${'$'}path"
                         exit 0
                     fi
                done
                
                 # Priority 4: PowerVR
                for path in /sys/class/devfreq/*; do
                     if [ -d "${'$'}path" ] && { [ -e "${'$'}path/governor" ] || [ -e "${'$'}path/cur_freq" ] || [ -e "${'$'}path/available_frequencies" ]; } && { echo "${'$'}path" | grep -q "pvr" || echo "${'$'}path" | grep -q "rgx"; }; then
                         echo "${'$'}path"
                         exit 0
                     fi
                done
            fi
            
            # 2. Check legacy Adreno path
            if [ -d "/sys/class/kgsl/kgsl-3d0/devfreq" ] && { [ -e "/sys/class/kgsl/kgsl-3d0/devfreq/governor" ] || [ -e "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" ] || [ -e "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies" ]; }; then
                echo "/sys/class/kgsl/kgsl-3d0/devfreq"
                exit 0
            fi
            
            # 3. Check MediaTek specific fallback
            if [ -d "/sys/class/misc/mali0/device/devfreq/13000000.mali" ] && { [ -e "/sys/class/misc/mali0/device/devfreq/13000000.mali/governor" ] || [ -e "/sys/class/misc/mali0/device/devfreq/13000000.mali/cur_freq" ] || [ -e "/sys/class/misc/mali0/device/devfreq/13000000.mali/available_frequencies" ]; }; then
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
     * FREQ_AVAILABLE=<0|1>
     */
    fun getGpuInfo(path: String): String {
        return """
            echo "GOV=$(cat "$path/governor" 2>/dev/null)"
            echo "AVAIL=$(cat "$path/available_governors" 2>/dev/null)"

            # Frequencies - try primary path, then several fallbacks
            # (Adreno kernels may expose freq via kgsl-3d0/gpuclk or
            # /sys/class/devfreq/<dev>/cur_freq; many are root-only on modern Android)
            CUR_FREQ=""
            FREQ_AVAILABLE=0
            BASE=$(basename "$path")
            SOC_ADDR=$(echo "${'$'}BASE" | cut -d. -f1)
            for f in "$path/cur_freq" "$path/cur_frequency" "$(dirname "$path")/cur_freq" "/sys/class/kgsl/kgsl-3d0/gpuclk" "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" "/sys/class/devfreq/kgsl-3d0/cur_freq" "/sys/class/devfreq/${'$'}BASE/cur_freq" "/sys/devices/platform/soc/${'$'}SOC_ADDR/kgsl/kgsl-3d0/gpuclk" "/sys/devices/platform/soc/${'$'}SOC_ADDR/kgsl/kgsl-3d0/devfreq/cur_freq"; do
                v=$(cat "${'$'}f" 2>/dev/null | tr -d '[:space:]')
                if [ -n "${'$'}v" ] && [ "${'$'}v" != "0" ]; then
                    CUR_FREQ="${'$'}v"
                    FREQ_AVAILABLE=1
                    break
                fi
            done
            echo "CUR_FREQ=${'$'}CUR_FREQ"
            echo "FREQ_AVAILABLE=${'$'}FREQ_AVAILABLE"

            echo "MIN_FREQ=$(cat "$path/min_freq" 2>/dev/null)"
            echo "MAX_FREQ=$(cat "$path/max_freq" 2>/dev/null)"
            echo "TARGET_FREQ=$(cat "$path/target_freq" 2>/dev/null)"
            # Prefer the real OPP table. If unavailable, expose only points that
            # the driver actually reports; never invent compatibility values.
            AVAIL_FREQ=$(cat "$path/available_frequencies" 2>/dev/null | tr '\n' ' ')
            if [ -z "${'$'}AVAIL_FREQ" ] && [ -e "$path/stats/time_in_state" ]; then
                AVAIL_FREQ=$(awk '{print ${'$'}1}' "$path/stats/time_in_state" 2>/dev/null | tr '\n' ' ')
            fi
            if [ -z "${'$'}AVAIL_FREQ" ] && [ -e "$path/time_in_state" ]; then
                AVAIL_FREQ=$(awk '{print ${'$'}1}' "$path/time_in_state" 2>/dev/null | tr '\n' ' ')
            fi
            if [ -z "${'$'}AVAIL_FREQ" ]; then
                for v in "${'$'}CUR_FREQ" "$(cat "$path/min_freq" 2>/dev/null)" "$(cat "$path/max_freq" 2>/dev/null)" "$(cat "$path/target_freq" 2>/dev/null)"; do
                    case " ${'$'}AVAIL_FREQ " in *" ${'$'}v "*) ;; *) [ -n "${'$'}v" ] && [ "${'$'}v" != "0" ] && AVAIL_FREQ="${'$'}AVAIL_FREQ ${'$'}v" ;; esac
                done
            fi
            echo "AVAIL_FREQ=${'$'}AVAIL_FREQ"

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
                    fi
                elif echo "$path" | grep -q "kgsl"; then
                     # Adreno gpubusy - usually at ../gpubusy relative to devfreq
                     PARENT=$(dirname "$path")
                     if [ -f "${'$'}PARENT/gpubusy" ]; then
                         read busy total < "${'$'}PARENT/gpubusy"
                         if [ "${'$'}total" -gt 0 ]; then
                             LOAD=$((${'$'}busy * 100 / ${'$'}total))
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
