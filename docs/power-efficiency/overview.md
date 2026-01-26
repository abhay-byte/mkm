# Power Efficiency Overview

Finding the optimal balance between performance and battery life requires benchmarking "Efficiency" rather than just raw speed.

## The Metric: Efficiency (Score per Watt)
We define efficiency as:
$$ \text{Efficiency} = \frac{\text{Performance Score}}{\text{Power Consumption (Watts)}} $$

- **Performance Score**: Operations per second, Frames per second (FPS), or inverse duration ($1/t$).
- **Power Consumption**: The power drawn by the device during the workload (Voltage $\times$ Current).

## The Goal: "The Sweet Spot"
Processors (CPU/GPU) do not scale linearly.
- At low frequencies, static power leakage dominates, making them inefficient.
- At high frequencies, voltage requirements scale exponentially, causing massive power usage for diminishing performance returns.
- **The "Sweet Spot"** is the frequency where the **Efficiency Score** is maximized. Locking your custom kernel or governor to this max frequency ensures the best battery life for heavy tasks.

## Components
1. **[CPU Benchmark](cpu_benchmark.md)**: Automated script to test CPU Core efficiency.
2. **[GPU Benchmark](gpu_benchmark.md)**: Automated script to test GPU frequency efficiency.
3. **[Power Monitor](power_component.md)**: Technical details on reading wattage from the Kernel/App.
4. **[Data Analysis](data_analysis.md)**: How to process the CSV results and generate the Power Efficiency Graph.
