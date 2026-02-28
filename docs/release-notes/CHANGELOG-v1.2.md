<p align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/mkm/main/fastlane/metadata/android/en-US/images/icon.png" width="128" alt="MKM Logo"/>
</p>

# Changelog - MKM v1.2

## Version 1.2 (February 2026)

### ✨ New Features

#### Power Monitoring & Calibration
- **Power Calibration Card**: Added a compact power calibration card to the Overlay Settings screen.
- **Live Readings**: View live raw and calibrated power readings with polarity (charging/discharging).
- **Custom Multiplier**: Set a custom multiplier for accurate power readings across different devices.
- **Polarity Indicators**: Added `+` and `-` signs with green/red status pills for charging and discharging states across the app and overlay.

#### Battery % Fixes
- **Non-Root & MediaTek Support**: Fixed battery percentage always showing 0% on non-root devices, including MediaTek chipsets.
- **Real-Time Voltage**: Now uses real voltage data instead of a hardcoded constant for more accurate power calculations.

### 🔧 Improvements
- **State Management**: Improved internal state management using a single source of truth for the calibration multiplier, ensuring consistency across all screens.

---

## Past Releases

- **[v1.1](CHANGELOG-v1.1.md)** (February 2026) - Non-root access via Shizuku, UI improvements.
- **[v1.0](CHANGELOG-v1.0.md)** (January 2026) - Initial Release with Root-only access, persistent swap, and performance overlay.
