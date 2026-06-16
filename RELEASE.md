# v1.7

## What's Changed
- Discord community card in Settings.
- Quick Access grid on the Home page for one-tap navigation to each component.
- Locked Quick Access tiles for components that need root or Shizuku.
- Overlay: per-metric absolute value display for CPU, GPU, RAM, and SWAP.
- Overlay: CPU Frequency display mode selector (All Cores, Average, Max).
- Overlay: accent color can now tint the background, with a separate background color picker.
- RAM icon updated to a more representative memory glyph.
- Overlay auto-resumes on app start when it was enabled before a force close.
- GPU frequency on Adreno / Turnip shows N/A with a root-required hint when the kernel blocks shell access.

## Items Shipped
- Discord: Join the MKM community card in Settings.
- Home: Quick Access grid with per-component lock state.
- Overlay: Absolute Values toggle with per-metric sub-toggles.
- Overlay: CPU Frequency Display dropdown (All Cores / Average / Max).
- Overlay: Apply Accent to Background + separate background color picker.
- Overlay: Auto-resume on app start if previously enabled.
- GPU: Graceful N/A when shell cannot read frequency.

## Migration Notes
- No migration required.