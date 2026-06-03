# Battery Monitor Features - Implementation Plan

This document details the architectural plan to add charging session monitoring, mAh stats in notifications, and session history persistence.

## 1. Charging Session Monitoring
- **Goal:** Track battery metrics while charging, showing a live charging status card in the persistent notification rather than standard "Plugged in. Monitoring will resume on disconnect."
- **Logic in `BatterySessionTracker.kt`**:
  - Keep `isSessionActive = true` during charging sessions.
  - On `ACTION_POWER_CONNECTED`: Finalize discharging session, save it to history, and start a new **charging session**.
  - On `ACTION_POWER_DISCONNECTED`: Finalize charging session, save it to history, and start a new **discharging session**.
- **Metrics**: Track elapsed time, percentage gained (+X%), and charging wattage (W).

## 2. mAh stats in Notification Card
- **Goal:** Display battery consumption in mAh in the notification expanded content.
- **Preference Key:** `BatteryMonitorService.PREF_NOTIF_EXP_SHOW_MAH = "notif_exp_show_mah"` (Boolean).
- **Calculation in `BatteryNotificationManager.kt`**:
  - `capacity = stats.estimatedCapacityMah ?: stats.ratedCapacityMah`
  - `mAh = (drainPercent / 100f) * capacity`
  - Render as: `Screen on: 1h 20m (4.5% drain · 225 mAh)`.
- **UI:** Toggle switch added to `NotificationContentCard` under Expanded Content settings on the Battery page.

## 3. Session History Persistence & UI Card
- **Goal:** Persist charging and discharging sessions and present them in a rich history list.
- **Utility `BatteryHistoryManager.kt`**:
  - Serializes `BatteryStats` to/from JSON (`battery_history.json`) using Android's native `org.json.JSONObject`.
  - Save session automatically at charging/discharging state boundaries.
- **ViewModel `BatteryViewModel.kt`**:
  - Expose a `StateFlow<List<BatteryStats>>` loaded from history.
  - Provide a clean history trigger.
- **UI in `BatteryScreen.kt`**:
  - Add an expandable `"SESSION HISTORY"` card listing historical sessions.
  - Stylize rows to clearly show discharging (lost %) vs charging (gained %) with rich stat breakdowns.
