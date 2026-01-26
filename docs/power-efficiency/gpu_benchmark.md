# GPU Efficiency Benchmark

This guide explains how to use the `gpu_efficiency_benchmark.sh` script to find the optimal GPU clock speed.

## Methodology
Unlike the CPU script, the GPU script **does not generate its own load** (as shell-based 3D rendering is not feasible). 
**You must run a graphic-intensive application** (like a game, 3DMark Wild Life, or GFXBench) in the background or split-screen mode while the script runs.

The script:
1. Detects your GPU model and path (Adreno, Mali, etc.).
2. Locks the GPU to each available frequency (High $\to$ Low).
3. Measures **Power Consumption** while your 3D app is running.
4. Records **GPU Utilization** (Busy %) to ensure the load is sufficient.

## How to Run

1. **Prepare the Device**:
   - Charge battery > 50%.
   - **Open your 3D Benchmark app** (e.g., 3DMark).
   - Configure it to run a "Stress Test" or "Loop" mode.
   
2. **Start the Script (in a floating Terminal or via ADB)**:
   ```bash
   su
   sh /sdcard/scripts/gpu_efficiency_benchmark.sh
   ```

3. **Start the 3D Load**:
   - When the script prompts "Press ENTER", switch to your 3D app, start the benchmark, then quickly switch back and press ENTER (or run via PC ADB so you don't need to switch).

## Output Format
The script generates `gpu_efficiency_results.csv`:

| Column | Description |
|--------|-------------|
| `Frequency_Hz` | The locked GPU frequency. |
| `Utilization` | GPU Load percentage (0-100). If this drops below 90%, the GPU is underutilized (CPU bottleneck or VSync), providing invalid data. |
| `Power_W` | Power consumption in Watts. |
| `Score` | *Limited to manual correlation*. Since FPS cannot be reliably captured via shell scripts across all devices, efficient tuning relies on comparing Power vs Frequency. |

> **Tip**: If you see Power dropping significantly but Frame Rate (visually) remaining smooth/acceptable, that is your efficiency win.
