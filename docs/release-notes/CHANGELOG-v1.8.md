# Changelog - MKM v1.8

## Version 1.8 (August 2026)

### Features Added
- FPS Session Recording with live Canvas graphing (`FpsRecordGraph`) and summary metric pills.
- Root-level hardware GPU frame rate collectors for Snapdragon (Adreno inflight) and MediaTek (Mali dma_fence).
- Non-root FPS Monitor fallback with guaranteed 2-second sampling cadence.
- Elapsed time and time-axis indicators on the FPS recording chart.
- Complete Simplified Chinese translations for all new overlay and FPS recording UI.

### Fixes & Improvements
- Overlay Update Frequency slider (500 ms – 5000 ms) now directly controls HUD refresh interval.
- Removed arbitrary 1000 ms floor for FPS-off mode down to 500 ms.
- Fixed power wattage and current polarity on charge/discharge across Overlay, Power, Battery, and Notifications.
- Fixed battery charging session tracking with instant cache invalidation on plug/unplug events.
- Added comprehensive unit test suites for session recording and charging evaluation.
