# Shizuku + Root Fallback Implementation

## Problem
The app was failing to execute system commands because:
1. Shizuku detection worked but command execution hung
2. No fallback to root when Shizuku failed
3. Process streams weren't being handled properly

## Solution
Implemented intelligent fallback mechanism in `ShellManager`:
- **Primary**: Try Shizuku first (wireless ADB privilege, uid=2000)
- **Fallback**: Use root access via libsu (uid=0)
- **Last resort**: Local shell without privileges (uid=app)

## Technical Details

### Shizuku Execution
- Uses reflection to access `Shizuku.newProcess()` (private in API 13.x)
- Closes stdin immediately to prevent process hanging
- Reads stdout/stderr in parallel threads to avoid deadlock
- 10-second timeout with proper process cleanup

### Fallback Logic
```kotlin
fun exec(command: String): CommandResult {
    if (hasShizuku()) {
        val result = execShizuku(command)
        if (result.isSuccess || result.exitCode != -1) {
            return result
        }
        // Shizuku failed, try root
    }
    
    return when (getAvailableMethod()) {
        AccessMethod.ROOT -> execRoot(command)
        AccessMethod.LOCAL -> execLocal(command)
    }
}
```

### Key Fixes
1. **Process stream handling**: Read streams in parallel threads before `waitFor()`
2. **Stdin closure**: Close `process.outputStream` immediately to prevent blocking
3. **Fallback on failure**: Don't return Shizuku errors; fall back to root instead
4. **Timeout handling**: Proper 10-second timeout with process destruction

## Testing Results
Successfully tested on:
- Device: 2311DRK48I (Xiaomi/Redmi)
- Android: 16 (userdebug)
- Wireless ADB: Connected at root level (uid=0)
- Shizuku: v13.1.5, server running but app permission not granted

### Execution Flow
1. App detects Shizuku binder connected
2. Attempts Shizuku command execution
3. Command times out (permission not properly granted)
4. Falls back to root via libsu
5. Successfully reads /proc/stat, thermal zones, etc.
6. No more "Permission denied" errors

## Log Evidence
```
CpuUtilizationProvider: Failed to read /proc/stat: Shizuku execution failed: process hasn't exited
[Fallback to root]
CpuUtilizationProvider: CPU usage updated from /proc/stat: 97%
```

## Files Modified
- `app/src/main/java/com/ivarna/mkm/shell/ShellManager.kt`
  - Added `execShizuku()` with reflection-based execution
  - Added `execShizukuStreaming()` for streaming output
  - Implemented fallback logic in `exec()` and `execStreaming()`
  - Proper process stream handling with parallel threads

## Future Improvements
1. Request Shizuku permission explicitly in app UI
2. Implement UserService API for better Shizuku integration
3. Add permission check before Shizuku execution attempts
4. Show user which execution method is active (Shizuku/Root/Local)

## Related Documentation
- [rooted-adb.md](rooted-adb.md) - Rooted ADB connection guide
- [shizuku-ram-integration.md](shizuku-ram-integration.md) - Shizuku RAM page integration
