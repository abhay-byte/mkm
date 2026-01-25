# CPU and GPU Tuning Guide

## Overview
This guide provides technical details on how to safely manage CPU and GPU performance settings (governors and frequencies) on rooted Android devices. These settings allow you to balance performance against battery life and thermal output.

> [!CAUTION]
> **Risk of Force Reboot**: Writing invalid values (e.g., a frequency not supported by the hardware or a misspelled governor name) to system files can cause the kernel to panic, resulting in an immediate force reboot. Always verify available options before applying changes.

---

## 1. CPU Tuning

Android devices use the Linux kernel's CPUFreq subsystem. Modern mobile SoCs (System on Chips) are often "big.LITTLE" or tri-cluster designs, grouping cores into "policies".

### Understanding Policies
Instead of setting frequency for each core individually, cores are grouped into **clusters** managed by a **policy**.
- **Policy 0**: Usually the "LITTLE" (power-saving) cores.
- **Policy 4/6/7**: Usually the "Big" or "Prime" (performance) cores.

### Paths
Base directory: `/sys/devices/system/cpu/cpufreq/`

Inside `policyX` (where X is the policy number):
- `scaling_governor`: Current governor.
- `scaling_available_governors`: List of safe governors to use.
- `scaling_cur_freq`: Current operating frequency (KHz).
- `scaling_min_freq`: Minimum constrained frequency.
- `scaling_max_freq`: Maximum constrained frequency.
- `scaling_available_frequencies`: List of supported frequencies (KHz).

### Safety Checks
Before writing a value, **always read the "available" file first**.

**Example (Shell/Terminal):**
```bash
# Check available governors for Policy 0
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors

# Check available frequencies
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies
```

### Setting Values
To change settings, you must be root (`su`).

**Set Governor:**
```bash
echo "schedutil" > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
```

**Set Max Frequency (e.g., limit performance to save battery):**
```bash
# Must be a value from scaling_available_frequencies
echo "1800000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
```

### Common Governors
- **schedutil**: (Default) Uses kernel scheduler utilization data to make fast frequency decisions. Best for modern devices.
- **performance**: Locks CPU at max frequency. High battery drain.
- **powersave**: Locks CPU at min frequency. Laggy interface.
- **interactive**: (Older) Scales aggressively for touch responsiveness.

---

## 2. GPU Tuning

GPU paths vary significantly between SoC manufacturers (Qualcomm vs. MediaTek/Exynos/Tensor).

### Identifying Your GPU Path

**Qualcomm (Adreno):**
primary path: `/sys/class/kgsl/kgsl-3d0`
devfreq path: `/sys/class/kgsl/kgsl-3d0/devfreq` (or sometimes just the primary path)

**MediaTek/Mali:**
Common path: `/sys/class/misc/mali0/device/devfreq/13000000.mali` (Address `13000000` may vary)

### GPU Files
- `governor`: Current governor.
- `available_governors`: Supported governors.
- `min_freq` / `max_freq`: Frequency constraints.
- `available_frequencies`: Supported frequencies.

### Setting GPU Values

**Example (Adreno):**
```bash
# Set Governor
echo "msm-adreno-tz" > /sys/class/kgsl/kgsl-3d0/devfreq/governor

# Set Min Frequency (Hz)
echo "300000000" > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq
```

**Example (Mali):**
```bash
echo "simple_ondemand" > /sys/class/misc/mali0/device/devfreq/13000000.mali/governor
```

---

## 3. Best Practices & Troubleshooting

### Safe Application
1. **Never apply on boot initially**: Test your settings manually or via a script first. If the device reboots, the settings revert, saving you from a bootloop.
2. **Match exact strings**: "performance" is proper, "Performance" (capitalized) might be rejected or cause issues depending on the kernel driver strictness.
3. **Frequency Units**:
   - **CPU**: Usually **KHz** (e.g., 2000000 = 2.0 GHz).
   - **GPU**: Usually **Hz** (e.g., 500000000 = 500 MHz), but verify by reading the current freq first.

### Recovering from Instability
If you apply a setting that causes a freeze or reboot:
- The device should simply reboot to stock settings (since sysfs changes are volatile).
- If you used a root app to "Apply on Boot" and it loops:
  - Boot into Safe Mode (usually holding Volume Down during boot animation). This often disables third-party root apps from launching their boot scripts.
