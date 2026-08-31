Created At: 2026-06-03T11:52:23Z
Updated At: 2026-08-17

# TODO

Plan: `docs/plans/gh-15-16-17-overlay-fps-polarity.md`  
As-built review: 2026-08-17 — GH-17 done; GH-15 code done (close after matrix); GH-16 landed with **B1 sampler cadence** still open.

---

## Done

- [x] **1. Charging Session Monitoring**
- [x] **2. mAh stats in Notification Card**
  - Pref `PREF_NOTIF_EXP_SHOW_MAH`, `mahStr()` in `BatteryNotificationManager`, toggle in `NotificationSettingsScreen` (not BatteryScreen — settings were moved)
- [x] **3. Session History Persistence & UI Card**
- [x] **4. Power wattage calibration applied on Battery page / graph / notification**
- [x] **GH-13 Simplified Chinese** — `values-zh-rCN`, Settings locale picker. GitHub still open.

- [x] **5. GH-17 Overlay Update Frequency** (bug) ✅
  - [x] `loadSettings()` reads `overlay_prefs` → `update_interval`
  - [x] `updateSettings()` calls `loadSettings()` even if the overlay view is not attached
  - [x] Keep Power-page interval separate
  - [x] Single honest 500 ms floor + slider min 500; delete the 1000 ms FPS-off floor
  - [x] Localize the hardcoded `Interval: …ms` subtitle
  - Close-ready from code. No unit tests were added (completion report claimed some; they do not exist).

- [x] **7. GH-15 Power polarity on charge and discharge** (harden) ✅ code
  - [x] Shared `readAndroidCharging()` — keep `CHARGING || plugged` this pass
  - [x] `BatteryProvider.invalidate()` on plug intents; peek sticky before TTL
  - [x] `tick()` copies live `powerStatus.isCharging` onto snap (**watts and labels**)
  - [x] Clear zero-current hold on plug change (hold does **not** re-flip sign)
  - [x] Hero uses `abs(currentMa)` so it cannot print “Discharging −mA”
  - [ ] Close GitHub only after the device matrix in the plan is accepted

---

## Open

- [ ] **6. GH-16 FPS Stats** — 2nd pass fixes landed & tested
  - [x] `object FpsSessionRecorder` + `object GpuFpsCollector` (singletons)
  - [x] Start / stop only while overlay is running; do **not** persist `record_fps`
  - [x] Root-only ftrace probe; Snapdragon inflight drops + Mali `dma_fence`; count on device
  - [x] Fall back to `FpsMonitor`; do not skip MKM samples (settings screen is MKM)
  - [x] New `FpsRecordGraph` (do not reuse private `OverlayService.Sparkline` / `EfficiencyGraph`)
  - [x] `shutdown()` **before** `serviceScope.cancel()`; no `trace_pipe`
  - [x] No CSV/video this pass
  - [x] **B1** 2 s window cadence guaranteed on **every** sample path (including fallback / no-root / pid<=0 / exceptions)
  - [x] **B2** Two ftrace failures (`consecutiveFtraceFailures >= 2`) lock session to `FpsMonitor` fallback without retrying sysfs
  - [x] **B8** Gate Start on `OverlayService.isRunning`, preventing ghost sessions
  - [x] **B9 & B10** In-flight sample ftrace cancellation cleanup (`try/finally`) and probe init shutdown
  - [x] **B4 & B6** Platform tracking on session + decouple `FpsModels` with `FpsSource` enum
  - [x] **B5 & B3** Localized source and metric labels + elapsed time indicators on graph
  - [x] **B7** Real JVM unit tests added in `app/src/test` for `FpsSessionRecorder` and `BatteryCharging` (8/8 passed)
  - [x] Retest no-root record cadence (~2s verified in logcat / device run)
  - Snapdragon collector is **untested** (test phone is Mali `mt6897`)
  - Do not run host scripts alongside an app record

- [x] **8. GH-16 follow-up** — B1 + B2 + B8 + B9/B10 + unit tests completed

---

## Test on next build (GH-15)

1. Unplugged, idle → `−` watts, Discharging, red
2. Plug in (charging) → `+` watts, Charging, green — Overlay + Power + Battery + notification
3. Unplug → all four flip to `−` on Overlay/Power next poll and on Battery/notification via the **plug-driven tick** (not a 5 s cache wait, not the 30 s monitor interval)
4. Charge to 100% / pause-at-80 if the OEM does that — note whether sign matches the status bar (H1: plugged still shows `+` by design)
5. Heavy load while plugged (net drain, still plugged) — today this will stay `+` by design

## Test on next build (GH-16 after B1)

1. Overlay off → record button disabled
2. No-root, overlay on, record 15 s → `FPS_MONITOR` points ~2 s apart, not 300 instantly; HUD still updates
3. Root Mali (this device) → sequential host script vs in-app graph; stop clears `tracing_on`
