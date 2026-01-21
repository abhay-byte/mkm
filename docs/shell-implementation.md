# Shell Execution Component

This document describes the Shell Execution component implemented in the Swap Creator app. This component provides a unified interface for executing shell commands with support for both **Root (su)** and **Shizuku**.

## Architecture

The shell execution system consists of three main components:

1.  **`ShizukuHelper`**: Manages Shizuku-specific logic such as availability checks, permission requests, and binder lifecycle.
2.  **`ShellManager`**: A unified executor that handles command execution logic. It follows a priority-based fallback mechanism:
    *   **Priority 1: Shizuku** - If Shizuku is available and has permissions, commands are executed via Shizuku's remote process.
    *   **Priority 2: Root** - If Shizuku is unavailable but the device is rooted, it falls back to `libsu` for root execution.
    *   **Priority 3: Local Shell** - If neither Shizuku nor Root is available, commands are run in the app's local shell (limited permissions).
3.  **`ShellScripts`**: A repository of predefined kernel-level scripts for common tasks (CPU governors, swap management, etc.).

## Files Created

- `app/src/main/java/com/ivarna/mkm/shell/ShizukuHelper.kt`
- `app/src/main/java/com/ivarna/mkm/shell/ShellManager.kt`
- `app/src/main/java/com/ivarna/mkm/shell/ShellScripts.kt`

## Dependencies Added

- `dev.rikka.shizuku:api:14.0.1`: Core Shizuku API.
- `dev.rikka.shizuku:provider:14.0.1`: Shizuku provider for binder communication.
- `com.github.topjohnwu.libsu:core:6.0.0`: Industry-standard library for managing root shells.

## Configuration

### Manifest
The following was added to `AndroidManifest.xml`:
- `rikka.shizuku.ShizukuProvider`: To enable Shizuku communication.
- `moe.shizuku.manager.permission.API_V23`: Permission to access Shizuku.

## Usage Example

### Executing a simple command
```kotlin
val result = ShellManager.exec("id")
if (result.isSuccess) {
    Log.d("Shell", "Output: ${result.stdout}")
} else {
    Log.e("Shell", "Error: ${result.stderr}")
}
```

### Applying a script
```kotlin
val script = ShellScripts.createSwap("/data/local/tmp/swapfile", 1024)
val result = ShellManager.exec(script)
```

## Considerations
- **Threading**: Shell commands should always be executed on a background thread (e.g., `Dispatchers.IO`) to avoid ANRs.
- **SELinux**: Some paths in `/sys` or `/proc` might still be restricted by SELinux even with Shizuku (which runs as the `shell` UID). Root (UID 0) typically bypasses these restrictions more effectively.
