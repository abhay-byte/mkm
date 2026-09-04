# Release 1.9

<div align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/mkm/main/assets/logo.png" alt="MKM Logo" width="120"/>
</div>

## Version 1.9 (September 2026)

### Game Boost

* Added global **Game Boost** session with policy-safe CPU and GPU tuning (governor selection, max-frequency locks, storage governor support).
* Added thermal service: boost enters a thermal-limited state and releases max locks when thermally unsupported.
* Added recovery reconciliation: failed or interrupted sessions report remaining steps and restore prior tuning state.
* Added scoped snapshots with boot identity fallback so stale snapshots never apply after reboot.
* Added transactional tuning with verification and rollback on failure.
* Hardened GPU vendor table discovery: undiscovered frequencies are rejected, selection ordering is centralized.
* Added KernelSU `su` path support for in-app root execution.

### Toolchain

* Upgraded to **AGP 9.3**, `compileSdk` / `targetSdk` **37**, NDK `29.0.14206865`.
* Enabled R8 code optimization via the AGP 9.3 unified optimization DSL with keep rules under `src/release/keepRules/`.
* Limited packaged locales to `en` and `zh-rCN`.

### FPS Accuracy

* Removed the `(raw - 30) * 0.85` gfxinfo calibration in `FpsBinder`; raw `dumpsys gfxinfo framestats` values are reported directly.
* Removed the `/ 2.1f` overlay fallback divisor and the `com.ivarna.mkm` foreground exclusion in `FpsMonitor`.
* Clamped all reported FPS (overlay HUD, `GpuFpsCollector` Snapdragon/MediaTek/fallback paths, session recording feed) to the active display refresh rate from `DisplayManager` (verified 120 Hz on `mt6897`).
* Fixed FPS metric display: single value without duplicated suffix, `widthIn(min = 42.dp)` to prevent clipping.

## Past Releases

* [v1.8](https://github.com/abhay-byte/mkm/releases/tag/v1.8) (August 2026) - FPS Session Recording, root GPU collectors, overlay interval slider, power polarity fixes.
* [v1.7](https://github.com/abhay-byte/mkm/releases/tag/v1.7) (June 2026) - Discord community card, Quick Access grid, overlay appearance options, CPU frequency display mode, overlay accent color.
* [v1.6](https://github.com/abhay-byte/mkm/releases/tag/v1.6) (June 2026) - Battery stats automation, navigation drawer fixes, shell timeout fixes, Settings crash fix, monochrome icon.
* [v1.5](https://github.com/abhay-byte/mkm/releases/tag/v1.5) (June 2026) - Battery monitoring fixes, duplicate notification cleanup, live capacity and ETA improvements.
* [v1.4](https://github.com/abhay-byte/mkm/releases/tag/v1.4) (June 2026) - Battery settings page, configurable update interval, massive battery self-drain reductions (<1%), and improved awake drain logic.
* [v1.3](https://github.com/abhay-byte/mkm/releases/tag/v1.3) (May 2026) - Battery monitor, Apply on Boot, Shizuku Hidden App Support.
* [v1.2](https://github.com/abhay-byte/mkm/releases/tag/v1.2) (February 2026) - Power monitoring, calibration, battery stats.
* [v1.1](https://github.com/abhay-byte/mkm/releases/tag/v1.1) (February 2026) - Non-root access via Shizuku, UI improvements.
* [v1.0](https://github.com/abhay-byte/mkm/releases/tag/v1.0) (January 2026) - Initial release with root-only access, persistent swap, and performance overlay.
