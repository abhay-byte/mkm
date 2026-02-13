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
    echo "  Attempting to reconnect to known device..."
    adb connect 192.168.137.242:36101
    sleep 2
    adb devices | grep -q "device$"
    if [ $? -eq 0 ]; then
        echo "✓ Device reconnected"
    else
        echo "✗ Still no device connected"
        exit 1
    fi
fi

# Test 2: Check current privilege
echo ""
echo "Test 2: Checking current privilege..."
USER_INFO=$(adb shell "id" 2>/dev/null)
if [ -z "$USER_INFO" ]; then
    echo "✗ Cannot get user info (device offline?)"
    exit 1
fi

USER_ID=$(echo "$USER_INFO" | grep -oP 'uid=\K[0-9]+')
if [ "$USER_ID" == "0" ]; then
    echo "✓ Already running as root (uid=0)"
elif [ "$USER_ID" == "2000" ]; then
    echo "● Running as shell user (uid=2000)"
else
    echo "? Unknown user ID: $USER_ID"
fi
echo "  Full info: $USER_INFO"

# Test 3: Try adb root
echo ""
echo "Test 3: Attempting adb root..."
ADB_ROOT_OUTPUT=$(adb root 2>&1)
echo "  Output: $ADB_ROOT_OUTPUT"

if echo "$ADB_ROOT_OUTPUT" | grep -q "already running as root"; then
    echo "✓ Already running as root"
elif echo "$ADB_ROOT_OUTPUT" | grep -q "restarting adbd as root"; then
    echo "● ADB daemon restarting as root..."
    sleep 3
    
    # Try to reconnect if wireless
    echo "  Attempting to reconnect..."
    adb connect 192.168.137.242:36101 2>/dev/null
    sleep 2
elif echo "$ADB_ROOT_OUTPUT" | grep -q "cannot run as root"; then
    echo "✗ Device doesn't support adb root (production build)"
else
    echo "? Unexpected output"
fi

# Test 4: Verify root access
echo ""
echo "Test 4: Verifying root access..."
ROOT_INFO=$(adb shell "id" 2>/dev/null)
if [ -z "$ROOT_INFO" ]; then
    echo "✗ Device offline, trying to reconnect..."
    adb devices
    adb connect 192.168.137.242:36101
    sleep 2
    ROOT_INFO=$(adb shell "id" 2>/dev/null)
fi

if [ -n "$ROOT_INFO" ]; then
    ROOT_ID=$(echo "$ROOT_INFO" | grep -oP 'uid=\K[0-9]+')
    if [ "$ROOT_ID" == "0" ]; then
        echo "✓ Root access enabled (uid=0)"
        echo "  Full info: $ROOT_INFO"
    else
        echo "✗ Root access via adb root failed (uid=$ROOT_ID)"
        echo "  Trying su command..."
        
        # Test 5: Try su command
        SU_INFO=$(adb shell "su -c 'id'" 2>&1)
        if echo "$SU_INFO" | grep -q "uid=0"; then
            echo "✓ Root access available via su command"
            echo "  Full info: $SU_INFO"
        elif echo "$SU_INFO" | grep -q "not found"; then
            echo "✗ su command not found (device not rooted)"
        else
            echo "✗ No root access available"
            echo "  Error: $SU_INFO"
        fi
    fi
else
    echo "✗ Cannot communicate with device"
fi

# Test 6: Read CPU frequency (requires root)
echo ""
echo "Test 6: Reading CPU frequency (root required)..."

# Try with current privileges first
FREQ=$(adb shell "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null")
if [ -n "$FREQ" ] && [ "$FREQ" != "${FREQ//[0-9]/}" ]; then
    echo "✓ Can read CPU frequency without su: ${FREQ}kHz"
else
    # Try with su
    FREQ=$(adb shell "su -c 'cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq'" 2>/dev/null)
    if [ -n "$FREQ" ] && [ "$FREQ" != "${FREQ//[0-9]/}" ]; then
        echo "✓ Can read CPU frequency with su: ${FREQ}kHz"
    else
        echo "✗ Cannot read CPU frequency"
    fi
fi

# Test 7: Check available CPU cores
echo ""
echo "Test 7: Checking CPU cores..."
CPU_COUNT=$(adb shell "ls -d /sys/devices/system/cpu/cpu[0-9]* 2>/dev/null | wc -l")
if [ -n "$CPU_COUNT" ] && [ "$CPU_COUNT" -gt 0 ]; then
    echo "✓ Found $CPU_COUNT CPU cores"
    
    # Try to read frequencies of all cores
    echo "  Reading all core frequencies..."
    for ((i=0; i<$CPU_COUNT; i++)); do
        CORE_FREQ=$(adb shell "cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq 2>/dev/null")
        if [ -n "$CORE_FREQ" ]; then
            echo "    CPU$i: ${CORE_FREQ}kHz"
        fi
    done
else
    echo "✗ Cannot detect CPU cores"
fi

# Test 8: Check GPU information
echo ""
echo "Test 8: Checking GPU information..."
GPU_FREQ=$(adb shell "cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null")
if [ -n "$GPU_FREQ" ] && [ "$GPU_FREQ" != "${GPU_FREQ//[0-9]/}" ]; then
    echo "✓ Can read GPU frequency: ${GPU_FREQ}Hz"
else
    echo "● Cannot read GPU frequency (might not be available)"
fi

# Test 9: Check thermal information
echo ""
echo "Test 9: Checking thermal zones..."
THERMAL_ZONES=$(adb shell "ls -d /sys/class/thermal/thermal_zone* 2>/dev/null | wc -l")
if [ -n "$THERMAL_ZONES" ] && [ "$THERMAL_ZONES" -gt 0 ]; then
    echo "✓ Found $THERMAL_ZONES thermal zones"
    
    # Read first thermal zone
    TEMP=$(adb shell "cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")
    if [ -n "$TEMP" ]; then
        TEMP_C=$((TEMP / 1000))
        echo "  thermal_zone0: ${TEMP_C}°C"
    fi
else
    echo "● No thermal zones found"
fi

# Test 10: Check SELinux status
echo ""
echo "Test 10: Checking SELinux status..."
SELINUX=$(adb shell "getenforce 2>/dev/null")
if [ -n "$SELINUX" ]; then
    echo "✓ SELinux status: $SELINUX"
    if [ "$SELINUX" == "Permissive" ]; then
        echo "  (Permissive mode allows more operations)"
    elif [ "$SELINUX" == "Enforcing" ]; then
        echo "  (Enforcing mode may restrict some operations)"
    fi
else
    echo "● Cannot check SELinux status"
fi

echo ""
echo "=== Test Complete ==="
echo ""
echo "Summary:"
echo "--------"
if [ "$ROOT_ID" == "0" ] || echo "$SU_INFO" | grep -q "uid=0"; then
    echo "✓ Root access is available"
    echo "  You can use root commands for MKM development"
else
    echo "✗ Root access is NOT available"
    echo "  Consider using Shizuku/Dhizuku as alternative"
    echo "  See docs/dhizuku-vs-shizuku.md for more information"
fi
