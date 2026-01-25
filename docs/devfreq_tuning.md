# Android Devfreq Tuning Guide

This guide explains how to safely tune **devfreq** (Device Frequency) subsystems on Android devices, specifically focusing on memory (RAM) and interconnect bus scaling.

## What is devfreq?

`devfreq` is a generic Linux kernel framework used to scale the frequency and voltage of non-CPU devices, such as:
- **DRAM / RAM Controllers** (Memory Bandwidth)
- **GPU** (often handled separately but can use devfreq)
- **Interconnects** (Buses connecting CPU, GPU, RAM, etc.)
- **DSP / ISP** (Digital Signal / Image Signal Processors)

Unlike CPU governors (cpufreq), `devfreq` governors optimize for memory bandwidth utilization or specific hardware constraints.

## MediaTek: `mtk-dvfsrc-devfreq`

On MediaTek (MTK) devices, `mtk-dvfsrc-devfreq` is the device responsible for interacting with the **DVFSRC** (Dynamic Voltage and Frequency Scaling Resource Collector).

### What it controls
It primarily controls **DDR (RAM) Frequency** and related **Interconnect Voltages/Frequencies** (EMI/BUS).

### Common Governors
You can check available governors by reading `available_governors` in the devfreq directory.

- **`simple_ondemand`** (Default/Safe): Scales frequency based on load. Recommended for daily use.
- **`performance`**: Locks the device to the **maximum** available frequency. Useful for gaming.
- **`powersave`**: Locks the device to the **minimum** frequency.
- **`userspace`**: Allows manually setting a specific frequency.
- **`dummy`**: Usually a placeholder, does nothing.
- **`apupassive-pe`, `apupassive`, `apuconstrain`, `apuuser`**: Specific to MediaTek's AI Processing Unit (APU) workload coordination. Generally should not be set manually unless debugging AI tasks.

### Crash Risks & Safety
**⚠️ WARNING: CHANGING THESE SETTINGS CAN CAUSE REBOOTS OR FREEZES.**

- **RAM Stability**: Manually forcing a RAM frequency that is unstable or undervolted can crash the kernel immediately.
- **Under-speccing**: Setting the governor to `powersave` or a low fixed frequency usually causes the UI to become unresponsive because the screen/GPU cannot get data fast enough.
- **VCORE Conflicts**: The DVFSRC hardware manages voltage. If you force a frequency that the current voltage floor cannot support (unlikely with official kernels but possible with custom ones), the device will reboot.

**Recommendation**: Stick to `simple_ondemand` for balance, or `performance` for gaming. Avoid `userspace` unless you are sure the specific frequency is stable.

## Snapdragon Equivalents

Qualcomm Snapdragon devices use different naming conventions for their memory/interconnect nodes.

### Common Nodes
Look for these names in `/sys/class/devfreq/`:

- **`soc:qcom,bimc`** (Bus Interconnect Memory Controller) - **Primary RAM controller** on many older/mid-range chips.
- **`soc:qcom,ddr_bw`** - DDR Bandwidth control.
- **`soc:qcom,cnoc`** / **`soc:qcom,snoc`** - Config/System NoC (Network on Chip) buses.
- **`soc:qcom,cpu-cpu-llcc-bw`** - CPU to LLCC (Last Level Cache Controller) bandwidth.
- **`soc:qcom,m4m`** - Multimedia path.

*Note: Newer kernels (GKI / 5.x+) may use generic names like `1d84000.qcom,bcm-voter` or similar hex addresses. You often need to check `cat /sys/class/devfreq/*/cur_freq` to guess which one is changing dynamically with load.*

## How to Find & Tune

You need **Root** access or an **ADB Shell** with sufficient permissions (though usually Root is required to write).

### 1. List all devfreq devices
```bash
ls /sys/class/devfreq/
```

### 2. Identify the Memory Controller
Check the name of the device:
```bash
cat /sys/class/devfreq/<device_name>/trans_stat
# or just guess based on name (bimc, ddr, dvfsrc)
```

### 3. Check Available Frequencies & Governors
```bash
cd /sys/class/devfreq/mtk-dvfsrc-devfreq  # (Replace with your specific device)
cat available_frequencies
cat available_governors
```

### 4. Change Governor Safe Method
To switch to efficient mode (default):
```bash
echo "simple_ondemand" > governor
```

To switch to max performance:
```bash
echo "performance" > governor
```

To set a specific frequency (Advanced):
1. Switch to userspace: `echo "userspace" > governor`
2. specific freq: `echo 8533000000 > userspace/set_freq` (Must be a valid value from `available_frequencies`)

---

## Troubleshooting

- **Device froze after setting `powersave`**: Force restart the device (Hold Power + Vol Down).
- **Settings revert after reboot**: This is normal. You need a script or an app (like a Kernel Manager) to apply these on boot.
- **"Permission Denied"**: You strictly need **Root** (su) to write to these files.
