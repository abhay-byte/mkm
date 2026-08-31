package com.ivarna.mkm.shell

object PowerScripts {
    /**
     * Reads signed current (µA), voltage (µV), and capacity from a **battery-class**
     * power supply only.
     *
     * Preference order:
     * 1. Well-known names: battery, bms, BAT0/BAT1/BATTERY
     * 2. Any supply with type=Battery
     *
     * Does **not** fall through to usb/dc/ac/wireless when battery current is 0
     * (full charge) — that was the source of absurd multi-kW readings.
     * Current sign is preserved (kernel convention varies; callers must not use
     * it for UI polarity — use Android charging state instead).
     */
    fun getPowerAndVoltage(): String {
        return """
            current=0
            voltage=0
            capacity=0
            found=0

            try_read() {
                ps="${"$"}1"
                if [ -e "${"$"}ps/current_now" ] && [ -e "${"$"}ps/voltage_now" ]; then
                    c=${"$"}(cat "${"$"}ps/current_now" 2>/dev/null)
                    v=${"$"}(cat "${"$"}ps/voltage_now" 2>/dev/null)
                    # Accept battery node even when current is 0 (full / idle).
                    if [ -n "${"$"}v" ] && [ "${"$"}v" != "0" ]; then
                        current=${"$"}c
                        voltage=${"$"}v
                        if [ -e "${"$"}ps/capacity" ]; then
                            capacity=${"$"}(cat "${"$"}ps/capacity" 2>/dev/null || echo 0)
                        fi
                        return 0
                    fi
                fi
                return 1
            }

            # Pass 1: well-known battery / BMS names
            for name in battery bms BAT0 BAT1 BATTERY Battery; do
                ps="/sys/class/power_supply/${"$"}name"
                if [ -d "${"$"}ps" ] && try_read "${"$"}ps"; then
                    found=1
                    break
                fi
            done

            # Pass 2: any supply with type=Battery
            if [ "${"$"}found" -eq 0 ]; then
                for ps in /sys/class/power_supply/*; do
                    [ -d "${"$"}ps" ] || continue
                    [ -e "${"$"}ps/type" ] || continue
                    t=${"$"}(cat "${"$"}ps/type" 2>/dev/null)
                    if [ "${"$"}t" = "Battery" ] && try_read "${"$"}ps"; then
                        found=1
                        break
                    fi
                done
            fi

            # Capacity fallback if still 0
            if [ "${"$"}capacity" -eq 0 ] 2>/dev/null; then
                for ps in /sys/class/power_supply/*; do
                    if [ -e "${"$"}ps/capacity" ]; then
                        capacity=${"$"}(cat "${"$"}ps/capacity" 2>/dev/null || echo 0)
                        [ "${"$"}capacity" != "0" ] && break
                    fi
                done
            fi

            echo "${"$"}current ${"$"}voltage ${"$"}capacity"
        """.trimIndent()
    }

    private val GPU_BENCHMARK_SCRIPT = """
#!/system/bin/sh
OUTPUT_FILE="gpu_efficiency_results.csv"
echo "Frequency_Hz,Duration_Sec,Score,Power_W,Efficiency_ScorePerWatt" > ${"$"}OUTPUT_FILE

find_gpu_path() {
    paths="/sys/class/kgsl/kgsl-3d0/devfreq /sys/class/misc/mali0/device/devfreq/13000000.mali /sys/kernel/gpu /sys/class/devfreq/b00000.qcom,kgsl-3d0"
    for p in ${"$"}paths; do
        if [ -d "${"$"}p" ]; then
            echo "${"$"}p"
            return
        fi
    done
    ls -d /sys/class/devfreq/* 2>/dev/null | head -n1
}

GPU_PATH=${"$"}(find_gpu_path)
[ -z "${"$"}GPU_PATH" ] && exit 1
echo "Found GPU at: ${"$"}GPU_PATH"

# Battery-first power read (same policy as getPowerAndVoltage). Keep |current| for magnitude.
get_power_values() {
    try_read() {
        ps="${"$"}1"
        if [ -e "${"$"}ps/current_now" ] && [ -e "${"$"}ps/voltage_now" ]; then
            c=${"$"}(cat "${"$"}ps/current_now" 2>/dev/null)
            v=${"$"}(cat "${"$"}ps/voltage_now" 2>/dev/null)
            if [ -n "${"$"}v" ] && [ "${"$"}v" != "0" ]; then
                c=${"$"}{c#-}
                echo "${"$"}c ${"$"}v"
                return 0
            fi
        fi
        return 1
    }
    for name in battery bms BAT0 BAT1 BATTERY Battery; do
        ps="/sys/class/power_supply/${"$"}name"
        if [ -d "${"$"}ps" ] && try_read "${"$"}ps"; then
            return
        fi
    done
    for ps in /sys/class/power_supply/*; do
        [ -d "${"$"}ps" ] || continue
        [ -e "${"$"}ps/type" ] || continue
        t=${"$"}(cat "${"$"}ps/type" 2>/dev/null)
        if [ "${"$"}t" = "Battery" ] && try_read "${"$"}ps"; then
            return
        fi
    done
    echo "0 0"
}

read _unused

avail_freqs=${"$"}(cat "${"$"}GPU_PATH/available_frequencies")
sorted_freqs=${"$"}(echo ${"$"}avail_freqs | tr ' ' '\n' | sort -nr)

orig_gov=${"$"}(cat "${"$"}GPU_PATH/governor")
orig_min=${"$"}(cat "${"$"}GPU_PATH/min_freq")
orig_max=${"$"}(cat "${"$"}GPU_PATH/max_freq")

echo "userspace" > "${"$"}GPU_PATH/governor" 2>/dev/null || echo "performance" > "${"$"}GPU_PATH/governor" 2>/dev/null

for freq in ${"$"}sorted_freqs; do
    echo "Testing GPU Frequency: ${"$"}{freq} Hz"
    echo ${"$"}freq > "${"$"}GPU_PATH/userspace/set_freq" 2>/dev/null
    echo ${"$"}freq > "${"$"}GPU_PATH/min_freq" 2>/dev/null
    echo ${"$"}freq > "${"$"}GPU_PATH/max_freq" 2>/dev/null
    sleep 0.5
    
    cur_freq=${"$"}(cat "${"$"}GPU_PATH/cur_freq" 2>/dev/null || cat "${"$"}GPU_PATH/scaling_cur_freq")
    
    pv1=${"$"}(get_power_values)
    c1=${"$"}(echo ${"$"}pv1 | awk '{print ${"$"}1}')
    v1=${"$"}(echo ${"$"}pv1 | awk '{print ${"$"}2}')
    
    sleep 1
    
    pv2=${"$"}(get_power_values)
    c2=${"$"}(echo ${"$"}pv2 | awk '{print ${"$"}1}')
    v2=${"$"}(echo ${"$"}pv2 | awk '{print ${"$"}2}')
    
    vals=${"$"}(awk "BEGIN {
        c1=${"$"}c1; v1=${"$"}v1; c2=${"$"}c2; v2=${"$"}v2;
        avg_c = (c1 + c2) / 2;
        avg_v = (v1 + v2) / 2;
        power_pW = avg_c * avg_v;
        power_W = power_pW / 1000000000000;
        print power_W
    }")
    
    power_W=${"$"}(echo ${"$"}vals)
    
    util=${"$"}(cat "${"$"}GPU_PATH/gpu_busy" 2>/dev/null || cat "${"$"}GPU_PATH/load" 2>/dev/null || echo "0")
    
    echo "${"$"}cur_freq,1.0,${"$"}util,${"$"}power_W,0" >> ${"$"}OUTPUT_FILE
done

echo ${"$"}orig_min > "${"$"}GPU_PATH/min_freq"
echo ${"$"}orig_max > "${"$"}GPU_PATH/max_freq"
echo ${"$"}orig_gov > "${"$"}GPU_PATH/governor"
"""

    fun getGpuScriptContent(): String = GPU_BENCHMARK_SCRIPT

    fun executeGpuBenchmark(scriptPath: String): String {
        return "echo \"\" | sh $scriptPath"
    }
    
    fun checkCpuBenchmarkExists(): String = "" // Deprecated usage
    fun checkGpuBenchmarkExists(): String = "" // Deprecated usage
}
