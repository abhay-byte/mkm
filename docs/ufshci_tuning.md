# UFS Host Controller Tuning Guide

## Overview
The UFS Host Controller Interface (UFSHCI) manages the communication between your device's processor and its high-speed storage (Universal Flash Storage). The identifier seen in settings, such as **112b0000.ufshci**, represents the specific hardware address of this controller on your device's motherboard.

## 1. Frequency Governors
The **Governor** controls how the UFS controller switches between different clock frequencies (speeds). The list of available governors depends on your device manufacturer (e.g., Qualcomm, MediaTek, Samsung) and kernel version.

### Standard Governors (Common)
These are standard Linux kernel governors often found on many devices:

*   **simple_ondemand** (Recommended): The default for most mobile devices. It scales the frequency up when there is I/O activity (reading/writing data) and scales it down when idle. It offers the best balance for daily use.
*   **performance**: Locks the controller to the **Maximum Freq**.
    *   *Pros:* fastest possible storage response.
    *   *Cons:* increased power consumption and heat; prevents the device from entering low-power states.
*   **powersave**: Locks the controller to the **Minimum Freq**.
    *   *Pros:* maximum battery saving.
    *   *Cons:* noticeably slower app loading and file transfers.
*   **userspace**: Allows custom frequency setting by user-space programs. Generally not used unless you have a specific script controlling it.
*   **dummy**: Usually a placeholder governor that does nothing or passes control to hardware logic directly.

### Vendor-Specific Governors (Likely MediaTek/APU)
The "apu" prefix (e.g., `apupassive`) strongly suggests these are specific to **MediaTek** chipsets or devices with specific AI Processing Unit (APU) power management integration.

*   **apupassive**: A passive power management mode where the UFS controller likely waits for specific power signals from the APU to change states, prioritizing low power consumption.
*   **apupassive-pe**: Similar to `apupassive` but likely with more aggressive Power Efficiency (PE) optimizations enabled.
*   **apuconstrain**: Indicates a constrained mode, possibly limiting the maximum frequency or throughput to prevent overheating or excessive battery drain during specific APU tasks.
*   **apuuser**: A mode designed to be controlled or influenced by user-space applications or specific vendor services, allowing dynamic adjustment based on current app needs.

> **Note:** For most users, **simple_ondemand** is the safest and most effective choice. Experimental governors like `apupassive` should only be used if you are testing for specific battery drain issues.

---

## 2. Finding Your UFS Controller Address
The value `112b0000` is a **hexadecimal memory address** assigned by the hardware manufacturer (Device Tree). It will be different on different phones (e.g., a Pixel might differ from a Xiaomi).

If you are rooted, you can find your specific UFS controller address using a terminal emulator (like Termux) or `adb shell`.

### Methods to Locate UFS Address

**Method 1: Searching System Devices (Recommended)**
Run the following command in a root shell:
```bash
su
find /sys/devices/platform -name "*.ufshci"
```
*Output Example:*
`/sys/devices/platform/soc/112b0000.ufshci`
*(The number `112b0000` is your address)*

**Method 2: Listing SCSI Hosts**
UFS devices appear as SCSI hosts. You can list them to see their names:
```bash
su
ls -l /sys/class/scsi_host/
```
Look for a folder (host0, host1...) that links to a path containing `.ufshci`.

**Method 3: Checking MountInfo**
You can sometimes infer it from where the main partitions are mounted:
```bash
mount | grep /data
```
The output usually points to a block device (e.g., `/dev/block/sda` or `/dev/block/dm-0`). Tracing the parent of this block device device in `/sys/block/` represents the UFS controller.

## 3. Tuning Recommendations

| Goal | Governor | Minimum Freq | Notes |
| :--- | :--- | :--- | :--- |
| **Balanced (Default)** | `simple_ondemand` | Lowest Available | Best for daily use. |
| **Gaming / Performance** | `simple_ondemand` | Medium/High | Setting a higher Min Freq reduces "micro-stutters" when loading assets. |
| **Max Battery** | `powersave` | Lowest Available | Will make the UI feel sluggish. |
| **Benchmarking** | `performance` | Max Available | Forces max speed for checking raw storage throughput. |
