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
