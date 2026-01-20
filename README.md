# Minimal Kernel Manager (MKM)

**Package:** `com.ivarna.mkm`

Android kernel management application built with Kotlin, Android Gradle Plugin 8.13.2, compile/target SDK 36, and NDK 29.

## Features

- **Persistent Swap Management**: Create and manage persistent swap files with customizable sizes for improved multitasking and memory management
- **Dual Access Methods**: Supports both Shizuku (non-root) and root access
- **Boot Persistence**: Automatically activates swap on device boot

See [docs/problem-statement.md](docs/problem-statement.md) for detailed feature specifications.

## Building
1. Ensure Android SDK platform 36 and build tools plus NDK 29 are installed and configured in `ANDROID_HOME` / `ANDROID_SDK_ROOT`.
2. Make the wrapper executable if needed: `chmod +x ./gradlew`.
3. Build the app: `./gradlew assembleDebug`.

> Note: AGP 8.13.2 and SDK 36 may not be available in standard repos; if the build fails resolving them, adjust to available versions.
