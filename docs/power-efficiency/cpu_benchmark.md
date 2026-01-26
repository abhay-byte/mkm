# CPU Efficiency Benchmark

This guide explains how to use the `cpu_efficiency_benchmark.sh` script to analyze your CPU's power characteristics.

## Methodology
The script performs the following steps for **each CPU cluster** (Policy):
1. Detects available frequencies.
2. Iterates from the **Highest** frequency to the **Lowest**.
3. For each frequency step:
   - Locks the CPU frequency.
   - Measures Baseline Power (optional/implicit).
   - Runs a specific mathematical workload (calculating trigonometric functions and square roots on ~300k iterations).
   - Measures **Power Consumption** (Amperes $\times$ Volts) during the run.
   - Calculates a **Score** based on $1000 / \text{Duration}$.
   - Calculates **Efficiency** (Score / Watts).

## How to Run

1. **Root Access Required**:
   ```bash
   su
   ```

2. **Stop Background Services**:
   For accurate power readings, enable **Airplane Mode**, minimize screen brightness (or keep it constant), and close other apps.

3. **Execute the Script**:
   ```bash
   sh /sdcard/scripts/cpu_efficiency_benchmark.sh
   # OR wherever you placed it
   ```

## Output Format
The script generates `cpu_efficiency_results.csv` with the following columns:

| Column | Description |
|--------|-------------|
| `Policy` | The CPU cluster (e.g., policy0, policy4). |
| `Frequency_KHz` | The measured frequency. |
| `Duration_Sec` | Time taken to complete the workload. |
| `Score` | Performance metric ($1000/Duration$). Higher is better. |
| `Power_W` | Average power consumption in Watts. |
| `Efficiency` | Score per Watt. **Peak Efficiency is the goal.** |

## Notes
- **Power Sampling**: The script uses `/sys/class/power_supply` (battery). This includes screen and system power. Differences between frequencies are what matters, but "System Idle Power" impacts the "Low Freq" efficiency curve.
- **Thermal Throttling**: The workload is kept short (~1 sec) to avoid thermal throttling, ensuring we measure the efficiency of the *frequency settings*, not the cooling solution.
