<p align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/mkm/main/fastlane/metadata/android/en-US/images/icon.png" width="128" alt="MKM Logo"/>
</p>

# Changelog - MKM v1.7

## Version 1.7 (June 2026)

### ✨ New Features

#### Discord Community Card
- **Join MKM Community**: A new Discord card on the Settings page opens the MKM Discord invite with a single tap. Uses the official Discord logo and brand blurple.

#### Home: Quick Access Grid
- **One-tap navigation**: New compact card on the Home page with tiles for RAM, CPU, GPU, Storage, Power, Battery, Overlay, and Settings.
- **Lock state for privileged components**: Tiles that need root or Shizuku (CPU, GPU, RAM, Storage, Power) show a lock badge and are dimmed when access is missing. Tapping a locked tile shows a toast explaining root/Shizuku is required — same logic as the side drawer.
- **Battery, Overlay, and Settings remain unlocked** so the user can always reach them.

#### Overlay: Absolute Values Display
- **Per-metric toggles**: A master "Show Absolute Values" toggle plus per-metric sub-toggles for CPU (frequency), GPU (frequency), RAM (size), and SWAP (size). Each defaults ON.
- **Progress bars hidden** for absolute metrics since the 0–100% scale no longer applies.

#### Overlay: CPU Frequency Display Mode
- **Dropdown selector** in the Appearance section with three modes: All Cores (joined cluster frequencies, the previous behavior), Average (mean across clusters), and Max (peak cluster).

#### Overlay: Accent Color Background
- **Apply Accent to Background toggle**: Lets the accent color tint the overlay card background in addition to icons.
- **Separate background color picker**: When the toggle is on, a second color row lets the user pick a different color for the background than the accent.

#### RAM Icon Refresh
- **Dns glyph**: The RAM tile and Quick Access card now use the Dns icon (memory module / rack style) instead of the chip-style Memory icon.

### 🔧 Bug Fixes

- **Overlay state after force close**: The overlay was reporting "active" when the service had been killed. Now the app detects the dead service and either auto-restarts the overlay (if overlay permission is still held) or resets the stale flag.
- **GPU frequency on Adreno / Turnip**: Previously showed misleading `0 MHz` when the kernel blocked shell access. Now shows `N/A` with a "Root needed for freq" hint and tries 9 fallback paths before giving up.

### 📦 Technical Changes

- `OverlayService` exposes a `companion object isRunning` flag toggled in `onCreate` / `onDestroy`.
- `MainActivity.onCreate` now resumes the overlay on app start if the pref is enabled and the service is not running.
- `GpuStatus` gains `frequencyAvailable` and `freqRequiresRoot` fields.
- `GpuScripts.getGpuInfo` tries multiple sysfs paths (`cur_freq`, `cur_frequency`, `kgsl-3d0/gpuclk`, devfreq class link, soc-relative kgsl path) and emits `FREQ_AVAILABLE=0|1`.
- New overlay prefs: `show_absolute_values`, `abs_cpu`, `abs_gpu`, `abs_ram`, `abs_swap`, `cpu_freq_display`, `accent_tint_background`, `accent_bg_color_index`.
- New vector drawable: `app/src/main/res/drawable/ic_discord.xml`.

---

## Past Releases

- **[v1.6](CHANGELOG-v1.3.md)** (June 2026) - Auto-reset battery stats, monochrome themed icon, scrollable landscape drawer.
- **[v1.3](CHANGELOG-v1.3.md)** (May 2026) - Battery monitor screen, apply-on-boot, Shizuku hidden-app support.
- **[v1.2](CHANGELOG-v1.2.md)** (February 2026) - Power monitoring, calibration, battery stats.
- **[v1.1](CHANGELOG-v1.1.md)** (February 2026) - Non-root access via Shizuku, UI improvements.
- **[v1.0](v1.0.md)** (January 2026) - Initial release with root-only access, persistent swap, and performance overlay.
