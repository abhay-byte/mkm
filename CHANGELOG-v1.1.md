# Changelog - MKM v1.1

## Version 1.1 (February 2026)

### ✨ New Features

#### Shizuku Support
- **Non-root access**: MKM now works without root via Shizuku!
- **Automatic Sui detection**: Magisk users with Sui module get automatic support
- **Intelligent fallback**: Shizuku → Root → Error with graceful degradation
- **Permission management UI**: Clear permission request flow with status indicators

#### Access Method Indicators
- **Home screen badges**: Visual indicators for Shizuku and Root status
- **Settings integration**: Access method card showing current access mode
- **Real-time status**: Live updates when access methods change

### 🔧 Improvements

- **Better error messages**: Clear feedback when access is denied
- **Improved status indicators**: More visible access method display
- **Settings screen**: Shows current access method with management options

### 📦 Technical Changes

- Added Shizuku API 13.1.5 and Provider dependencies
- New `ShizukuManager` object for Shizuku lifecycle management
- Updated `ShellManager` with `AccessMethod` enum and priority-based execution
- New `PermissionScreen` for Shizuku permission handling
- Removed placeholder `ShizukuHelper` and `ShizukuJavaHelper` files

### 🔄 Migration from v1.0

No user action required. Existing root users will continue to work seamlessly. The app will automatically detect and use the best available access method.

### 📋 Requirements

| Method | Requirements |
|--------|--------------|
| Shizuku | Android 11+ (wireless debugging) or Root |
| Root | Magisk, KernelSU, or other root solution |
| Sui | Magisk module (auto-detected) |

---

## Version 1.0 (January 2026)

### Initial Release

- Real-time performance overlay with customizable metrics
- CPU/GPU frequency and governor management
- Persistent swap file creation and management
- Material Design 3 UI with Jetpack Compose
- Root-only access via libsu
