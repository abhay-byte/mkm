# Minimal Kernel Manager v1.4

## Features
- Added comprehensive battery notification settings page
- Unified battery activity card in Settings
- Configurable battery update interval
- Notification customization (tap-to-open, toggles for stats)
- Added "Customise Notification" navigation card to Settings

## Performance
- Reduced MKM self-drain from 5-6% to <1% (massive improvement!)
- Drain-based percentage tracking for more accurate estimates

## Fixes
- Fixed wattage sign to use Android charging state instead of kernel current direction
- Fixed awake drain calculation logic
- Fixed charging notification preferences not being respected
- Fixed header charging rate display
- Removed inaccurate mAh toggle feature
- Updated drawer to visually indicate locked pages when Root/Shizuku is unavailable
- Removed redundant "Frequent Operations" section from the Home screen
