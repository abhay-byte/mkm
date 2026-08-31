---
- id: T2
  title: Simplified Chinese Support
  type: feature
  priority: high
  difficulty: medium
  why: Make the app accessible to Simplified Chinese speaking users
  really_needed: unknown
  impact: UI strings, localization resources, possibly layouts
  followups: null
  images: null
  github_ref: GH-13
  plan: |
    Goal: Add Simplified Chinese (zh-rCN) localization.
    Files: MODIFY values/strings.xml (extract ~457 English strings); CREATE values-zh-rCN/strings.xml (Chinese translations); MODIFY 20 UI .kt files (replace hardcoded strings with stringResource(R.string.*))
    Approach: 1) Extract all visible UI strings from 20 Compose files 2) Write values/strings.xml with resource IDs 3) Write values-zh-rCN/strings.xml with translations 4) Refactor .kt files to use stringResource() 5) Verify build succeeds
    Edge cases: Formatted strings (%s/%d), contentDescription, dynamic concatenations, plurals
    Test plan: Build app, verify no compile errors, verify zh locale loads
- id: T3
  title: Add --latency-frameinfo fallback to FPS monitoring pipeline
  type: feature
  priority: high
  difficulty: medium
  why: On Android 12+ devices where --latency returns no data (SoloX#303 regression), --latency-frameinfo may still work. It also provides richer GPU composition timing data. Also need to investigate why current root FPS readings are inaccurate.
  impact: fps_binder.cpp (C++ JNI), FpsBinder.kt (Kotlin bridge), FpsMonitor.kt (top-level entry)
  followups: null
  images: null
  github_ref: null
  plan: |
    Goal: Insert --latency-frameinfo as new fallback between --latency and gfxinfo. Investigate why root FPS readings are currently inaccurate.
    Files: MODIFY app/src/main/cpp/fps_binder.cpp; MODIFY app/src/main/java/com/mkm/fps/FpsBinder.kt
    Approach:
      C++: new parseLatencyFrameinfo() for 7-field format (frameNum,vsyncId,inputTime,animStart,gpuStart,gpuEnd,presentTime), new JNI entry nativeFpsFromLatencyFrameinfoText, insert into getBestFpsForPackage chain after --latency fails
      Kotlin: new external declaration, add shellExec("dumpsys SurfaceFlinger --latency-frameinfo '$layer'") in computeFps() after --latency step fails
    Edge cases: device doesn't support frameinfo (empty output), zero presentTime rows, degenerate output detection like --latency parser
    Investigation: root FPS inaccuracy — check FPS math (fpsFromSortedTimestampsNanos), staleness window (1.5s), gfxinfo vs SurfaceFlinger source alignment, cross-reference against on-device tools
- id: T4
  title: Overlay Update Frequency does not apply
  type: bug
  priority: high
  difficulty: easy
  status: landed
  why: Overlay slider wrote overlay_prefs/update_interval; service read PowerCalibrationManager. Fixed — service reads overlay_prefs, single 500 ms floor, loadSettings always.
  impact: OverlayService, OverlayScreen
  followups: keep Power-page interval separate
  images: null
  github_ref: GH-17
  plan: docs/plans/gh-15-16-17-overlay-fps-polarity.md#1-gh-17--overlay-update-frequency--landed
- id: T5
  title: FPS session recording and basic graph
  type: feature
  priority: high
  difficulty: medium
  status: landed-with-residuals
  why: Recorder + graph + root ftrace landed. B1 no-root fallback has no 2 s delay (tight dumpsys loop). B2 consecutiveFtraceFailures unused. Do not close GH-16 until B1 is fixed.
  impact: GpuFpsCollector, FpsSessionRecorder, FpsRecordGraph; OverlayService; OverlayScreen
  followups: B1 sampler cadence; B2 two-failure lock-in; no CSV this pass
  images: null
  github_ref: GH-16
  plan: docs/plans/gh-15-16-17-overlay-fps-polarity.md#3-gh-16--fps-recording--landed-with-residuals
- id: T6
  title: Power polarity on charge and discharge
  type: bug
  priority: high
  difficulty: easy
  status: landed-pending-device-close
  why: Charging used kernel current sign. Now Android plug state + cache bust + live isCharging copy. 5 s TTL remains for steady-state; plug/unplug invalidate + tick. H1 plugged!=0 still forces +. Close after device matrix.
  impact: PowerProvider, BatteryProvider, BatterySessionTracker, BatteryCharging
  followups: tighten isCharging if OEM pause-at-80 fails the test
  images: null
  github_ref: GH-15
  plan: docs/plans/gh-15-16-17-overlay-fps-polarity.md#2-gh-15--polarity-on-charge-and-discharge--landed-device-close-pending
---
