# Release 1.8

<div align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/mkm/main/assets/logo.png" alt="MKM Logo" width="120"/>
</div>

## Version 1.8 (August 2026)

### FPS Recording & Performance Metrics (GH-16)

* Added in-memory **FPS Session Recording** with live Canvas graphing (`FpsRecordGraph`), real-time summary metric pills (Current, Avg, Min, Max, Samples count), and elapsed time view.
* Added root-level GPU frame collectors for **Snapdragon/Adreno** (inflight command batch drop counting via KGSL tracing) and **MediaTek/Mali** (`dma_fence_signaled` timeline event counting).
* Added robust non-root fallback to `FpsMonitor` with a guaranteed 2-second sampling cadence and automatic fallback lock-in after consecutive failures.
* Gated recording on active overlay life to prevent ghost sessions.
* Added idempotent cleanup and cancellation safety for kernel ftrace tracing.

### Overlay HUD Improvements (GH-17)

* Fixed the Overlay update interval preference slider (`500 ms – 5000 ms`) so it directly drives the overlay monitor loop instead of reading Power calibration settings.
* Lowered minimum update interval floor from 1000 ms to 500 ms.
* Localized the update interval label.

### Power & Battery Polarity (GH-15)

* Fixed power wattage and current polarity (`+/-` sign) on charge and discharge across Overlay PWR, Power Screen, Battery Screen, Settings, and Notifications.
* Mitigated snapshot cache latency: plug and unplug events immediately invalidate provider caches and trigger a tick update.
* Live `isCharging` state copied during session tracking ticks, clearing zero-current hold on plug changes.

### Localization & Testing

* Expanded Simplified Chinese (`zh-rCN`) translations across all new FPS recording, overlay interval, and power metrics.
* Added comprehensive JVM unit test suites (`FpsSessionRecorderTest`, `BatteryChargingTest`) under `app/src/test`.

## Past Releases

* [v1.7](https://github.com/abhay-byte/mkm/releases/tag/v1.7) (June 2026) - Discord community card, Quick Access grid, overlay appearance options, CPU frequency display mode, overlay accent color.
* [v1.6](https://github.com/abhay-byte/mkm/releases/tag/v1.6) (June 2026) - Battery stats automation, navigation drawer fixes, shell timeout fixes, Settings crash fix, monochrome icon.
* [v1.5](https://github.com/abhay-byte/mkm/releases/tag/v1.5) (June 2026) - Battery monitoring fixes, duplicate notification cleanup, live capacity and ETA improvements.
* [v1.4](https://github.com/abhay-byte/mkm/releases/tag/v1.4) (June 2026) - Battery settings page, configurable update interval, massive battery self-drain reductions (<1%), and improved awake drain logic.
* [v1.3](https://github.com/abhay-byte/mkm/releases/tag/v1.3) (May 2026) - Battery monitor, Apply on Boot, Shizuku Hidden App Support.
* [v1.2](https://github.com/abhay-byte/mkm/releases/tag/v1.2) (February 2026) - Power monitoring, calibration, battery stats.
* [v1.1](https://github.com/abhay-byte/mkm/releases/tag/v1.1) (February 2026) - Non-root access via Shizuku, UI improvements.
* [v1.0](https://github.com/abhay-byte/mkm/releases/tag/v1.0) (January 2026) - Initial release with root-only access, persistent swap, and performance overlay.
