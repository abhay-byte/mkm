# Root CPU Utilization Component

## Overview

This component provides accurate CPU utilization monitoring using direct `/proc/stat` reading via root access, with automatic fallback to frequency-based calculation when root is unavailable.

## Architecture

The implementation consists of three main components:

### 1. CpuUtilizationScripts.kt
Shell scripts that read CPU statistics from `/proc/stat`:
- `getCpuStatOverall()` - Gets overall CPU statistics
- `getCpuStatPerCore()` - Gets per-core CPU statistics
- `getCpuStatAll()` - Gets both in a single call (more efficient)

### 2. CpuUtilizationProvider.kt
Core logic for CPU utilization calculation:
- **Primary Method**: Reads `/proc/stat` using root access via `ShellManager`
- **Fallback Method**: Calculates usage from CPU frequency (no root required)
- **Throttling**: Implements a 900ms minimum update interval to prevent interference between multiple callers (e.g., Home screen and Overlay service)
- Implements delta-based calculation for accurate utilization percentages
- Caches last calculated values (overall and per-core)
- Thread-safe singleton pattern using `@Synchronized` for updates

### 3. Integration with CpuProvider.kt
The existing `CpuProvider` has been updated to use `CpuUtilizationProvider` for all CPU usage calculations, ensuring consistency across the entire application.

## How It Works

### `/proc/stat` Method (Primary)

The `/proc/stat` file contains CPU time counters in jiffies (clock ticks):

```
cpu  964800 78931 728531 5865986 18001 102980 5440 0 0 0
```

Fields:
1. `user` - Normal processes executing in user mode
2. `nice` - Niced processes executing in user mode
3. `system` - Processes executing in kernel mode
4. `idle` - Idle time
5. `iowait` - Waiting for I/O to complete
6. `irq` - Servicing interrupts
7. `softirq` - Servicing softirqs
8. `steal` - Time spent in other operating systems (virtualization)
9. `guest` - Time spent running virtual CPU
10. `guest_nice` - Time spent running niced guest

**Calculation**:
```kotlin
total_time = user + nice + system + idle + iowait + irq + softirq + steal
idle_time = idle + iowait
active_time = total_time - idle_time

// Delta-based calculation between two measurements
total_delta = current_total - previous_total
idle_delta = current_idle - previous_idle
cpu_usage = 1.0 - (idle_delta / total_delta)
```

### Frequency-Based Method (Fallback)

When root access is unavailable, CPU usage is estimated from frequency:

```kotlin
current_freq = read("/sys/devices/system/cpu/cpuX/cpufreq/scaling_cur_freq")
min_freq = read("/sys/devices/system/cpu/cpuX/cpufreq/cpuinfo_min_freq")
max_freq = read("/sys/devices/system/cpu/cpuX/cpufreq/cpuinfo_max_freq")

usage = (current_freq - min_freq) / (max_freq - min_freq)
```

This method is less accurate but provides a reasonable estimate without root.

## Usage

### Basic Usage

```kotlin
// Get overall CPU usage (0.0 to 1.0)
val cpuUsage = CpuUtilizationProvider.getOverallCpuUsage()
Log.d("CPU", "Usage: ${(cpuUsage * 100).toInt()}%")

// Get per-core CPU usage
val perCoreUsage = CpuUtilizationProvider.getPerCoreCpuUsage()
perCoreUsage.forEach { (coreId, usage) ->
    Log.d("CPU", "Core $coreId: ${(usage * 100).toInt()}%")
}
```

### With Coroutines (Recommended)

```kotlin
viewModelScope.launch {
    // First call initializes baseline
    CpuUtilizationProvider.getOverallCpuUsage()
    delay(500) // Wait for CPU activity
    
    // Subsequent calls return actual usage
    while (isActive) {
        val usage = CpuUtilizationProvider.getOverallCpuUsage()
        _cpuUsage.value = usage
        delay(1000) // Update every second
    }
}
```

### Disable Fallback (Force /proc/stat only)

```kotlin
// Returns 0.0 if /proc/stat unavailable (no root)
val usage = CpuUtilizationProvider.getOverallCpuUsage(useFrequencyFallback = false)
```

### Reset Cache

```kotlin
// Reset cached measurements to get fresh baseline
CpuUtilizationProvider.reset()
```

## Integration Points

The CPU utilization component is now integrated throughout the application:

### 1. CpuProvider
- **Location**: `app/src/main/java/com/ivarna/mkm/data/provider/CpuProvider.kt`
- **Method**: `getCpuStatus()`
- Uses `CpuUtilizationProvider` for overall and per-core usage calculations

### 2. HomeScreen
- **Location**: `app/src/main/java/com/ivarna/mkm/ui/screens/HomeScreen.kt`
- Displays overall CPU usage in StatCard
- Automatically uses new calculation via `CpuProvider`

### 3. CpuScreen
- **Location**: `app/src/main/java/com/ivarna/mkm/ui/screens/CpuScreen.kt`
- Shows detailed CPU utilization in HeroUsageCard
- Displays per-core usage for each cluster

### 4. OverlayService
- **Location**: `app/src/main/java/com/ivarna/mkm/service/OverlayService.kt`
- Displays real-time CPU usage in floating overlay
- Updates every refresh cycle

## Advantages

### Over Previous Implementation
1. **More Accurate**: Uses actual CPU time statistics instead of frequency estimation
2. **Root-Aware**: Automatically uses best available method
3. **Modular**: Centralized logic, easy to maintain and reuse
4. **Efficient**: Delta-based calculation, no unnecessary overhead
5. **Robust**: Graceful fallback when root unavailable

### /proc/stat vs Frequency-Based
| Aspect | /proc/stat | Frequency-Based |
|--------|-----------|-----------------|
| Accuracy | High (actual time spent) | Medium (estimation) |
| Root Required | Yes | No |
| CPU Overhead | Minimal | Minimal |
| Governor Independence | Yes | No (affected by governor) |
| Use Case | Primary method | Fallback method |

## Technical Details

### Thread Safety
- Uses `object` singleton pattern
- All state is internal and thread-safe
- Safe to call from multiple threads/coroutines

### Performance
- Minimal CPU overhead (simple arithmetic)
- File reads cached by kernel
- Delta calculation is O(1)

### Memory
- Stores only last measurement (~200 bytes)
- Per-core map scales with core count
- Automatic cleanup on reset

### Root Access
- Uses `ShellManager.exec()` for root commands
- Falls back gracefully if root unavailable
- No app crashes on permission denial

## Example Output

```
CpuUtilizationProvider: CPU usage from /proc/stat: 45%
CpuProvider: Core 0: freq=1800000, usage=0.45 (hwMin=300000, hwMax=2400000)
CpuProvider: Core 1: freq=1900000, usage=0.52 (hwMin=300000, hwMax=2400000)
CpuProvider: Core 2: freq=1100000, usage=0.31 (hwMin=300000, hwMax=2400000)
CpuProvider: Overall usage: 0.45 (from /proc/stat)
```

## Troubleshooting

### Issue: Always returns 0%
**Cause**: First call or no CPU activity
**Solution**: Call once to initialize, then wait 500ms before reading again

### Issue: Fallback to frequency method
**Cause**: Root access not available or `/proc/stat` read failed
**Solution**: Grant root access via SuperSU/Magisk or accept frequency-based estimation

### Issue: Inaccurate readings
**Cause**: CPU governor in powersave mode with frequency-based fallback
**Solution**: Switch to performance governor or ensure root access for `/proc/stat` method

### Issue: Per-core usage empty
**Cause**: First measurement or read failure
**Solution**: Ensure root access and call again after 500ms

## Future Enhancements

Potential improvements:
1. Add user/system/iowait breakdown
2. Implement moving average for smoothing
3. Add CPU temperature correlation
4. Export usage history for analysis
5. Add CPU load average integration

## References

- Linux kernel documentation: `/proc/stat` format
- Android cpufreq subsystem: `/sys/devices/system/cpu/cpufreq/`
- Root execution: `ShellManager` and `libsu` library
