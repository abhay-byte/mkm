# Power Consumption Monitoring Component

To calculate power efficiency (Score/Watt), the application or scripts must be able to read real-time power consumption. This document outlines the technical implementation for retrieving this data.

## 1. The Power Formula
$$ \text{Power (Watts)} = \text{Voltage (Volts)} \times \text{Current (Amps)} $$

## 2. Kernel / Sysfs Approach (Root Required)
This is the most direct method and is used by the logic in our `scripts/`. It reads directly from the kernel's power supply subsystem.

### Paths
- **Base Directory**: `/sys/class/power_supply/`
- **Target Device**: Generally `battery` or `bms` (Battery Management System).

### Files
- `current_now`: Instantaneous current in **microamperes** ($\mu A$).
  - *Note*: Often negative when discharging. Take the absolute value.
- `voltage_now`: Instantaneous voltage in **microvolts** ($\mu V$).

### Calculation Example (Shell)
```bash
CURRENT=$(cat /sys/class/power_supply/battery/current_now) # e.g., -500000 -> 500mA
VOLTAGE=$(cat /sys/class/power_supply/battery/voltage_now) # e.g., 4200000 -> 4.2V

# Convert to Amps and Volts
# (uA * uV) / 10^12 = Watts
# Or easier: (uA / 10^6) * (uV / 10^6)
POWER_WATTS=$(awk "BEGIN {print ($CURRENT * $VOLTAGE) / 1000000000000}")
```

## 3. Android SDK Approach (App Level)
For the Android application code (`.kt`), we can use the `BatteryManager` API.

### `BatteryManager`
Located in `android.os.BatteryManager`.

**Properties**:
- `BATTERY_PROPERTY_CURRENT_NOW`: Instantaneous current average (microamperes).
- `BATTERY_PROPERTY_ENERGY_COUNTER`: Energy remaining (nanowatt-hours).

### implementation Snippet (Kotlin)
```kotlin
import android.os.BatteryManager
import android.content.Context

fun getPowerConsumption(context: Context): Float {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    
    // Current in microamperes
    val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).let {
        if (it < 0) -it else it // Absolute value
    }
    
    // Voltage is not directly available via Property, usually requires BroadcastReceiver for ACTION_BATTERY_CHANGED
    // OR approximation (e.g., 4.0V) if root not available.
    // BUT for root apps, read sysfs.
    
    // If using Root (ShellUtils):
    val currentStr = ShellUtils.readFile("/sys/class/power_supply/battery/current_now")
    val voltageStr = ShellUtils.readFile("/sys/class/power_supply/battery/voltage_now")
    
    val current = currentStr.toLongOrNull()?.let { kotlin.math.abs(it) } ?: 0L
    val voltage = voltageStr.toLongOrNull() ?: 0L
    
    // Power in Watts = (uA * uV) / 1e12
    return (current * voltage) / 1_000_000_000_000f
}
```

## 4. Hardware Limitations
- **Resolution**: Most fuel gauges update only every few seconds (e.g., 5-30s).
- **Noise**: Instantaneous current can fluctuate wildly. Always average readings over the duration of a benchmark run.
