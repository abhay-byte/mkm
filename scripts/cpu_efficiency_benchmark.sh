#!/system/bin/sh

# CPU Efficiency Benchmark Script
# Iterates through all CPU policies and frequencies to calculate Efficiency (Score/Watt).

OUTPUT_FILE="cpu_efficiency_results.csv"
echo "Policy,Frequency_KHz,Duration_Sec,Score,Power_uW,Efficiency_ScorePerWatt" > $OUTPUT_FILE

# Function to get current power in micro-watts
get_power() {
    # Try generic battery detection
    for ps in /sys/class/power_supply/*; do
        if [ -e "$ps/current_now" ] && [ -e "$ps/voltage_now" ]; then
            current=$(cat "$ps/current_now")
            voltage=$(cat "$ps/voltage_now")
            # Absolute value of current (charging/discharging)
            current=${current#-}
            
            # Simple multiplication for power (uV * uA / 1000000 = W, but keeping in uW for precision or scaling later)
            # Some kernels report current in uA, some in A? usually uA. voltage uV.
            # Power = Current * Voltage
            # Handle potential overflow in shell arithmetic by using awk? 
            # Or just echo and let awk handle it later.
            echo "$current $voltage"
            return
        fi
    done
    echo "0 0"
}

# Simple benchmark: Calculate prime numbers or simple math loop
run_benchmark() {
    start_time=$(date +%s.%N)
    
    # Payload: ~10000 iterations of math. Adjust to take ~0.5s-1s at max freq.
    # Using awk for heavy lifting to ensure CPU usage
    awk 'BEGIN {
        for (i=0; i<300000; i++) {
            x = sqrt(i) * sin(i) * cos(i)
        }
    }'
    
    end_time=$(date +%s.%N)
    # Use awk for float subtraction
    duration=$(awk "BEGIN {print $end_time - $start_time}")
    echo $duration
}

# Detect policies
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -e "$policy" ] || continue
    policy_name=$(basename "$policy")
    echo "Benchmarking $policy_name..."
    
    # Store original settings
    orig_gov=$(cat "$policy/scaling_governor")
    orig_min=$(cat "$policy/scaling_min_freq")
    orig_max=$(cat "$policy/scaling_max_freq")
    
    # Switch to userspace or performance + max_freq locking
    # Best way to lock freq is usually: set min=max=target
    # But usually setting 'performance' and then capping max is enough?
    # Some kernels fight back. Safest for root script:
    # 1. Set governor to performance (to avoid downclocking during test)
    # 2. Set min/max to target freq.
    
    avail_freqs=$(cat "$policy/scaling_available_frequencies")
    
    # Sort freqs from High to Low
    sorted_freqs=$(echo $avail_freqs | tr ' ' '\n' | sort -nr)
    
    for freq in $sorted_freqs; do
        echo "  Testing Frequency: ${freq} KHz"
        
        # Set Frequency
        # Set max first to allow min to be raised if needed, wait, that might fail if min > new_max
        # If new freq is lower than current min, we must lower min first.
        # If new freq is higher than current max, we must raise max first.
        # To be safe: Set Min to lowest possible, Set Max to Target, then Set Min to Target.
        
        lowest_freq=$(echo $avail_freqs | tr ' ' '\n' | sort -n | head -n1)
        
        echo "performance" > "$policy/scaling_governor" 2>/dev/null
        echo $lowest_freq > "$policy/scaling_min_freq" 2>/dev/null
        echo $freq > "$policy/scaling_max_freq" 2>/dev/null
        echo $freq > "$policy/scaling_min_freq" 2>/dev/null
        
        # Verify if freq was applied (approximately)
        cur_freq=$(cat "$policy/scaling_cur_freq")
        if [ "$cur_freq" != "$freq" ]; then
             # Try userspace governor if performance locked failed? 
             # Or just record the actual frequency used
             echo "    Warning: Requested $freq, running at $cur_freq"
        fi
        
        # Measure Power (Baseline - optional, but we want load power)
        # Actually we want 'Active Power - Idle Power' ideally, but 'Total Power' acts as a proxy for System Efficiency at that load.
        # The prompt asks for "wattage x score". 
        
        # Start measurement in background? No, shell is single threaded mostly. 
        # We'll measure immediately before/after or 'during' is hard without parallel.
        # Approximation: Measure power, Run Bench, Measure Power. Average.
        
        p1=$(get_power)
        duration=$(run_benchmark)
        p2=$(get_power)
        
        # Extract current/voltage
        c1=$(echo $p1 | cut -d' ' -f1); v1=$(echo $p1 | cut -d' ' -f2)
        c2=$(echo $p2 | cut -d' ' -f1); v2=$(echo $p2 | cut -d' ' -f2)
        
        avg_current=$(( (c1 + c2) / 2 ))
        avg_voltage=$(( (v1 + v2) / 2 ))
        power_uW=$(( avg_current * avg_voltage ))
        
        # Score calculation: 1 / duration (inverse time is speed)
        # or fixed operations (e.g. 1000) / duration.
        # Let's say Score = 1000 / duration
        score=$(awk "BEGIN {print 1000 / $duration}")
        
        # Efficiency = Score / Power (in Watts)
        # Power in Watts = power_uW / 1e12 ? No, uA * uV = pW (pico). 
        # Wait:
        # Current (uA) * Voltage (uV) = 10^-6 * 10^-6 = 10^-12 (pW)
        # usually files are uA and uV.
        # So divide by 1,000,000,000,000 to get Watts.
        # OR:
        # Check units. power_supply usually uA, uV.
        # W = (uA * uV) / 10^12.
        
        # Let's just log Raw Power (uA*uV) and let data_analysis.md handle headers.
        # Actually prompt asks for "Score/Wattage".
        # Let's print Power in Watts in the CSV for convenience.
        power_W=$(awk "BEGIN {print $power_uW / 1000000000000}")
        
        eff=$(awk "BEGIN {print $score / ($power_W + 0.00001)}") # avoid div by zero
        
        echo "$policy_name,$cur_freq,$duration,$score,$power_W,$eff" >> $OUTPUT_FILE
        
        # Cooldown?
        sleep 1
    done
    
    # Restore original
    echo $orig_min > "$policy/scaling_min_freq"
    echo $orig_max > "$policy/scaling_max_freq"
    echo $orig_gov > "$policy/scaling_governor" 
done

echo "Done. Results saved to $OUTPUT_FILE"
