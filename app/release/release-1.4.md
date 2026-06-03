Changelog - MKM v1.4

## Version 1.4 (June 2026)
### ✨ New Features

#### Battery Settings and Customisation
* Added a comprehensive battery notification settings page.
* Added a "Customise Notification" navigation card to Settings.
* Configurable battery update interval.
* Notification customization options, including tap-to-open and toggles for various stats.
* Unified the battery activity card in Settings.

### ⚡ Performance Improvements

#### Battery Drain Reductions
* Reduced MKM self-drain from 5-6% to <1% (massive improvement!).
* Switched to drain-based percentage tracking for more accurate estimates.

### 🔧 Bug Fixes
* Fixed wattage sign to use Android charging state instead of kernel current direction.
* Fixed awake drain calculation logic to handle screen states properly.
* Fixed charging notification preferences not being respected.
* Fixed header charging rate display.
* Removed the inaccurate mAh toggle feature.
* Updated side drawer to visually indicate locked pages when Root/Shizuku is unavailable.
* Removed redundant "Frequent Operations" section from the Home screen.

--------

## Past Releases

* [v1.3](https://github.com/abhay-byte/mkm/releases/tag/v1.3) (May 2026) - Battery monitor, Apply on Boot, Shizuku Hidden App Support.
* [v1.2](https://github.com/abhay-byte/mkm/releases/tag/v1.2) (February 2026) - Power monitoring, calibration, battery stats.
* [v1.1](https://github.com/abhay-byte/mkm/releases/tag/v1.1) (February 2026) - Non-root access via Shizuku, UI improvements.
* [v1.0](https://github.com/abhay-byte/mkm/releases/tag/v1.0) (January 2026) - Initial release with root-only access, persistent swap, and performance overlay.
