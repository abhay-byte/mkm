# GPU Metrics Extraction (Adreno & Mali)

This document details how MKM retrieves GPU usage (load) and GPU frequency for Android devices, specifically covering Adreno (Qualcomm/Snapdragon) and Mali (MediaTek/Exynos) GPUs.

## 1. Locating the GPU Device (devfreq path)

Before reading metrics, MKM locates the primary GPU devfreq interface. The script checks these locations in order of priority:

1. **Standard `devfreq` mapping (`/sys/class/devfreq/*`)**:
   - Searches for paths containing `kgsl` (Adreno priority 1).
   - Searches for paths containing `mali` (Mali priority 2).
   - Fallbacks for generic `gpu` and PowerVR/Imagination `pvr` or `rgx`.
2. **Legacy Adreno Path**: `/sys/class/kgsl/kgsl-3d0/devfreq`
3. **Legacy/MediaTek Mali Fallback**: `/sys/class/misc/mali0/device/devfreq/13000000.mali`

This resolves to the base `$path` used for subsequent metrics.

## 2. Reading GPU Frequency

Unlike CPUs, GPU frequency files are highly fragmented across vendors and Android versions. MKM iterates through a robust sequence of potential files, stopping at the first valid, non-zero reading. 

The paths checked (in order) are:
1. `$path/cur_freq` (Standard devfreq)
2. `$path/cur_frequency`
3. `$(dirname $path)/cur_freq`
4. `/sys/class/kgsl/kgsl-3d0/gpuclk` (Adreno specific)
5. `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` (Adreno legacy)
6. `/sys/class/devfreq/kgsl-3d0/cur_freq`
7. `/sys/class/devfreq/$BASE/cur_freq`
8. `/sys/devices/platform/soc/$SOC_ADDR/kgsl/kgsl-3d0/gpuclk` (Direct SoC addressing for Adreno)
9. `/sys/devices/platform/soc/$SOC_ADDR/kgsl/kgsl-3d0/devfreq/cur_freq`

*(Note: `$BASE` is the devfreq folder name, and `$SOC_ADDR` is extracted from the base name, e.g., `5000000` from `5000000.qcom,kgsl-3d0`)*

## 3. Reading GPU Usage (Load)

Obtaining GPU load percentage varies drastically between Mali and Adreno. MKM uses the following fallback strategy:

### Step 1: Standard devfreq load
MKM first checks if the standard devfreq load file is available:
- **File**: `$path/load`
- **Format**: Often a percentage (e.g., `45` or `45%`). MKM strips the `%` symbol if present.

### Step 2: Vendor-Specific Fallbacks
If the standard `load` file doesn't exist, MKM branches logic based on the GPU type.

#### Mali (MediaTek / Exynos)
For Mali GPUs, standard devfreq load is often missing. MKM falls back to the GED (Graphics Execution Manager) driver statistics exposed on MediaTek devices:
- **File**: `/sys/kernel/ged/hal/gpu_utilization`
- **Format**: Direct integer value representing percentage (0-100).

#### Adreno (Qualcomm Snapdragon)
For Adreno GPUs (`kgsl`), MKM relies on the kernel graphics support layer's `gpubusy` node.
- **File**: `../gpubusy` (relative to the devfreq path, effectively `/sys/class/kgsl/kgsl-3d0/gpubusy`).
- **Format**: Contains two space-separated values: `<busy_time> <total_time>`.
- **Calculation**: MKM calculates the load dynamically:
  `LOAD = (busy_time * 100) / total_time`

---

*Reference: See `app/src/main/java/com/ivarna/mkm/shell/GpuScripts.kt` for implementation details.*
