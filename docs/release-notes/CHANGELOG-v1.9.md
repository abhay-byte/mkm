# Changelog - MKM v1.9

## Version 1.9 (September 2026)

### Features Added
- Global Game Boost session with policy-safe CPU/GPU tuning and storage governor support.
- Game Boost thermal limiting with max-lock release and recovery reconciliation.
- Scoped boost snapshots with boot identity fallback and transactional verification.

### Fixes & Improvements
- FPS accuracy: raw gfxinfo readings with no calibration divisor, clamped to active display refresh rate.
- Overlay FPS HUD, GPU collector paths, and session recording feed all respect the display refresh-rate cap.
- KernelSU su path support and hardened GPU vendor/frequency discovery.
- Toolchain: AGP 9.3, SDK 37, NDK 29, R8 optimization, locales en + zh-rCN.
