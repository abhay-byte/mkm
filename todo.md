Created At: 2026-06-03T11:52:23Z
Completed At: 2026-06-03T11:52:23Z
# TODO List - Battery Monitor Improvements

- [x] **1. Charging Session Monitoring** ✅
  - [x] Update `BatteryStats` data model with gained battery and average charging current in `BatteryModels.kt`
  - [x] Enable session tracking for charging state in `BatterySessionTracker.kt`
  - [x] Handle session transitions in `onPowerConnected()` and `onPowerDisconnected()`
  - [x] Add customized charging notification format in `BatteryNotificationManager.kt`
  - [x] Update `TimeBreakdownCard` in `BatteryScreen.kt` to show charging session header

- [ ] **2. mAh stats in Notification Card**
  - [ ] Add `PREF_NOTIF_EXP_SHOW_MAH` key to `BatteryMonitorService.kt`
  - [ ] Implement mAh formula and notification text updating in `BatteryNotificationManager.kt`
  - [ ] Add mAh toggle control to `NotificationContentCard` and state in `BatteryScreen.kt`

- [x] **3. Session History Persistence & UI Card** ✅
  - [x] Create `BatteryHistoryManager.kt` utility to serialize and store session list to `battery_history.json`
  - [x] Trigger session auto-saving in `BatterySessionTracker.kt` at transitions
  - [x] Expose history state and clear operation in `BatteryViewModel.kt`
  - [x] Create expandable history card UI with rich session breakdown and styling in `BatteryScreen.kt`
  - [x] Add "Clear History" confirmation dialog in UI

- [x] **4. Power Wattage Calibration Not Applied in Battery Page, Graph & Notification** ✅

  **Bug:** The calibration multiplier is correctly applied in the Settings (Power) page but shows
  wrong (under/over-scaled) values in the Battery page wattage display, the wattage sparkline
  graph, and the persistent notification.

  **Polarity contract:** `wattageW` and `calibratedWattageW` are **signed** —
  positive (+) when charging, negative (−) when discharging. Preserved throughout.

  **Fix applied:**
  - [x] `BatteryProvider.kt` — Compute `calibratedPowerW = powerW * multiplier` (unsigned magnitude
    first), then re-derive `calibratedWattageW` by re-applying polarity from `isCharging`. This
    mirrors exactly what `PowerProvider` does, eliminating the divergence.
  - [x] `BatteryModels.kt` — Added KDoc on `BatterySnapshot` and `BatteryStats` documenting the
    signed polarity contract and calibration order-of-operations.
  - [x] `BatterySessionTracker.kt` — Replaced hardcoded `/ 10f` sparkline normalisation with a
    dynamic `peakWattageW` rolling tracker. The graph now scales to the session's actual peak
    wattage (floor 1 W) so it never clips and stays correct regardless of calibration factor.
    `peakWattageW` resets at each `startNewSessionLocked()` so sessions scale independently.
  - [x] `BatteryNotificationManager.kt` — No logic changes needed; existing `%+.2f` format on
    `calibratedWattageW` correctly shows sign once the provider produces the right value.
