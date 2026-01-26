# Android CPU Frequency Control

## Overview

This document explains how to properly set CPU frequencies on Android devices programmatically, based on research into how apps like 3C CPU Manager achieve this functionality.

## Prerequisites

- **Root Access**: Required for writing to sysfs files
- **Performance/Userspace Governor**: Recommended for direct frequency control
- **Custom Kernel** (optional): Some advanced features require custom kernels

## CPU Frequency Architecture

### Sysfs Paths

CPU frequency control is exposed through the Linux kernel's `cpufreq` subsystem via sysfs:

```
/sys/devices/system/cpu/cpuX/cpufreq/
├── scaling_max_freq          # Maximum allowed frequency
├── scaling_min_freq          # Minimum allowed frequency  
├── scaling_governor          # Active governor (performance, powersave, userspace, etc.)
├── scaling_available_frequencies  # List of supported frequencies
├── scaling_cur_freq          # Current operating frequency
└── cpuinfo_cur_freq          # Actual current frequency (read-only)
```

### Policy-Based Control

Modern Android devices use **cpufreq policies** to group CPUs that share frequency scaling:

```
/sys/devices/system/cpu/cpufreq/policyX/
├── affected_cpus             # Which cores are controlled by this policy
├── scaling_max_freq
├── scaling_min_freq
└── scaling_governor
```

## Governors

CPU governors determine how frequency scaling behaves:

| Governor | Behavior | Use Case |
|----------|----------|----------|
| **performance** | Always max frequency | Benchmarking, gaming |
| **powersave** | Always min frequency | Battery saving |
| **userspace** | Manual control by user/app | Direct frequency setting |
| **ondemand** | Scales based on load | General use |
| **interactive** | Aggressive scaling for UI | Default on many devices |
| **conservative** | Gradual scaling | Battery-focused |

## Proper Frequency Setting Method

### Step 1: Set Governor to Userspace or Performance

For direct frequency control, set the governor first:

```bash
# For policy-based (recommended)
echo "userspace" > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor

# Or for per-core
echo "userspace" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
```

**Why?** Other governors (like `ondemand` or `interactive`) will override your frequency settings based on their own algorithms.

### Step 2: Set Min and Max Frequencies

Set both min and max to the same value to lock frequency:

```bash
# Lock to 2000MHz
echo "2000000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
echo "2000000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
```

**Important:** Always set min before max, or max before min depending on direction:
- **Increasing frequency**: Set max first, then min
- **Decreasing frequency**: Set min first, then max

### Step 3: Verify the Change

```bash
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq
# Or for actual frequency
cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq
```

## Common Issues and Solutions

### Issue 1: Permission Denied

**Cause:** Insufficient permissions or SELinux restrictions

**Solution:**
- Ensure root access is granted
- Check SELinux mode: `getenforce`
- Temporarily set permissive: `setenforce 0` (not recommended for production)

### Issue 2: Frequency Not Changing

**Possible Causes:**

1. **Wrong Governor**
   - Solution: Set to `userspace` or `performance`

2. **Thermal Throttling**
   - Device is hot and kernel is limiting frequency
   - Solution: Cool down device, disable thermal throttling (risky)

3. **Vendor Restrictions**
   - Some OEMs lock certain clusters
   - Solution: May require custom kernel or be impossible

4. **Core Offline**
   - CPU core is powered down
   - Solution: Bring core online first:
     ```bash
     echo 1 > /sys/devices/system/cpu/cpu4/online
     ```

5. **MPDecision/Hotplug**
   - Qualcomm's MPDecision or kernel hotplug interfering
   - Solution: Disable MPDecision (requires custom kernel)

### Issue 3: Settings Don't Persist After Reboot

**Solution:** Use init scripts or apps with "set on boot" functionality

## Implementation in Android Apps

### Using Shell Commands

```kotlin
fun setFrequency(policyId: Int, freqKhz: String): Boolean {
    // 1. Set governor to userspace
    val govResult = ShellManager.exec(
        "echo userspace > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor"
    )
    
    if (!govResult.isSuccess) return false
    
    // 2. Set min and max frequency
    val minResult = ShellManager.exec(
        "echo $freqKhz > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_min_freq"
    )
    
    val maxResult = ShellManager.exec(
        "echo $freqKhz > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq"
    )
    
    return minResult.isSuccess && maxResult.isSuccess
}
```

### Per-Core Approach

```kotlin
fun setFrequencyForCore(coreId: Int, freqKhz: String): Boolean {
    // Find policy for this core
    val policyPath = findPolicyForCore(coreId) 
        ?: "/sys/devices/system/cpu/cpu$coreId/cpufreq"
    
    // Set frequency
    val result = ShellManager.exec(
        "echo $freqKhz > $policyPath/scaling_max_freq && " +
        "echo $freqKhz > $policyPath/scaling_min_freq"
    )
    
    return result.isSuccess
}
```

## Best Practices

1. **Always backup original settings** before making changes
2. **Restore settings on error** to prevent system instability
3. **Monitor temperature** when setting high frequencies
4. **Test on specific device** - behavior varies by OEM/kernel
5. **Handle failures gracefully** - some devices simply won't allow certain frequencies
6. **Add stabilization delay** (500-1000ms) after setting frequency before reading
7. **Use performance governor** for benchmarking instead of userspace if direct control fails

## Device-Specific Considerations

### Qualcomm Devices
- May have MPDecision interfering with frequency control
- Some frequencies may be restricted by thermal engine
- Custom kernels often provide better control

### Samsung (Exynos)
- Generally good frequency control support
- May have Samsung-specific governors

### MediaTek
- Can have vendor-specific restrictions
- Frequency tables may differ from other SoCs

## Debugging Frequency Issues

### Check Available Frequencies

```bash
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies
```

### Check Current Governor

```bash
cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
```

### Check Affected CPUs

```bash
cat /sys/devices/system/cpu/cpufreq/policy0/affected_cpus
```

### Monitor Real-Time Frequency

```bash
watch -n 0.5 cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_cur_freq
```

## References

- [Linux CPUFreq Documentation](https://www.kernel.org/doc/Documentation/cpu-freq/governors.txt)
- [Android Source - CPUFreq](https://android.googlesource.com/kernel/common/+/refs/heads/android-mainline/Documentation/cpu-freq/)
- [XDA Forums - CPU Control](https://xdaforums.com/)
- [3C CPU Manager](https://3c71.com/)

## Conclusion

Successful CPU frequency control on Android requires:
1. Root access
2. Proper governor configuration (userspace/performance)
3. Understanding of device-specific limitations
4. Graceful handling of failures

Some devices simply won't allow frequency changes on certain clusters due to vendor restrictions or thermal policies. In such cases, the best approach is to work within the device's limitations or require a custom kernel.
