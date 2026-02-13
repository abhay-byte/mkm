# Working with Rooted ADB

## Overview

ADB (Android Debug Bridge) is a versatile command-line tool that lets you communicate with Android devices. When working with system-level modifications like the MKM app, having root access through ADB can be essential for testing and debugging.

## Table of Contents

- [Understanding ADB Root Access](#understanding-adb-root-access)
- [Testing Current ADB Connection](#testing-current-adb-connection)
- [Enabling ADB Root](#enabling-adb-root)
- [Root Access Methods](#root-access-methods)
- [Implementation Options for MKM](#implementation-options-for-mkm)
- [Common ADB Root Commands](#common-adb-root-commands)
- [Troubleshooting](#troubleshooting)
- [Security Considerations](#security-considerations)

## Understanding ADB Root Access

ADB can run in two privilege modes:

1. **Shell User (uid=2000)**: Default mode with limited permissions
2. **Root User (uid=0)**: Elevated mode with full system access

### Checking Current Privileges

```bash
# Check current user 
adb shell "id"

# Expected output for regular shell:
# uid=2000(shell) gid=2000(shell) groups=...

# Expected output for root:
# uid=0(root) gid=0(root) groups=...
```

## Testing Current ADB Connection

### 1. Check Connected Devices

```bash
adb devices
```

Expected output:
```
List of devices attached
<device_ip>:<port>    device
```

### 2. Verify Root Availability

```bash
# Method 1: Check if su command exists
adb shell "which su"

# Method 2: Try to execute su
adb shell "su -c 'id'"

# Method 3: Check current shell user
adb shell "id"
```

## Enabling ADB Root

### Prerequisites

- Device must be rooted (Magisk, SuperSU, etc.) OR
- Device must be running a userdebug/eng build (not user build)
- USB debugging enabled in Developer Options

### Method 1: Using `adb root` Command

The `adb root` command restarts the ADB daemon (adbd) in root mode:

```bash
# Restart ADB daemon as root
adb root

# Expected output:
# "restarting adbd as root"
```

**Note**: This command only works on:
- Devices with userdebug or eng builds
- Emulators
- Some custom ROMs with insecure boot images

**After running `adb root`**:
```bash
# Wait for device to reconnect
sleep 3

# Verify root access
adb shell "id"

# Should show: uid=0(root) gid=0(root)
```

### Method 2: Using `su` Command (Rooted Devices)

For devices rooted with Magisk or similar:

```bash
# Execute command as root via su
adb shell "su -c 'command'"

# Example: Read system file
adb shell "su -c 'cat /proc/cpuinfo'"

# Interactive root shell
adb shell
su
# Now you have root shell
```

### Method 3: Wireless ADB with Root

For wireless debugging with root:

```bash
# Enable TCP/IP mode on port 5555
adb tcpip 5555

# Connect wirelessly
adb connect <device_ip>:5555

# Enable root
adb root

# Reconnect if needed
adb connect <device_ip>:5555
```

## Root Access Methods

### Comparison Matrix

| Method | Requirements | Persistence | Best For |
|--------|--------------|-------------|----------|
| `adb root` | userdebug/eng build | Until reboot | Development devices, emulators |
| `su` command | Magisk/SuperSU | Across reboots | Production rooted devices |
| Root Manager APIs | Root management app | App-controlled | Production apps (Magisk, etc.) |
| Shizuku/Dhizuku | Shizuku service | While service runs | Apps requiring elevated permissions |

## Implementation Options for MKM

### Option 1: Direct Root Shell Commands

**Use Case**: Quick system modifications, file access

```kotlin
// Example implementation
fun executeRootCommand(command: String): String {
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        
        process.waitFor()
        
        return if (error.isEmpty()) output else error
    } catch (e: Exception) {
        return "Error: ${e.message}"
    }
}

// Usage
val cpuInfo = executeRootCommand("cat /proc/cpuinfo")
val setFreq = executeRootCommand("echo 2000000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq")
```

### Option 2: Root File Access

**Use Case**: Reading/writing protected system files

```kotlin
fun readRootFile(filePath: String): String? {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat", filePath))
        process.inputStream.bufferedReader().readText()
    } catch (e: Exception) {
        null
    }
}

fun writeRootFile(filePath: String, content: String): Boolean {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh"))
        process.outputStream.bufferedWriter().use { writer ->
            writer.write("echo '$content' > $filePath\n")
            writer.write("exit\n")
        }
        process.waitFor() == 0
    } catch (e: Exception) {
        false
    }
}
```

### Option 3: Using Libsu Library (Recommended)

**Use Case**: Production Android apps requiring root

Add to `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.topjohnwu.libsu:core:5.0.5")
    implementation("com.github.topjohnwu.libsu:service:5.0.5")
}
```

Implementation:
```kotlin
import com.topjohnwu.superuser.Shell

// Initialize in Application class
class MKMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}

// Check root access
fun checkRootAccess(): Boolean {
    return Shell.isAppGrantedRoot() ?: false
}

// Execute root command
fun executeCpuFrequencyChange(cpuId: Int, frequency: Long) {
    Shell.cmd(
        "echo $frequency > /sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_max_freq"
    ).exec().also { result ->
        if (result.isSuccess) {
            Log.d("Root", "Frequency changed successfully")
        } else {
            Log.e("Root", "Failed: ${result.err}")
        }
    }
}

// Execute multiple commands
fun applyOptimizations() {
    Shell.cmd(
        "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
        "echo 0 > /proc/sys/kernel/randomize_va_space",
        "echo 3 > /proc/sys/vm/drop_caches"
    ).submit { result ->
        if (result.isSuccess) {
            Log.d("Root", "Optimizations applied")
        }
    }
}
```

### Option 4: Shizuku Integration (Alternative to Root)

For users without root or preferring Shizuku:

```kotlin
// Check Shizuku availability
fun isShizukuAvailable(): Boolean {
    return try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
}

// Execute with Shizuku
fun executeWithShizuku(command: String) {
    val process = Shizuku.newProcess(
        arrayOf("sh", "-c", command),
        null,
        null
    )
    // Handle process output
}
```

## Common ADB Root Commands

### System Information

```bash
# CPU information
adb shell "su -c 'cat /proc/cpuinfo'"

# Memory information
adb shell "su -c 'cat /proc/meminfo'"

# CPU frequency info
adb shell "su -c 'cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq'"

# GPU information
adb shell "su -c 'cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq'"
```

### System Modifications

```bash
# Change CPU governor
adb shell "su -c 'echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor'"

# Set CPU frequency
adb shell "su -c 'echo 2000000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq'"

# Disable SELinux (temporary, for testing only)
adb shell "su -c 'setenforce 0'"

# Check SELinux status
adb shell "su -c 'getenforce'"

# Remount system as read-write
adb shell "su -c 'mount -o remount,rw /system'"

# Modify system property
adb shell "su -c 'setprop debug.performance.mode 1'"
```

### File Operations

```bash
# Copy file to system partition
adb push local_file.txt /sdcard/
adb shell "su -c 'cp /sdcard/local_file.txt /system/etc/'"

# Change file permissions
adb shell "su -c 'chmod 644 /system/etc/file'"

# Change file ownership
adb shell "su -c 'chown root:root /system/etc/file'"

# Read protected file
adb shell "su -c 'cat /data/system/users/0/settings_system.xml'"
```

### Process Management

```bash
# List all processes
adb shell "su -c 'ps -A'"

# Kill process by name
adb shell "su -c 'killall -9 process_name'"

# Get process priority
adb shell "su -c 'ps -p <pid> -o nice'"

# Change process priority
adb shell "su -c 'renice -n -20 -p <pid>'"
```

## Troubleshooting

### ADB Root Fails

**Error**: `adbd cannot run as root in production builds`

**Solutions**:
1. Device has a production (user) build - use `su` instead
2. Install a custom ROM with insecure boot
3. Use Magisk to make boot image insecure
4. Use Shizuku as alternative

### Connection Lost After `adb root`

**Issue**: Device goes offline after `adb root`

**Solutions**:
```bash
# Method 1: Wait and reconnect
sleep 5
adb reconnect

# Method 2: Kill and restart ADB server
adb kill-server
adb start-server
adb devices

# Method 3: Reconnect to wireless ADB
adb connect <device_ip>:<port>
```

### `su` Command Not Found

**Error**: `/system/bin/sh: su: inaccessible or not found`

**Solutions**:
1. Device is not rooted - install Magisk
2. Root access not granted - check Magisk settings
3. App not granted root permission - request in Magisk

### Permission Denied

**Error**: `Permission denied` even with root

**Solutions**:
```bash
# Check SELinux status
adb shell "su -c 'getenforce'"

# Temporarily disable SELinux (testing only)
adb shell "su -c 'setenforce 0'"

# Check file permissions
adb shell "su -c 'ls -laZ /path/to/file'"

# Fix permissions
adb shell "su -c 'chmod 666 /path/to/file'"
```

### Wireless ADB Issues

**Issue**: Can't connect after `adb root` on wireless

**Solutions**:
```bash
# Disable root, reconnect, then re-enable
adb unroot
adb disconnect
adb connect <device_ip>:5555
adb root
sleep 3
adb shell "id"  # Verify root
```

## Security Considerations

### Risks of Root Access

1. **System Stability**: Incorrect modifications can brick device
2. **Security Vulnerabilities**: Apps with root can access all data
3. **Warranty Void**: May void device warranty
4. **SafetyNet Failure**: Banking apps may not work

### Best Practices

1. **Validate Commands**: Always validate user input before executing root commands
2. **Minimal Permissions**: Request root only when necessary
3. **User Confirmation**: Show warnings before critical operations
4. **Backup**: Encourage users to backup before modifications
5. **Error Handling**: Gracefully handle permission denials
6. **SELinux Awareness**: Respect SELinux policies in production

### Security Implementation Example

```kotlin
// Secure root command execution
class RootCommandExecutor {
    private val allowedCommands = setOf(
        "cat /proc/cpuinfo",
        "cat /sys/devices/system/cpu/*/cpufreq/scaling_cur_freq",
        // ... whitelist of safe commands
    )
    
    fun executeSecurely(command: String): Result<String> {
        // Validate command
        if (!isCommandSafe(command)) {
            return Result.failure(SecurityException("Command not allowed"))
        }
        
        // Check root access
        if (!Shell.isAppGrantedRoot()) {
            return Result.failure(SecurityException("Root access denied"))
        }
        
        // Execute with timeout
        return try {
            val result = Shell.cmd(command).exec()
            if (result.isSuccess) {
                Result.success(result.out.joinToString("\n"))
            } else {
                Result.failure(Exception(result.err.joinToString("\n")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun isCommandSafe(command: String): Boolean {
        // Implement command validation logic
        return allowedCommands.any { command.startsWith(it) }
    }
}
```

## Testing Root Access in MKM

### Test Script

Create a test script to verify root functionality:

```bash
#!/bin/bash
# Test script for MKM root access

echo "=== MKM Root Access Test ==="
echo ""

# Test 1: Check ADB connection
echo "Test 1: Checking ADB connection..."
adb devices | grep -q "device$"
if [ $? -eq 0 ]; then
    echo "✓ Device connected"
else
    echo "✗ No device connected"
    exit 1
fi

# Test 2: Check current privilege
echo ""
echo "Test 2: Checking current privilege..."
USER_ID=$(adb shell "id" | grep -oP 'uid=\K[0-9]+')
if [ "$USER_ID" == "0" ]; then
    echo "✓ Already running as root (uid=0)"
elif [ "$USER_ID" == "2000" ]; then
    echo "● Running as shell user (uid=2000)"
else
    echo "? Unknown user ID: $USER_ID"
fi

# Test 3: Try adb root
echo ""
echo "Test 3: Attempting adb root..."
adb root
sleep 3

# Test 4: Verify root access
echo ""
echo "Test 4: Verifying root access..."
ROOT_ID=$(adb shell "id" 2>/dev/null | grep -oP 'uid=\K[0-9]+')
if [ "$ROOT_ID" == "0" ]; then
    echo "✓ Root access enabled (uid=0)"
else
    echo "✗ Root access failed"
    echo "  Trying su command..."
    
    # Test 5: Try su command
    adb shell "su -c 'id'" 2>&1 | grep -q "uid=0"
    if [ ${PIPESTATUS[1]} -eq 0 ]; then
        echo "✓ Root access via su command"
    else
        echo "✗ No root access available"
    fi
fi

# Test 6: Read CPU frequency (requires root)
echo ""
echo "Test 6: Reading CPU frequency (root required)..."
FREQ=$(adb shell "su -c 'cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq'" 2>/dev/null)
if [ -n "$FREQ" ]; then
    echo "✓ Can read CPU frequency: ${FREQ}kHz"
else
    echo "✗ Cannot read CPU frequency"
fi

echo ""
echo "=== Test Complete ==="
```

Save as `test_root_access.sh` and run:

```bash
chmod +x test_root_access.sh
./test_root_access.sh
```

## References

- [Android Debug Bridge Documentation](https://developer.android.com/tools/adb)
- [Android Source - Product Interfaces](https://source.android.com/docs/core/architecture/partitions/product-interfaces)
- [Magisk Documentation](https://topjohnwu.github.io/Magisk/)
- [Libsu Library](https://github.com/topjohnwu/libsu)
- [Shizuku Project](https://shizuku.rikka.app/)

## Related Documentation

- [dhizuku-vs-shizuku.md](./dhizuku-vs-shizuku.md) - Alternative privilege escalation
- [privilege-escalation-alternatives.md](./privilege-escalation-alternatives.md) - Non-root solutions
- [shell-implementation.md](./shell-implementation.md) - Shell command execution
