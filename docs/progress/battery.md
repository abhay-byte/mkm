# Battery System Progress

> Last updated: 2026-05-03

## Status: Core Implemented

### Completed

#### Battery Stats Screen (`BatteryScreen.kt`)
- [x] Hero card showing battery %, temperature, voltage, status
- [x] **Wattage display** in hero card (root-first sysfs reading, fallback BatteryManager)
- [x] **Capacity card** with:
  - Rated (design) capacity in mAh
  - Estimated full capacity in mAh
  - Battery health progress bar (estimated / rated)
- [x] Drain rate grid (Active / Idle drain per hour)
- [x] Session breakdown card with:
  - Screen on time + percentage
  - Screen off time + percentage
  - Deep sleep time + percentage
  - Awake time + percentage
- [x] Session active / ended state handling
- [x] Notification toggle card in UI with **runtime permission request** (Android 13+)

#### Battery Provider (`BatteryProvider.kt`)
- [x] **Root-first current/voltage reading** via sysfs shell, fallback to `BatteryManager`
- [x] Wattage computation: `W = |mA| × mV / 1,000,000`
- [x] Capacity reading from sysfs: `charge_full_design`, `charge_full`

#### Battery Session Tracker (`BatterySessionTracker.kt`)
- [x] **Live updates every 1 second** (was 2s)
- [x] Emits full stats via StateFlow for both active session and charging state

#### Battery ViewModel (`BatteryViewModel.kt`)
- [x] `BatteryStats` data model integration
- [x] `showNotification` state management
- [x] `setNotificationEnabled()` toggle

#### Notification (`BatteryNotificationManager.kt`)
- [x] Persistent notification with rich stats
- [x] Wattage shown in notification title
- [x] **Fixed (2026-05-03)**: Added runtime `POST_NOTIFICATIONS` permission request on Android 13+

#### Navigation
- [x] `Screen.Battery` route registered in NavHost
- [x] Drawer menu item present
- [x] **Fixed (2026-05-03)**: Click now navigates to Battery page (was blocked by `isAccessGranted`)

### Pending / Planned

- [ ] Tapping notification opens Battery screen (PendingIntent)
- [ ] Custom notification layout with progress bar for battery %
- [ ] Verify drain rate calculations against real usage over longer sessions
- [ ] Validate session time tracking across device sleep

### Files

| File | Purpose |
|------|---------|
| `ui/screens/BatteryScreen.kt` | UI composables |
| `ui/viewmodel/BatteryViewModel.kt` | State management |
| `data/model/BatteryStats.kt` | Data class |
| `data/provider/BatteryProvider.kt` | Raw battery readings |
| `service/BatterySessionTracker.kt` | Session tracking & aggregation |
| `utils/BatteryNotificationManager.kt` | Notification builder |
| `MainActivity.kt` | Navigation registration |
