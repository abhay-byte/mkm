# Thermal Tuning & Throttling Disabling

> [!CAUTION]
> **EXTREME RISK WARNING**: Disabling or modifying thermal limits can cause **PERMANENT HARDWARE DAMAGE**, battery explosion, fire, and severe burns.
> Mobile SoCs are designed to throttled to protect themselves. A limit of **105°C** is often beyond the absolute maximum rating (TjMax) of mobile silicon and can cause immediate failure or desoldering of components. Proceed at your own risk.

## Overview

Thermal throttling is a safety mechanism where the kernel reduces CPU/GPU frequencies to lower the temperature when the device gets too hot. On rooted devices, this behavior can be prioritized or disabled, though it is highly dangerous.

## Method 1: Modifying Thermal Configuration Files (Qualcomm devices)

Most Qualcomm-based devices use a configuration file to define thermal zones and actions.

### Locations
Common paths (requires root):
- `/system/etc/thermal-engine.conf`
- `/vendor/etc/thermal-engine.conf`
- `/vendor/etc/thermal-engine-map.conf`
- `/sys/kernel/debug/thermal/thermal_zone*/temp` (View current temps)

### How to Edit
1. **Backup** the original file first.
2. Open the file directly on the phone (using a root explorer) or via `adb pull` / `adb push`.
3. Look for sections defining `set_point` or `thresholds`.

### Example Configuration (Legacy Qualcomm)

```ini
[SS-GPU]
algo_type monitor
sensor tsens_tz_sensor9
sampling 50
set_point 90000
set_point_clr 85000
action gpu
action_info 400000000
```

- **`set_point`**: The temperature to STRAT throttling (in millidegrees Celsius). `90000` = 90°C.
- **`set_point_clr`**: The temperature to STOP throttling. `85000` = 85°C.

**To extend limits:**
Increase `set_point` to your desired limit (e.g., from `75000` to `90000`).
**DO NOT SET TO 105°C** unless you are absolutely certain your silicon supports it (most do not).

## Method 2: Disabling Thermal Daemons (Shell)

You can temporarily stop the thermal engine service. This stops the userspace thermal management, but kernel-level hard limits (OTPs) may still trigger a shutdown to save the hardware.

### Commands

```bash
su
stop thermal-engine
stop thermald
```

To make this permanent (NOT RECOMMENDED), you would need to disable the service in init scripts or rename the binary:

```bash
su
mv /vendor/bin/thermal-engine /vendor/bin/thermal-engine.bak
```
*Note: This may cause bootloops or overheating shutdown immediately.*

## Method 3: Magisk Modules

Magisk modules are the safest way to "systemlessly" edit these files.

### How they work
1. **Systemless File Replacement**: They mount a modified `thermal-engine.conf` over the system one.
2. **Resetprop**: They may use `resetprop` to change system properties related to thermal handling.
3. **Service Disabling**: Some modules simply run a boot script to `stop thermal-engine`.

### Searching for Modules
Search for "Thermal Mod", "Thermal Throttling Disabler", or specific device mods (e.g., "Poco F1 Thermal Unlock"). These pre-made scripts often contain optimized values that are aggressive but less likely to instantly kill the device than setting raw 105°C limits yourself.

## Safety Guidelines

1. **Monitor Temperatures**: Always have a temperature overlay (like `htop` or a floating monitor) active when testing.
2. **Stress Test**: Run benchmarks (Geekbench, 3DMark) to see if the device stays stable.
3. **Battery Limit**: Batteries degrade locally above 45°C and become dangerous above 60°C. If your CPU is at 90°C, the heat spreads to the battery. **Ensure the battery stays cool.**
