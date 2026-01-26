package com.ivarna.mkm.shell

object PowerScripts {
    // Reads current and voltage from the first valid power supply
    fun getPowerAndVoltage(): String {
        return """
            for ps in /sys/class/power_supply/*; do
                if [ -e "${"$"}ps/current_now" ] && [ -e "${"$"}ps/voltage_now" ]; then
                    current=${"$"}(cat "${"$"}ps/current_now")
                    voltage=${"$"}(cat "${"$"}ps/voltage_now")
                    current=${"$"}{current#-}
                    echo "${"$"}current ${"$"}voltage"
                    break
                fi
            done
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

get_power_values() {
    for ps in /sys/class/power_supply/*; do
        if [ -e "${"$"}ps/current_now" ] && [ -e "${"$"}ps/voltage_now" ]; then
            current=${"$"}(cat "${"$"}ps/current_now")
            voltage=${"$"}(cat "${"$"}ps/voltage_now")
            current=${"$"}{current#-}
            echo "${"$"}current ${"$"}voltage"
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
