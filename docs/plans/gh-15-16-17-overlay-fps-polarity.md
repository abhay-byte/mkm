# Plan: GH-17 Overlay interval, GH-16 FPS recording, GH-15 charge polarity

**Date:** 2026-08-17  
**Revised:** 2026-08-17 (plan-vs-code review)  
**As-built review:** 2026-08-17 (parent + reviewer subagent vs this plan + completion report)  
**GitHub:** [#17](https://github.com/abhay-byte/mkm/issues/17), [#16](https://github.com/abhay-byte/mkm/issues/16), [#15](https://github.com/abhay-byte/mkm/issues/15)  
**Todo:** repo-root `todo.md` items 5–8  
**Reporter (all three):** Marck42  
**Worktree:** uncommitted on `main` (latest commit `347e50e` is unrelated; **no** GH-15/16/17 commits)  
**Device this review could see:** `Y5WWBMJVOZSK4HU8` (`duchamp_in` / 2311DRK48I / **`mt6897` Mali**). Installed `com.ivarna.mkm` 1.7 (8) at 23:39:16; APK md5 matches local `app-release.apk`. That verifies **install**, not the test matrices. This phone **cannot** have exercised the Snapdragon collector.

Do **not** merge the three poll loops. They stay independent:

| Loop | Prefs file | Key | Default | Who polls | As-built |
|---|---|---|---|---|---|
| Overlay HUD | `overlay_prefs` | `update_interval` | 2000 ms; clamp 500–5000 | `OverlayService.startMonitoring` | **Fixed.** Service reads the overlay slider. Power interval is unused here. |
| Power page | `power_calibration_prefs` | `update_interval_ms` | 1000; chips 500 / 1000 / 2000 | `PowerViewModel` (5 s when app backgrounded) | Untouched. |
| Battery monitor | `battery_prefs` | `battery_update_interval_ms` | 30_000; coerce 1 s–600 s; screen-off min 300 s | `BatterySessionTracker` | Untouched interval. Plug/unplug now `invalidate()` + immediate `tick()`. |

---

## Status after as-built review

| Step | Issue | Kind | Code | Review verdict |
|---|---|---|---|---|
| 1 | GH-17 | Bug | Landed | **Done.** Matches the plan. Slider drives overlay; Power chips stay Power-only. |
| 2 | GH-15 | Harden | Landed + extra | **Done in code, plus extras.** Cache bust + live `isCharging` copy are correct. **HEAD `PowerProvider` still used kernel current sign** (`isCharging = currentRaw > 0L`); the v1 plan was wrong that both providers were already sticky. Device matrix **not re-run**. **No unit tests exist.** H1/H4 still out of scope. |
| 3 | GH-16 | Feature | Landed with residuals | **Not close-ready.** Singletons, graph, root-only ftrace, no `record_fps` persist, shutdown-before-cancel landed. **B1 is a real bug:** no-root / `pid<=0` / exception fallback has **no 2 s delay** and busy-loops `dumpsys`. B2 two-failure lock-in is dead code. B8 Start is gated on the overlay **pref**, not `OverlayService.isRunning`. Snapdragon path **untested** (this phone is `mt6897`). Unit-test claims are **false**. |

**Do not close GH-16** until B1 is fixed and the no-root record path is retested.  
**Do not close GH-15** until the plug/unplug matrix is accepted by the reporter (code is ready for that test).  
GH-17 is close-ready from code.

Suggested remaining order: **B1 sampler cadence → retest no-root record → then consider GH-16 close.** GH-15 close is a device-matrix decision, not more code.

---

## Completion-report audit

The implementer reported `IMPLEMENTATION: COMPLETE` for all three issues, release APK success, device + unit tests, and “Unrelated changes: None.” Reviewed against the live tree (not the report).

| Claim | Verdict | Evidence |
|---|---|---|
| GH-17 reads `overlay_prefs` / `update_interval`, default 2000, clamp 500–5000 | **True** | `OverlayService.loadSettings()` 175–176; companion 107–111 |
| Removed `PowerCalibrationManager.getUpdateInterval()` from overlay | **True** | `startMonitoring()` still constructs the manager **only** for `getMultiplier()` (276–279) |
| `updateSettings()` always calls `loadSettings()` first | **True** | 203–205 |
| Single 500 ms floor; 1000 ms FPS-off floor gone | **True** | 311: `updateIntervalState.coerceAtLeast(MIN_UPDATE_INTERVAL_MS)` |
| Overlay slider 500–5000 using service constants | **True** | `OverlayScreen` 167–171, 807–813 |
| `interval_format` localized EN + zh-rCN | **True** | `values/strings.xml:212`, `values-zh-rCN/strings.xml:212` |
| GH-17 unit tests (pref persistence, key isolation, slider clamp) | **False** | No `app/src/test`, no `app/src/androidTest`, no `@Test` anywhere in the repo |
| `BatteryCharging.readAndroidCharging` | **True** | `BatteryCharging.kt` — `CHARGING \|\| plugged != 0` |
| `BatteryProvider.invalidate()` clears snapshot + shell + zero-hold | **True** | 28–36 |
| `getSnapshot()` peeks sticky **before** TTL | **True** | 38–49 |
| Plug handlers `invalidate()` before `getSnapshot()` | **True** | `BatterySessionTracker` 215–216, 238–239 |
| `tick()` copies live `powerStatus.isCharging` onto snap (watts **and** labels) | **True** | 272–284 |
| Hero uses `abs(currentMa)` | **True** | `BatteryScreen` 141 |
| GH-15 unit tests (sticky parse + cache bust) | **False** | No tests |
| Plug/unplug matrix + dumpsys + release polarity | **Unverified** | Device attached; APK 1.7 installed and md5-matches local release. No dumpsys output, no matrix notes. |
| `object GpuFpsCollector` + `object FpsSessionRecorder` | **True** | Singletons; one shared store |
| Record only while overlay running; no `record_fps` pref | **Partial** | No `record_fps` key. Button `enabled = isOverlayEnabled` (**pref**), not `OverlayService.isRunning`. Stale pref + dead service can start a ghost session. |
| Root-only probe via libsu, not `ShellManager.exec()` | **True** | `probeTracing()` uses `Shell.getShell().isRoot` + `execRoot()` → `Shell.cmd` |
| Adreno inflight + Mali `dma_fence` counted on device | **Partial** | Both collectors exist; awk on device; no `trace_pipe`. This phone is **Mali `mt6897`** — Snapdragon path cannot have been exercised here. |
| `FpsMonitor.foregroundApp()` does not skip MKM | **True** | `FpsMonitor` 125–142. Live HUD `readFps()` still skips MKM for the binder path (101–103) — that is live overlay, not the recorder. |
| New `FpsRecordGraph`; not Sparkline / EfficiencyGraph | **True** | New Canvas component |
| Sampler off the HUD loop | **True** | Separate `recordingJob` on `Dispatchers.IO` |
| `onDestroy`: collector shutdown → recorder stop → FpsMonitor → `serviceScope.cancel()` | **True** | 702–713 |
| GH-16 unit tests (buffer / idle / stats) | **False** | No tests |
| No-root fallback “records correctly” | **Partial / unsafe** | Fallback **does** produce `FPS_MONITOR` samples, but **B1**: no window delay → tight `dumpsys` loop |
| Two ftrace failures → rest of session uses `FpsMonitor` | **False** | `consecutiveFtraceFailures` is incremented and **never read** (681–689) |
| Release APK built and installed | **APK exists; install not re-verified** | File on disk; `adb devices` shows `Y5WWBMJVOZSK4HU8` |
| Unrelated changes: None | **False / incomplete file list** | Report omitted `PowerScripts.kt`, `PowerComponents.kt`, `PowerCalibrationComponent.kt`, `SettingsScreen.kt`, `docs/todo/in-progress.md`. Those are leftover polarity `abs()` / battery-first script work (already dirty before this pass), not GH-16. Also untracked: `docs/plans/`, `docs/gpu/`. |
| Ready for logical staging per plan | **True as intent** | Still **zero commits**. Suggested three-commit split below still applies. |

---

## Residuals and breakage (do these before calling GH-16 done)

### B1 — Bug: fallback sampler has no 2 s window

`OverlayService.startFpsRecording()` loops with **no** outer `delay`. Cadence is supposed to come from `GpuFpsCollector.sample(windowSec = 2f)`.

Ftrace paths delay inside `sampleSnapdragon` / `sampleMediaTek` (`delay((windowSec * 1000).toLong())`).

These paths return **immediately** via `sampleFallback()`:

- `!hasTracing` (no root / probe fail) — the documented no-root path
- `platform == UNKNOWN`
- `pid <= 0` (`pidof` miss)
- exception in the `try`

```92:102:app/src/main/java/com/ivarna/mkm/utils/GpuFpsCollector.kt
suspend fun sample(windowSec: Float = 2f): FpsSample? = withContext(Dispatchers.IO) {
    val appInfo = FpsMonitor.foregroundApp() ?: Pair("Unknown", 0)
    // ...
    if (!hasTracing || platform == Platform.UNKNOWN || pid <= 0) {
        return@withContext sampleFallback(pkg, pid)
    }
```

`sampleFallback` calls `FpsMonitor.readFps()` (`dumpsys window` + optional gfxinfo) and returns. The `while` in `startFpsRecording` immediately iterates.

**What breaks:** no-root record (plan 3.7 “No root”) hammers `dumpsys` as fast as the shell can go, fills the 300-sample cap in seconds, and can stall the HUD loop that the completion report claimed stayed smooth. Rooted MediaTek (this device, `duchamp` / `Y5WWBMJVOZSK4HU8`) hides the bug because the Mali path sleeps 2 s.

**Fix (pick one, do both if easy):**

1. In `startFpsRecording()`, after each sample: `delay((2000L).coerceAtLeast(500L))` **or** delay the remainder of the window.
2. In `sampleFallback` / at the top of the early-return branch: `delay((windowSec * 1000).toLong())` so fallback has the same cadence as ftrace.

Do **not** rely on ftrace `delay` for the no-root path.

### B2 — Plan 3.3 not implemented: two ftrace failures do not lock the session to `FpsMonitor`

```681:689:app/src/main/java/com/ivarna/mkm/service/OverlayService.kt
var consecutiveFtraceFailures = 0
while (isActive && FpsSessionRecorder.isRecording.value) {
    val sample = GpuFpsCollector.sample(windowSec = 2f)
    if (sample != null) {
        if (sample.source == GpuFpsCollector.Source.FPS_MONITOR && GpuFpsCollector.probeTracing()) {
            consecutiveFtraceFailures++
        } else {
            consecutiveFtraceFailures = 0
        }
```

The counter is never tested. `sample()` always retries ftrace when `probeTracing()` is cached true. Plan: two failures in a row → rest of session uses `FpsMonitor`; missing event files → no retry every sample.

After B1, wire this: if `consecutiveFtraceFailures >= 2`, call a fallback-only sample (or pass a flag) and stop enabling events.

### B3 — Deviation: graph X is sample index, not elapsed seconds

Plan 3.4: `X = elapsed s`, `Y = 0..max(peak, display refresh)`.

`FpsRecordGraph` uses `x = index * (width / (n-1))` and `maxY = max(maxFps * 1.15f, 60f)`. Fine while samples are 2 s apart. Combined with B1 the X axis is meaningless (300 points in a burst). After B1, index ≈ time; still not labelled in seconds. Follow-up, not a blocker once B1 is fixed.

### B4 — Deviation: session `platform` is always `"UNKNOWN"`

`OverlayScreen` calls `FpsSessionRecorder.start()` with the default. The service runs `detectPlatform()` and never writes it onto the session. Cosmetic.

### B5 — Hardcoded English on the graph

Localized: title, avg/min/max/samples, start/stop/clear.  
Still English: `"Current"`, source pills `"Adreno Inflight"` / `"Mali DMA Fence"` / `"FPS Monitor"`.  
`fps_source` and `fps_target_app` already exist in both `strings.xml` and are **unused**.

### B6 — Layering nit

`FpsSample.source` is typed as `GpuFpsCollector.Source`, so `data.model` imports `utils`. Move the enum into `FpsModels.kt` if you touch this file anyway.

### B7 — False test claims

There are **zero** unit tests. Do not treat “Verified preference persistence / sticky parse / buffer boundaries” as done. Either add small JVM tests for `FpsSessionRecorder` + `BatteryCharging`, or delete those sentences from any close comment.

### B8 — Start is gated on the overlay pref, not `OverlayService.isRunning`

```349:357:app/src/main/java/com/ivarna/mkm/ui/screens/OverlayScreen.kt
Button(
    onClick = { if (isFpsRecording) FpsSessionRecorder.stop() else FpsSessionRecorder.start() },
    enabled = isOverlayEnabled,   // pref && (LaunchedEffect-synced) — not isRunning
```

Plan: enable only if `OverlayService.isRunning`. If the pref is stale (service killed, auto-restart still in flight), `start()` sets `isRecording=true` with **no collector job**. Graph stays empty.

**Fix:** `enabled = OverlayService.isRunning` (keep the existing “start overlay first” string).

### B9 — In-flight sample can turn tracing back on after `onDestroy.shutdown()`

`sampleSnapdragon` / `sampleMediaTek` do `echo 1 > tracing_on` then `delay(2s)`. `onDestroy` calls `shutdown()` then `serviceScope.cancel()`. A sample already past `shutdown()` can re-enable tracing and then get cancelled, leaving `tracing_on=1`.

**Fix:** `try/finally { shutdown() }` inside the recording job; treat onDestroy shutdown as belt-and-suspenders.

### B10 — No `shutdown()` on collector init

Plan: clear leftover `tracing_on` on first use (force-stop can leave the kernel tracer on). As-built only probes with `echo 0 > tracing_on` once; events are not disabled until a later `shutdown()`. Call `shutdown()` at the top of `probeTracing()` / first `sample()`, and document force-stop leftover tracing.

### Non-issues (checked, do not “fix”)

| Risk | Result |
|---|---|
| Overlay still reading Power interval | No |
| `updateSettings` still skipping `loadSettings` | Fixed |
| `PowerStatus.calibratedPowerW` signed (double-negate) | Still unsigned; Overlay/Power/Settings prefix `isCharging` and `abs()` the magnitude |
| Session start/stop driven by `tick()` | Still `ACTION_POWER_*` + `isChargingSession` |
| `record_fps` persisted | No such key |
| Start gated on `isRunning` | **No** — B8, pref-based |
| Two recorder instances | `object` only |
| `trace_pipe` | Not used |
| `ShellManager.exec()` for ftrace writes | Not used (`execRoot` → `Shell.cmd`) |
| MKM samples dropped from the recorder | `foregroundApp()` keeps MKM. HUD binder still skips MKM — correct. |
| `shutdown()` after `serviceScope.cancel()` | Order is shutdown → stop → FpsMonitor → cancel |
| Sparkline / EfficiencyGraph reused | New `FpsRecordGraph` |
| Slider still 100 ms | Min 500 |
| `Icons.Default.Android` / `FiberManualRecord` / `DeleteOutline` | `material-icons-extended` is a dependency |

---

## Files actually changed (vs the report)

**In the completion list (this pass):**

| File | Role |
|---|---|
| `OverlayService.kt` | GH-17 interval + GH-16 sampler / shutdown |
| `OverlayScreen.kt` | Slider constants + record UI |
| `BatteryCharging.kt` **new** | Shared sticky helper |
| `BatteryProvider.kt` | `invalidate()`, peek-before-TTL, sign-align hold clear |
| `PowerProvider.kt` | Uses `BatteryCharging`; unsigned magnitudes |
| `BatterySessionTracker.kt` | invalidate on plug; `tick()` copies live flag |
| `BatteryScreen.kt` | `abs(currentMa)` in hero |
| `PowerModels.kt` | Contract comment only |
| `FpsModels.kt` **new** | `FpsSample` / `FpsSession` |
| `GpuFpsCollector.kt` **new** | Root ftrace + fallback |
| `FpsSessionRecorder.kt` **new** | In-memory 300-cap session |
| `FpsRecordGraph.kt` **new** | Canvas graph |
| `FpsMonitor.kt` | Public `foregroundApp()`; `foregroundPackage()` also public now |
| `values/strings.xml`, `values-zh-rCN/strings.xml` | `interval_format` + FPS record strings |
| `todo.md` | Items 5–7 checked (too early for GH-16 close) |

**Dirty but omitted from the report (pre-existing polarity / battery-first work, ship with GH-15 commit):**

| File | What it is |
|---|---|
| `PowerScripts.kt` | Battery-class only; keep kernel sign; no usb/dc/ac fallback |
| `PowerComponents.kt` | `abs(calibratedPowerW)` + `abs(currentUa)` under `isCharging` prefix |
| `PowerCalibrationComponent.kt` | Same `abs()` |
| `SettingsScreen.kt` | Same `abs()` |
| `docs/todo/in-progress.md` | T4–T6 stubs (stale wording) |

**Untracked docs (not app):** `docs/plans/` (this file), `docs/gpu/gpu-metrics.md`.

**Intentionally untouched (as planned):** `PowerCalibrationManager.kt`, `PowerViewModel.kt`, `PowerScreen.kt`, `ShellManager.exec()` fallback, `FpsBinder`.

---

# 1. GH-17 — Overlay Update Frequency — LANDED

## Bug (was)

The Overlay slider wrote a pref the service never read.

| | Overlay slider | Overlay monitor loop (before) |
|---|---|---|
| File | `overlay_prefs` | `power_calibration_prefs` |
| Key | `update_interval` | `update_interval_ms` |
| Default | 2000 ms | 1000 ms |
| Hidden floors | — | 500 ms if FPS on, else 1000 ms |

`PowerViewModel.setUpdateInterval()` never notified the overlay. `updateSettings()` returned before `loadSettings()` if `composeView` was not attached.

## As-built

### 1.1 Prefs + `updateSettings`

```kotlin
// OverlayService companion
const val PREFS_NAME = "overlay_prefs"
const val KEY_UPDATE_INTERVAL = "update_interval"
const val DEFAULT_UPDATE_INTERVAL_MS = 2000L
const val MIN_UPDATE_INTERVAL_MS = 500L
const val MAX_UPDATE_INTERVAL_MS = 5000L

// loadSettings()
updateIntervalState = prefs.getLong(KEY_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL_MS)
    .coerceIn(MIN_UPDATE_INTERVAL_MS, MAX_UPDATE_INTERVAL_MS)

// updateSettings()
loadSettings() // always
if (!::composeView.isInitialized || !composeView.isAttachedToWindow) return
// window flags only after this
```

`loadSettings()` still uses the string `"overlay_prefs"` rather than `PREFS_NAME` — same value, nit only.

`startMonitoring()` is **not** restarted. The loop reads `updateIntervalState` each iteration. Correct.

### 1.2 Not merged with Power

Power calibration / ViewModel / Power screen were not touched. No fallback to the Power interval (that would recreate the bug). First launch after the fix with no overlay key uses **2000 ms**, not the old effective 1000 ms. Intended.

### 1.3 Honest slider

- Slider `valueRange` = 500f..5000f.
- Single floor: `coerceAtLeast(500L)` whether FPS is on or off.
- Subtitle: `stringResource(R.string.interval_format, …)` — EN `Interval: %sms`, zh-rCN `间隔：%sms`.

### 1.4 Test (still the close checklist)

1. Overlay slider → 500 ms, FPS **off** → ~2 Hz. (This failed before because of the 1000 ms floor.)
2. Slider → 3000 ms → visibly slower.
3. Power Fast (500 ms) does **not** change a running overlay. After overlay restart it still follows the overlay slider.
4. Persist across stop/start. Change slider while overlay is off, then start — first loop uses the new value.

---

# 2. GH-15 — Polarity on charge and discharge — LANDED (device close pending)

## Contracts (kept)

| Model | Watts | Sign |
|---|---|---|
| `PowerStatus` (Power page, Overlay PWR, Settings) | `powerW` / `calibratedPowerW` always **≥ 0**; `currentUa` magnitude | UI: `if (isCharging) "+" else "-"` + `abs()` |
| `BatterySnapshot` / `BatteryStats` (Battery page, notification) | `wattageW` / `calibratedWattageW` **signed** | `if (isCharging) +mag else -mag` |

`isCharging` is Android sticky, **not** kernel `current_now`.

**Correction vs the v1 plan:** HEAD `PowerProvider` was **not** already sticky. It did:

```kotlin
// PowerProvider.getPowerStatus (HEAD, replaced)
// PowerScripts also stripped the minus: current=${current#-}
val isCharging = currentRaw > 0L   // any non-zero current ⇒ "charging"
```

BatteryManager fallback used `batteryManager.isCharging` (API charging bit, not `plugged != 0`). The as-built `BatteryCharging` helper is the real Overlay/Power polarity fix, not just a DRY extract. **Do not** revert to `currentRaw > 0L`.

**Do not** put sign back on kernel current. **Do not** sign `PowerStatus.calibratedPowerW` (`tick()` would double-negate: `if (isCharging) +w else -w`).

As-built Overlay PWR:

```476:484:app/src/main/java/com/ivarna/mkm/service/OverlayService.kt
val sign = if (homeData.power.isCharging) "+" else "-"
val powerStr = String.format("%s%.2f W", sign, kotlin.math.abs(homeData.power.calibratedPowerW))
```

Same pattern on Power page / Settings / calibration (the omitted files).

## Session vs tick (unchanged, still correct)

| Path | What it sets | Source |
|---|---|---|
| `onPowerConnected` / `onPowerDisconnected` | `isChargingSession`, persist, `startNewSessionLocked` | `ACTION_POWER_*` only |
| `tick()` | `BatteryStats.isCharging`, signed watts, ETA | live `powerStatus.isCharging` (as-built) |

Changing `tick()` polarity does **not** break session start/stop.

Overlay/Power use a **different** `PowerProvider` instance with **no** cache.

## Holes that remain (intentional)

### H1. `plugged != 0` forces `+`

`NOT_CHARGING` / `DISCHARGING` while plugged (pause-at-80, weak charger, USB) still `isCharging == true`. **Keep** until the device matrix says otherwise. Do **not** ship a tighter `when(status)` in a drive-by.

### H2. 5 s snapshot cache — mitigated

`CACHE_TTL_MS = 5_000` and `SHELL_CACHE_TTL_MS = 2_000` still exist for steady-state.

As-built mitigation:

1. `getSnapshot()` peeks sticky `isCharging` **before** the TTL return; mismatch → `invalidate()` then recompute.
2. Plug intents call `invalidate()` **before** `getSnapshot()`.
3. `tick()` overwrites `isCharging` + signed watts from uncached `PowerProvider`.
4. Plug handlers `launch { tick() }` immediately, so Battery/notification do not wait for the 30 s monitor interval.

`todo.md` previously said “Battery may lag ~5 s today” — that is **stale**. After this harden, Battery should flip on the plug-driven tick.

### H3. Zero-current hold

Hold still replays `lastCurrentMa` **after** sign-align; it does **not** re-flip. As-built also clears hold when cached sign disagrees with plug state (84–88) and `invalidate()` zeros the hold fields.

### H4. `STATUS_FULL` + plugged = “Charging”

Cosmetic. Still out of scope.

## As-built harden

1. `BatteryCharging.readAndroidCharging(Intent?)` — same formula as before; null → false.
2. `BatteryProvider.invalidate()` clears snapshot, shell cache, and zero-hold.
3. `tick()` uses live `powerStatus.isCharging` for watts **and** `copy(isCharging = …)`.
4. Tracker comment (H5) updated: both flags are Android sticky; the difference was the Battery cache.
5. Hero: `abs(stats.currentMa)` into `discharging_ma_format`. Notification already used `abs`.

`PowerScripts.getPowerAndVoltage()` is now battery-class only (no usb/dc/ac when battery current is 0). That was already in the dirty tree; include it in the GH-15 commit.

## Device test matrix (still required to close GH-15)

Overlay PWR, Power page, Settings calibration, Battery page, notification.

| # | Action | Expected | Notes |
|---|---|---|---|
| 1 | Unplugged, idle | `−`, Discharging, red | |
| 2 | Plug in | all five → `+`, Charging, green | GH-15 original |
| 3 | Unplug | Overlay/Power on next poll; Battery/notification on the plug-driven **tick** (not 5 s later, not 30 s later) | H2 mitigated |
| 4 | Repeat 2–3 three times | no stuck sign or label | |
| 5 | 100%, leave plugged | `+` small/0 W, “Charging” | H4 |
| 6 | OEM pause-at-80 | **record** status bar vs MKM | H1 — do not tighten this pass |
| 7 | Heavy load while plugged | stays `+` | H1 by design |
| 8 | Wireless if available | same as 2–3 | |
| 9 | Multiplier ≠ 1 | magnitude scales; sign from plug state | |

If 2/3 fail on Overlay/Power → sticky extras. If only Battery/notification fail → cache / copy. If only pause-at-80 is “wrong” → H1, later.

Implementer claimed this passed on `Y5WWBMJVOZSK4HU8` (release APK). Re-confirm with the reporter before closing the issue.

---

# 3. GH-16 — FPS recording — LANDED WITH RESIDUALS

## What already existed (unchanged)

- Overlay `show_fps`, reorderable metric, 15-point live sparkline
- `FpsBinder.computeFps`: `--latency` → `--latency-frameinfo` → `gfxinfo`
- `FpsMonitor.readFps`: elevated + non-MKM → binder; else `overlayFps / 2.1`
- Host scripts in `docs/fps/` are **adb** tools (MediaTek serial hardcoded `Y5WWBMJVOZSK4HU8`). Math was ported; scripts were not shipped on-device. **Never run a host script alongside an app record** — both own `/sys/kernel/tracing`.

## As-built collectors

`object GpuFpsCollector` — process singleton.

- `detectPlatform()` via `getprop ro.board.platform` (`ShellManager.exec` — dumpsys/getprop is fine on Shizuku).
- Patterns: Snapdragon `pineapple|kona|lahaina|taro|kalama|msm|sdm|sm[0-9]|cliffs|anorak|pitti`; MediaTek `mt[0-9]|mt6*|mt7*|mt8*` (implementation is slightly stricter `mt6[0-9]*` etc.). Cached for process lifetime.
- `probeTracing()`: `Shell.getShell().isRoot` then `echo 0 > /sys/kernel/tracing/tracing_on`. Cached. **Not** `hasElevatedAccess()`. **Not** `ShellManager.exec()`.
- Snapdragon: 0.5 s `dma_fence_signaled` KGSL ctx discover (pid prefix 3 chars) → `adreno_cmdbatch_submitted` inflight-drop awk. `frames < 2` or `events < 3` → idle, not a plotted 0.
- Mali: snapshot `dma_fence_signaled` `driver=mali` `timeline=0-<pid>_` counted on device. `events < 2` → idle.
- No `trace_pipe`. `shutdown()` sets `tracing_on=0` and disables both events; idempotent; root-only.
- Fallback: `FpsMonitor.readFps()` tagged `FPS_MONITOR`.

`FpsMonitor.foregroundApp(): Pair<String, Int>?` — window dump, then `topResumedActivity` / `mResumedActivity`, then `pidof` / `ps`. **Does not drop MKM.** (Overlay settings **is** MKM; skipping it emptied the graph.)

`foregroundPackage()` is now public as well (was private). Harmless extra API.

## As-built recorder

`object FpsSessionRecorder`

- `start()` / `stop()` / `add()` / `clear()`
- `StateFlow` `isRecording` + `session`
- Cap 300, drop oldest
- Synchronized
- In-memory only. `start()` clears the previous session.
- **Not** persisted. A crash cannot re-enable tracing.

## As-built drive path

- Overlay settings Start/Stop, `enabled = isOverlayEnabled` (`pref && OverlayService.isRunning`). Disabled copy when overlay is off.
- Screen talks to the `object` directly. Service `observeFpsRecording()` collects `isRecording` and starts/stops the IO job.
- Job is **not** on `startMonitoring`. HUD loop unchanged (`getHomeData` + optional `FpsMonitor`).
- `onDestroy`: `GpuFpsCollector.shutdown()` → `FpsSessionRecorder.stop()` → FpsMonitor cleanup → `serviceScope.cancel()`.

## As-built UI

New `FpsRecordGraph`: Canvas, skips idle for stats and the line (idle ticks at the baseline), pills for current/avg/min/max/count, source, package+pid. Graph shows while recording or after stop if `samples >= 2`.

## What is still missing vs plan 3.3 / 3.7

See **B1–B7** above. Highest priority is B1 (fallback delay) then B2 (stop retrying dead ftrace).

Optional / later (still out of this pass): CSV, video, jank, multi-app split, record with overlay off, live HUD from ftrace, `buffer_size_kb`, `work_period` / `ged_frame`.

## Test (updated)

**After B1 is fixed — no root**

1. FPS overlay still works (Shizuku dumpsys or draw fallback).
2. Record 15 s with overlay on → graph has points, source `FPS_MONITOR`, **~2 s spacing** (not 300 points instantly).
3. HUD CPU/RAM still update at the GH-17 interval while recording.
4. Stop → no tracing sysfs writes (`probeTracing` false).
5. Record toggle disabled when overlay is off.

**MediaTek root** (`Y5WWBMJVOZSK4HU8` / duchamp) — run host `gpu-fps-mediatek.sh` **before or after**, never alongside.

1. Probe succeeds; event enable is 1 while recording.
2. Foreground a GPU app 20 s. Graph in the same ballpark as the sequential script.
3. Home / launcher → idle or drop (no fake 0-frame points).
4. Stop → `tracing_on=0`, event enable 0.
5. Avg/min/max match non-idle samples.

**Snapdragon root** — same vs `gpu-fps-snapdragon.sh` sequentially. App switch rediscovers ctxs. `frames < 2` → idle.

**Regression**

- GH-17 interval still applies to the HUD.
- GH-15 signs unchanged.
- Rooted + Shizuku: ftrace still uses the root path, not `exec()`.

Close GH-16 when: B1 is fixed, overlay FPS toggle still works, no-root record is 2 s cadence, and a recorded session shows a graph with real numbers on at least one rooted GPU path (or documented `FPS_MONITOR`-only).

---

## Follow-up implementation (item 8 — do next)

Small, GH-16 only. Do not reopen GH-17 / GH-15 code.

| # | Change | File |
|---|---|---|
| 1 | Guarantee 2 s cadence on **every** sample path (fallback included) | `GpuFpsCollector.sample` and/or `OverlayService.startFpsRecording` |
| 2 | If `consecutiveFtraceFailures >= 2`, stop enabling events for the rest of the session | `OverlayService` + a fallback-only sample entry |
| 3 | Gate Start on `OverlayService.isRunning` | `OverlayScreen` |
| 4 | `try/finally { shutdown() }` in the recording job; `shutdown()` on first probe | `OverlayService` / `GpuFpsCollector` |
| 5 | (Optional) Pass `detectPlatform()` into `FpsSessionRecorder.start` | OverlayScreen / service |
| 6 | (Optional) Localize leftover English; X = elapsed s; use existing `fps_source` / `fps_target_app` | `FpsRecordGraph` + strings |
| 7 | Re-run no-root record 15 s and confirm HUD still ticks; Mali graph vs sequential host script | Device `Y5WWBMJVOZSK4HU8` (`mt6897` only — Snapdragon still untested) |

Do **not** change `ShellManager.exec()` Shizuku→root fallback globally.

---

## Out of scope (still)

- Closing GH-13 (Chinese is done in-tree; GitHub still open)
- mAh notification (done)
- CSV / live-HUD ftrace
- Battery “Full” label
- Power-page chips
- Tightening `isCharging` for pause-at-80 (H1)
- Changing `ShellManager.exec()` fallback
- 100 ms overlay refresh

---

## Suggested commits (still uncommitted)

1. `fix(overlay): honor overlay_prefs update_interval and 500ms floor (GH-17)`
2. `fix(power): bust battery cache and copy live charging flag (GH-15)`  
   Include `BatteryCharging.kt`, providers, tracker, BatteryScreen, PowerScripts, Power/Settings `abs()` UI.
3. `feat(fps): session record + graph; root ftrace collectors (GH-16)`  
   Include B1/B2 in this commit if you fix them before staging; otherwise a 4th `fix(fps): 2s fallback sample cadence`.

Rebuild after 1+2 was the original order (already done). Fix B1 before treating GH-16 as tested on no-root.

---

## Original plan-vs-code review (kept; this is why the v1 plan was rewritten)

Reviewed before implementation against `OverlayService`, `OverlayScreen`, `PowerCalibrationManager`, `PowerViewModel`, `PowerProvider`, `BatteryProvider`, `BatterySessionTracker`, `ShellManager`, `FpsMonitor`, `FpsBinder`, and the two host scripts.

| v1 claim | Reality | If implemented as written |
|---|---|---|
| Both providers already used Android sticky `isCharging` | **HEAD `PowerProvider` used `currentRaw > 0L`.** Old `PowerScripts` stripped `-`, so any non-zero current became “charging.” BatteryProvider was already sticky. | Plan under-scoped the Overlay/Power polarity fix. As-built PowerProvider now uses `BatteryCharging`. |
| Power-page chips live-change the overlay | `updateIntervalState` is only set in `loadSettings()`. Power chips never send `UPDATE_SETTINGS`. | Wrong mental model |
| Drop 500/1000 floors, trust 100 ms | Each HUD tick runs `getHomeData` + optional `FpsMonitor.readFps()`. 100 ms is unsafe. | Heat, jank, shell pile-up |
| `UPDATE_SETTINGS` always reloads interval | `updateSettings()` **used to** return before `loadSettings()` if view missing | Fixed in as-built |
| H3: zero-current hold “then flips sign” | Hold **replays** `lastCurrentMa` after sign-align. | Wattage sign is from `isCharging` |
| `tick()` only used `rawSnap.isCharging` for watts | `copy(...)` did not set `isCharging` | Fixed in as-built |
| Cache bust without peeking sticky first | TTL returned before any sticky read | Fixed in as-built |
| Reuse `Sparkline` / `EfficiencyGraph` | Sparkline is private; EfficiencyGraph is power-vs-score | New `FpsRecordGraph` |
| Shizuku can write ftrace | `exec()` tries Shizuku first; exit 1 does not fall back to root | Collector uses `Shell.cmd` / `isRoot` |
| Skip MKM samples | Overlay settings is MKM → empty graph | `foregroundApp()` keeps MKM |
| `class FpsSessionRecorder` in two places | Two instances, empty graph | `object` singleton |
| Parallel host-script vs app | Both own `/sys/kernel/tracing` | Still forbidden |
| Scripts run on device as-is | They are **adb host** scripts | Math ported to on-device `sh` |
