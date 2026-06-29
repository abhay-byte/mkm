# Release 1.7

<div align="center">
  <img src="https://raw.githubusercontent.com/abhay-byte/mkm/main/assets/logo.png" alt="MKM Logo" width="120"/>
</div>

## Version 1.7 (June 2026)

### Home & Navigation

* Added a Quick Access grid on the Home page for one-tap navigation to CPU, GPU, RAM, Storage, Power, Battery, Overlay, and Settings.
* Locked items show a lock badge and require root or Shizuku access.

### Overlay

* Added overlay appearance options to show absolute values (GB / MHz) for CPU, GPU, RAM, and SWAP with per-metric toggles.
* Added an overlay CPU Frequency display mode: All Cores, Average, or Max Frequency.
* Added an overlay accent color option to tint the background, with a separate background color picker.
* Fixed overlay state going stale after a force close; the overlay now auto-resumes on app start if it was enabled.

### Settings

* Added a Discord community card in Settings to join MKM users and developers.

### Device Support

* Improved GPU frequency handling on Adreno / Turnip devices: shows N/A and a "root needed" hint when the kernel blocks shell access.

### UI Polish

* Changed the RAM icon to a more representative memory module glyph.

## Past Releases

* [v1.6](https://github.com/abhay-byte/mkm/releases/tag/v1.6) (June 2026) - Battery stats automation, navigation drawer fixes, shell timeout fixes, Settings crash fix, monochrome icon.
* [v1.5](https://github.com/abhay-byte/mkm/releases/tag/v1.5) (June 2026) - Battery monitoring fixes, duplicate notification cleanup, live capacity and ETA improvements.
* [v1.4](https://github.com/abhay-byte/mkm/releases/tag/v1.4) (June 2026) - Battery settings page, configurable update interval, massive battery self-drain reductions (<1%), and improved awake drain logic.
* [v1.3](https://github.com/abhay-byte/mkm/releases/tag/v1.3) (May 2026) - Battery monitor, Apply on Boot, Shizuku Hidden App Support.
* [v1.2](https://github.com/abhay-byte/mkm/releases/tag/v1.2) (February 2026) - Power monitoring, calibration, battery stats.
* [v1.1](https://github.com/abhay-byte/mkm/releases/tag/v1.1) (February 2026) - Non-root access via Shizuku, UI improvements.
* [v1.0](https://github.com/abhay-byte/mkm/releases/tag/v1.0) (January 2026) - Initial release with root-only access, persistent swap, and performance overlay.
