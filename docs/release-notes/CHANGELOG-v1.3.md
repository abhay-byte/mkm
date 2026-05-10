<p align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/mkm/main/fastlane/metadata/android/en-US/images/icon.png" width="128" alt="MKM Logo"/>
</p>

# Changelog - MKM v1.3

## Version 1.3 (May 2026)

### ✨ New Features

#### Battery Monitor (Fixes #3)
- **Dedicated Battery Screen**: Full battery stats page with real-time graphs, capacity info, and charge history.
- **Persistent Notification**: Foreground notification service showing live wattage, polarity, and estimated time remaining/to full.
- **Estimated Battery Life**: Calculates remaining usage time (discharging) or time-to-full (charging) using stable rolling averages.
- **Signed Wattage with Polarity**: Displays `+` (charging) and `−` (discharging) with colour-coded indicators across the app and overlay.
- **Power Calibration in Settings**: Moved calibration card to Settings with live raw vs. calibrated wattage preview and reset button.

#### Apply on Boot (Fixes #5)
- **Automatic settings restore**: Kernel configurations (CPU, GPU, RAM, Storage) are automatically re-applied after every reboot when enabled.
- **10-second countdown notification**: A foreground notification with a progress bar counts down before applying, giving users time to intervene.
- **Per-category toggles**: Each section (CPU, GPU, RAM, Storage) has its own "Apply on Boot" toggle — enable only what you need.
- **`BootApplyService`**: New foreground service started by `BootReceiver` on `ACTION_BOOT_COMPLETED` that applies all enabled settings via the existing shell layer.

#### Shizuku Hidden App Support (Fixes #6)
- **Binder-based detection**: MKM can now detect and use Shizuku even when it is hidden via AppHider, Shelter, or similar tools.
- **New "Hidden" status**: Settings → Access Method card shows **"Shizuku (Hidden) — tap to grant"** in amber when the binder is alive but the package is invisible to PackageManager.
- **Dedicated permission screen state**: A new "Shizuku is Hidden" screen guides users to grant permission with a working **Grant Permission** button.

### 🔧 Bug Fixes

- **BootToggleCard text overlap**: Fixed the subtitle text running underneath the toggle switch on narrow screens. Long subtitles now wrap cleanly without overlapping the `Switch`.
- **Battery wattage stability**: Guarded estimated-time calculation against bad early data; deduplicated wattage reads between notification and overlay.
- **Wattage fallback**: Added a fallback path for devices where the primary power sysfs node is unavailable.

### 📦 Technical Changes

- New `BootApplyService` and `BootSettingsManager` for per-category boot persistence.
- `ShizukuManager.isHidden()` — returns `true` when `isRunning()` is `true` but `isInstalled()` is `false`.
- `isAvailable()` simplified to `isRunning()` (live binder is sufficient).
- Added `PermissionStatus.Hidden` enum value; permission flow priority re-ordered.

---

## Past Releases

- **[v1.2](CHANGELOG-v1.2.md)** (February 2026) - Power monitoring, calibration, battery stats.
- **[v1.1](CHANGELOG-v1.1.md)** (February 2026) - Non-root access via Shizuku, UI improvements.
- **[v1.0](CHANGELOG-v1.0.md)** (January 2026) - Initial release with root-only access, persistent swap, and performance overlay.
