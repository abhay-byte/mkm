# v1.8

## What's Changed
- FPS Session Recording with live Canvas graphing (`FpsRecordGraph`) and summary metrics.
- Root-level hardware GPU frame rate collectors for Snapdragon (Adreno inflight) and MediaTek (Mali dma_fence).
- Non-root FPS Monitor fallback with guaranteed 2-second sampling cadence.
- Overlay Update Frequency slider (500 ms – 5000 ms) now directly drives overlay monitor loop.
- Power wattage & current polarity on charge/discharge fixed across Overlay, Power, Battery, and Notifications.
- Instant cache invalidation and live state synchronization on plug/unplug events.
- Expanded Simplified Chinese translations for all new overlay and FPS recording UI.
- Comprehensive unit test suites for session recording and charging evaluation.

## Items Shipped
- FPS: In-memory session recorder, root ftrace collectors, non-root fallback, real-time graph.
- Overlay: Update interval preference slider support and 500ms floor.
- Power/Battery: Signed wattage and charging state synchronization.
- Testing: JVM unit tests for FpsSessionRecorder and BatteryCharging.
- Localization: Complete zh-rCN translations for new features.

## Migration Notes
- No migration required.