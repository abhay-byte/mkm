# GPU Frequency and Governor Tuning Guide

This guide details how to manage GPU performance settings (frequency and governor) on rooted Android devices. It covers major GPU architectures including Qualcomm Adreno, ARM Mali (MediaTek/Exynos/Pixel), PowerVR, and Samsung Xclipse.

> [!WARNING]
> **Root Access Required**: All commands below require root privileges (`su`).
> **Risk of Instability**: Setting unsupported frequencies or incompatible governors can cause system freezes, graphical glitches, or immediate reboots. Always verify available values before applying changes.

## 1. General Principles

Most Android GPUs use the Linux kernel's **devfreq** (Device Frequency) subsystem.
- **Base Directory**: `/sys/class/devfreq/`
- **Key Files**:
    - `governor`: Current scaling governor.
    - `available_governors`: List of supported governors.
    - `cur_freq`: Current GPU frequency (Hz).
    - `min_freq`: Minimum constrained frequency (Hz).
    - `max_freq`: Maximum constrained frequency (Hz).
    - `available_frequencies`: List of supported frequencies (Hz).

## 2. Finding Your GPU Device

Since paths vary by kernel and vendor, the most reliable method is to list all devfreq devices and identify the GPU.

**Command:**
```bash
ls -l /sys/class/devfreq/
```

Look for names like:
- `kgsl-3d0` (Qualcomm Adreno)
- `*.mali` (ARM Mali)
- `*.gpu` (Exynos/Generic)
- `*.pvr` or similar (PowerVR)

---

## 3. Comprehensive Path List

### Qualcomm Adreno (Snapdragon)
**Devfreq Paths**:
- `/sys/class/kgsl/kgsl-3d0/devfreq/` (Most common)
- `/sys/class/devfreq/b00000.qcom,kgsl-3d0/`
- `/sys/class/devfreq/1c00000.qcom,kgsl-3d0/`
- `/sys/devices/platform/soc/*.qcom,kgsl-3d0/devfreq/`

**Legacy / Direct Driver Paths**:
- `/sys/class/kgsl/kgsl-3d0/`
    - `gpuclk` (Read/Write Current Freq)
    - `max_gpuclk`
    - `idle_timer`
- `/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/`

### MediaTek & ARM Mali (Pixel, Exynos, Dimensity)
**MediaTek (Dimensity/Helio)**:
- `/sys/class/misc/mali0/device/devfreq/13000000.mali/`
- `/sys/devices/platform/13000000.mali/devfreq/13000000.mali/`
- `/sys/devices/platform/soc/13000000.mali/devfreq/13000000.mali/`

**Pixel (Tensor)**:
- `/sys/class/devfreq/28000000.mali/` (Pixel 6/7/8 series)
- `/sys/devices/platform/28000000.mali/devfreq/28000000.mali/`

**Other Mali Implementations**:
- `/sys/devices/platform/e82c0000.mali/devfreq/e82c0000.mali/`
- `/sys/devices/platform/ffe40000.bifrost/`
- `/sys/kernel/gpu/` (Generic Android 10+ path for some kernels)

### Samsung Xclipse (AMD RDNA)
Found in newer Exynos devices (S22, S23, S24 FE).
- `/sys/class/devfreq/57000000.gpu/`
- `/sys/devices/platform/57000000.gpu/devfreq/57000000.gpu/`

### PowerVR (ImgTec)
Used in some older MediaTek and lower-end chips.
- `/sys/class/devfreq/rgx/`
- `/sys/devices/platform/..../rgx/devfreq/`
- Look for `pvrsrvkm` in `/proc/modules` or `/sys/module/`.

---

## 4. How to Tune (Step-by-Step)

### Step 1: Switch to Root
```bash
su
```

### Step 2: Identify your path
```bash
# Example for MediaTek
GPUPATH="/sys/class/misc/mali0/device/devfreq/13000000.mali"

# Example for Adreno
GPUPATH="/sys/class/kgsl/kgsl-3d0/devfreq"
```

### Step 3: View Available Options
**Governors:**
```bash
cat $GPUPATH/available_governors
# Output example: simple_ondemand performance powersave userspace
```

**Frequencies:**
```bash
cat $GPUPATH/available_frequencies
# Output example: 300000000 50000000 800000000
```

### Step 4: Set a Governor
Safe choices:
- `simple_ondemand`: Default for most. Good balance.
- `performance`: Locks to max freq. High battery drain.
- `powersave`: Locks to min freq. Laggy gaming.

**MediaTek Specific (APU/AI)**:
- `dummy`: Placeholder, usually does nothing.
- `apupassive`, `apupassive-pe`: Passive mode for AI Processing Unit.
- `apuconstrain`, `apuuser`: AI workflow coordination governors.
*Note: These APU governors are often visible on MediaTek GPUs but should generally be avoided for standard GPU tuning.*

**Command:**
```bash
echo "performance" > $GPUPATH/governor
```

### Step 5: Set Frequency Constraints (Min/Max)
To limit max performance (save battery) or raise minimum floor (reduce stutter).

**Set Max Freq (e.g., limit to 500 MHz):**
```bash
# Must pick a generic valid value from available_frequencies
echo 500000000 > $GPUPATH/max_freq
```

**Set Min Freq (e.g., floor at 300 MHz):**
```bash
echo 300000000 > $GPUPATH/min_freq
```

### Step 6: Fix Frequency (Userspace)
To lock a specific frequency exactly:
1. Set governor to `userspace`.
2. Set frequency.

```bash
echo "userspace" > $GPUPATH/governor
echo 800000000 > $GPUPATH/userspace/set_freq
# OR on some devices just writing to min/max locks it if using performance governor
```

## 5. Troubleshooting
- **Permission Denied**: Ensure you ran `su`.
- **"Invalid Argument"**: The frequency you wrote is not in `available_frequencies` or the governor name is misspelled.
- **Reboot/Freeze**: You set a frequency that is unstable at the current voltage (undervolting issue) or set a governor that the kernel driver doesn't fully support. Reboot to reset.

---

## 6. Safety & Best Practices (Preventing Force Reboots)

Modifying GPU frequencies can lead to instability if done incorrectly. Follow these rules to avoid bootloops and crashes:

### Rule #1: Never "Set on Boot" Initially
When testing new frequencies or governors using a script or app (like 3C Toolbox, Franco Kernel Manager, etc.):
- **Do NOT** check "Apply on Boot" until you have tested the stability for at least 30 minutes.
- If the device crashes or reboots with "Apply on Boot" off, it will simply restart with stock safe settings.
- If you apply unstable settings on boot, you may create a **Bootloop** requiring Safe Mode or data wipe to fix.

### Rule #2: Check `available_frequencies` First
Never blindly echo a value. Always read the available list first.
- Many kernels will panic (force reboot) if you write a value not present in the scaling table.
- **Command**: `cat $GPUPATH/available_frequencies`

### Rule #3: Respect Voltage Limits
- Higher frequencies require higher voltage (p-states). If the kernel logic doesn't automatically raise the voltage (or if you are undervolting via other means), the GPU will hang, causing a visual freeze or reboot.
- Stick to the officially supported frequencies listed in step 2.

### Rule #4: Small Increments
When tuning manually:
1. Don't jump from min to max immediately on unstable hardware.
2. Test intermediate frequencies.

### Emergency Recovery
If you stuck the device in a loop:
1. **Force Reboot**: Hold Power + Volume Down for 10-15 seconds.
2. **Safe Mode**: Hold Volume Down while the boot animation plays. This usually prevents third-party root apps from executing their boot scripts.
