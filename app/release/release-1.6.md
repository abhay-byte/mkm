# Release 1.6

<div align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/mkm/main/assets/logo.png" alt="MKM Logo" width="120"/>
</div>

## Version 1.6 (June 2026)

### Battery Stats Automation

* Added optional automatic `dumpsys batterystats --reset` triggers for charger unplug, full charge, and reboot.
* Uses the existing ShellManager path, including Shizuku when available and root fallback when available.
* Keeps reset triggers opt-in so existing battery tracking behavior stays unchanged by default.

### UI Fixes

* Made the navigation drawer scrollable so landscape users can reach every menu item.
* Kept the drawer logo and divider pinned while the menu item list scrolls.
* Fixed shell command timeout handling on API 24 and API 25 devices.

### Android Theming

* Added a monochrome app icon layer for Android 13+ themed icons.

## Past Releases

* [v1.5](https://github.com/abhay-byte/mkm/releases/tag/v1.5) (June 2026) - Battery monitoring fixes, duplicate notification cleanup, live capacity and ETA improvements.
* [v1.4](https://github.com/abhay-byte/mkm/releases/tag/v1.4) (June 2026) - Battery settings page, configurable update interval, massive battery self-drain reductions (<1%), and improved awake drain logic.
* [v1.3](https://github.com/abhay-byte/mkm/releases/tag/v1.3) (May 2026) - Battery monitor, Apply on Boot, Shizuku Hidden App Support.
* [v1.2](https://github.com/abhay-byte/mkm/releases/tag/v1.2) (February 2026) - Power monitoring, calibration, battery stats.
* [v1.1](https://github.com/abhay-byte/mkm/releases/tag/v1.1) (February 2026) - Non-root access via Shizuku, UI improvements.
* [v1.0](https://github.com/abhay-byte/mkm/releases/tag/v1.0) (January 2026) - Initial release with root-only access, persistent swap, and performance overlay.
