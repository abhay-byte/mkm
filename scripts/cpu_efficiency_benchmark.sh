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

# MAXIMUM INTENSITY multi-phase benchmark to push CPU to 7W+ power draw
# Matrix ops, prime generation, hash computation, FPU stress
# Much heavier workload to fully utilize CPU capabilities
run_benchmark() {
    start_time=$(date +%s.%N)
    
    # Multi-phase MAXIMUM LOAD workload using awk
    awk 'BEGIN {
        # PHASE 1: Large Matrix-like operations (intensive nested loops, FPU)
        sum = 0
        for (i=0; i<300; i++) {
            for (j=0; j<300; j++) {
                for (k=0; k<300; k++) {
                    sum += sqrt(i*j+k+1) * sin(k/10.0) * cos(j/10.0)
                }
            }
        }
        
        # PHASE 2: Extended Prime number generation (heavy integer ops)
        limit = 250000
        for (p=2; p*p<=limit; p++) {
            for (m=p*p; m<=limit; m+=p) {
                sum += m
            }
        }
        
        # PHASE 3: Intensive Hash-like bit operations and mixing
        hash = 12345
        for (i=0; i<150000; i++) {
            hash = xor(hash, lshift(hash, 5))
            hash = xor(hash, rshift(hash, 3))
            hash += i * 2654435761  # Golden ratio prime
            sum += hash % 1000
            # Extra FPU stress
            sum += sqrt(hash % 10000) * sin(i/1000.0)
        }
        
        # PHASE 4: Aggressive Memory-intensive patterns
        for (i=0; i<100000; i++) {
            idx = (i * 1597 + 51749) % 50000
            sum += sqrt(idx+1) * sin(idx/100.0) * cos(idx/200.0)
        }
        
        # PHASE 5: Sustained FPU stress (NEW - push thermal)
        fpusum = 123.456
        for (i=0; i<80000; i++) {
            fpusum = sqrt(fpusum + i) * sin(fpusum/1000.0)
            fpusum += sqrt(i) * cos(i/100.0)
            if (i % 1000 == 0) sum += fpusum
        }
        
        # Prevent optimization
        if (sum < -999999999 || fpusum < -999999999) print sum, fpusum
    }'
    
    end_time=$(date +%s.%N)
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
