---
- id: T1
  title: Auto-reset system battery stats (dumpsys batterystats --reset) on charger unplug / 100% / reboot
  type: feature
  priority: medium
  difficulty: medium
  why: From GH-9 — user is unrooted, uses shizuku; wants BBS-like reset on 3 triggers so wakelock/UID analysis stays fresh
  really_needed: Workaround exists (manual adb/shizuku reset); per-session aggregation in app is unrelated and stays as-is
  impact: AndroidManifest (new receiver), new BatteryStatsResetReceiver.kt, new BatteryStatsResetPrefs.kt, SettingsScreen per-trigger toggle UI
  followups: T? (followup for "Reset now" button in BatteryScreen), T? (followup for reset-fired notification)
  images: null
  github_ref: GH-9
  plan: |
    Goal: Auto-execute `dumpsys batterystats --reset` on 3 user-enabled triggers
    using existing ShellManager fallback (shizuku → root → local), with per-trigger
    toggles in Settings (default OFF for safety).

    Approach
    - New BroadcastReceiver with 3 intent-filter actions in one class
    - Each trigger is gated by a SharedPreferences boolean; default false
    - Reuse ShellManager.exec("dumpsys batterystats --reset") — no new exec class
      (ShellManager already does shizuku→root→local fallback with proper timeout)
    - Use ACTION_BATTERY_LOW as a NO for "100% full" — use ACTION_BATTERY_CHANGED
      with EXTRA_STATUS == BATTERY_STATUS_FULL instead (stuck-full while plugged in)
    - Use ACTION_POWER_DISCONNECTED for charger unplug

    Files
    - NEW app/src/main/java/com/ivarna/mkm/receiver/BatteryStatsResetReceiver.kt
      — multi-action receiver, action-gated, calls ShellManager.exec in coroutine
    - NEW app/src/main/java/com/ivarna/mkm/utils/BatteryStatsResetPrefs.kt
      — 3 boolean toggles + isAnyEnabled() helper, mirrors BootSettingsManager pattern
    - MODIFY app/src/main/AndroidManifest.xml
      — register receiver with 3 <action> entries in one <intent-filter>
      — exported=true, no extra permission needed (BOOT_COMPLETED is system-sent)
    - MODIFY app/src/main/java/com/ivarna/mkm/ui/screens/SettingsScreen.kt
      — add a "Battery stats reset" section with 3 Switch composables
      — show "Will use: shizuku | root | unavailable" status hint below the section

    Edge cases
    - No shizuku + no root → exec returns -1, log warning, no crash, no toast
      (silent fail is better than spamming toasts from BOOT_COMPLETED)
    - ACTION_BATTERY_CHANGED fires often; gate on EXTRA_STATUS == FULL only
    - ACTION_POWER_DISCONNECTED fires once on disconnect, idempotent
    - BOOT_COMPLETED: registered in manifest, manifest-merged permission OK
    - Receiver runs in main thread → wrap exec in a coroutine on IO dispatcher
    - Pref reads inside receiver are fast SharedPreferences, no IO block concern
    - User uninstalls shizuku after enabling toggle → next trigger silently no-ops

    Test plan
    - Unit: BatteryStatsResetPrefs read/write round-trip
    - Manual A: enable "Reset on charger unplug" with shizuku granted
      → unplug charger → check `adb logcat | grep MKM` for success line
    - Manual B: enable "Reset on 100%" with shizuku
      → charge to 100% → verify reset
    - Manual C: enable "Reset on reboot" with shizuku
      → reboot → verify post-boot reset
    - Manual D: disable all 3 triggers → fire events → verify no reset
    - Manual E: enable with neither shizuku nor root → verify silent no-op

    Open questions
    - Should we add a "Reset now" button on BatteryScreen for manual trigger? (defer to T?)
    - Should reset events emit a notification? (defer to T?)
    - Some OEMs strip `dumpsys batterystats` — fall back gracefully? (silent fail OK)
  notes: |
    Dual execution path (handled by ShellManager — no new class needed):
    - root: ShellManager.exec → execRoot → libsu Shell.cmd
    - shizuku: ShellManager.exec → execShizuku → reflection on Shizuku.newProcess
    - fallback: local shell (which will fail for dumpsys batterystats --reset
      without root, returns -1, we silently ignore)
    Execution priority: ShellManager handles this — shizuku first if hasPermission,
    else root if Shell.getShell().isRoot, else local.
    Triggers (per-trigger toggle in Settings, default OFF):
    - ACTION_POWER_DISCONNECTED
    - ACTION_BATTERY_CHANGED with EXTRA_STATUS == BATTERY_STATUS_FULL
    - BOOT_COMPLETED (handled in same receiver)
---
