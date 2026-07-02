# MKM In-App FPS Monitoring — JNI Architecture

How MKM uses a C++ JNI library (`libfpsbinder.so`) for per-app FPS measurement,
and how it compares to Scene/vtools_en's native Binder approach.

---

## Architecture Overview

```
┌─────────────────────────────────┐
│  OverlayService.kt (500ms loop) │
│  FpsMonitor.readFps()           │
└──────────────┬──────────────────┘
               │ hasElevatedAccess?
     ┌─────────┴─────────┐
     │ YES (Shizuku/Root)│ NO
     ▼                   ▼
┌────────────────────┐  ┌──────────────────┐
│ FpsBinder.compute  │  │ Choreographer     │
│ Fps(pkg, callback) │  │ .FrameCallback    │
└────────┬───────────┘  │ (MKM's own UI)    │
         │              └──────────────────┘
         │ shellExec("dumpsys gfxinfo ...")
         │ shellExec("dumpsys SurfaceFlinger --list")
         │ shellExec("dumpsys SurfaceFlinger --latency ...")
         ▼
┌──────────────────────────────────────┐
│  libfpsbinder.so  (C++17, 786 lines) │
│                                      │
│  Text-in entry points:               │
│  • nativeFpsFromGfxinfoText()        │
│  • nativeFpsFromLatencyText()        │
│  • nativeFindLayerForPackage()       │
│                                      │
│  Self-contained entry points:        │
│  • nativeGetFpsForPackage()          │
│                                      │
│  Shared FPS math:                    │
│  • fpsFromSortedTimestampsNanos()    │
└──────────────────────────────────────┘
```

### Why shell-exec-in-Kotlin + parse-in-C++?

MKM uses **Shizuku** for elevated shell access. Shizuku elevates commands **through its own
service process** — it doesn't elevate the app process. A `popen("dumpsys ...")` called from
C++ runs as the app's own UID and will fail permission checks.

So the flow is:
1. Kotlin invokes the privileged command via Shizuku's shell
2. Raw text output is handed to JNI
3. C++ parses the text fast (no regex overhead in Kotlin, no GC pressure)

This is the **opposite** of Scene's approach — Scene runs native executables that talk Binder
directly and don't need Shizuku at all (they need their own root).

---

## Component Details

### 1. `FpsMonitor.kt` — Top-level entry point

**Path:** `app/src/main/java/com/ivarna/mkm/utils/FpsMonitor.kt`

A Kotlin `object` (singleton). Its `readFps()` method:

```kotlin
fun readFps(): FpsResult {
    if (ShellManager.hasElevatedAccess()) {
        val pkg = detectForegroundPackage()
        val fps = fpsBinder.computeFps(pkg) { cmd ->
            ShellManager.exec(cmd)   // Shizuku → root → local shell
        }
        if (fps > 0.0) return FpsResult(fps, 0)
    }
    // Fallback: MKM's own UI FPS via Choreographer
    return FpsResult(choreoSmoothedFps, 0)
}
```

Key design decision: The elevated-access check gates **all** per-app measurement. Without
Shizuku or root granted, only the Choreographer fallback runs.

### 2. `FpsBinder.kt` — Kotlin JNI bridge

**Path:** `app/src/main/java/com/mkm/fps/FpsBinder.kt`

Declares all `external` JNI functions and loads the native library:

```kotlin
System.loadLibrary("fpsbinder")  // → libfpsbinder.so
```

Its `computeFps()` has two strategies, each trying the native text-in entry point:

1. **gfxinfo framestats** (primary): `shellExec("dumpsys gfxinfo $pkg framestats")` → `nativeFpsFromGfxinfoText(text)`
2. **SurfaceFlinger --latency** (fallback): `shellExec("dumpsys SurfaceFlinger --list")` → `nativeFindLayerForPackage(listText, pkg)` → `shellExec("dumpsys SurfaceFlinger --latency '$layer'")` → `nativeFpsFromLatencyText(latencyText)`

**Package constraint:** The JNI class must be `com.mkm.fps.FpsBinder` — this matches the
native C++ export naming `Java_com_mkm_fps_FpsBinder_*`.

### 3. `fps_binder.cpp` — Native C++ engine (v2)

**Path:** `app/src/main/cpp/fps_binder.cpp`  
**Build:** `app/src/main/cpp/CMakeLists.txt` — C++17, links `liblog` only, NDK r29

#### 3a. gfxinfo parsing (`parseGfxInfoFrameStats`)

Parses the `---PROFILEDATA---` CSV format from `dumpsys gfxinfo <pkg> framestats`:

- Finds `FrameCompleted` column **by header name** (not hardcoded index — survives column
  reordering across Android versions)
- Skips rows with Flags bit 0 set (layout-change frames, not real presented frames)
- Matches against 5 column-name variants: `framecompleted`, `frame_completed`,
  `displaypresenttime`, `swapbufferscompleted`, `gpucompleted`
- Handles multi-block output (multiple `---PROFILEDATA---` sections from different processes)

#### 3b. SurfaceFlinger parsing

**`findLayerCandidatesForPackage`**: Parses `dumpsys SurfaceFlinger --list` output (the
current AOSP frontend format with `RequestedLayerState{...}` entries):

- Regex-matches `SurfaceView[...]#N` and `pkg/Activity#N` substrings
- Filters out decorations (`Background for`, `Bounds for`, `InputSink` entries)
- Ranks SurfaceView hits before activity hits

**`parseLatency`**: Parses `--latency <layer>` output:

- Discards the refresh-period header line
- Extracts `(desired, actual, ready)` tuples as `FrameTimestamps`
- **Degenerate-output detection**: If `< 3 real rows`, treats as "no data" rather than "0 FPS"
  (this is the SoloX#303 regression on Android 15/16)

#### 3c. FPS math (`fpsFromSortedTimestampsNanos`)

- Sliding 1-second window from the latest frame timestamp
- Staleness check: if latest frame > 1.5s old → 0.0
- For ≥15 frames over ≥200ms: `(count - 1) / window_duration` (avoids overcounting)
- Otherwise: raw count (safe for early/late low-frame-rate states)

#### 3d. Combined strategy (`getBestFpsForPackage`)

Self-contained entry point that does everything in C++:

1. `sfDump({"--list"})` → find layers for package
2. For each SurfaceView layer candidate: `sfDump({"--latency", layer})` → parse → compute FPS
3. If no latency data: `gfxInfoDump(pkg)` → parse → compute max FPS across all blocks
4. Track source via `FpsSource` enum: `SURFACEFLINGER_LATENCY` | `GFXINFO` | `NONE`

#### 3e. Binder access functions

For processes that run with elevated privileges directly (not via Shizuku):

- `getSurfaceFlingerBinder()`: Uses NDK `AServiceManager_getService("SurfaceFlinger")` to get
  a direct Binder handle, cached with an `isAlive` check
- `sfDumpViaBinder()`: Uses `AIBinder_dump()` to invoke dumpsys-equivalent on the Binder
  handle directly, reading via pipe

#### 3f. Dump fetch functions

- `sfDump(args)`: Tries Binder first → `popen("dumpsys SurfaceFlinger ...")` → `su -c`
- `gfxInfoDump(pkg)`: `popen("dumpsys gfxinfo ...")` → `su -c`

These work for root/shell processes but **not for Shizuku-only** (see privilege boundary note).

#### 3g. Removed from v1: Hook Mode

v1's `installIoctlHook()` / `nativeInstallHooks()` was a stub that always returned `true`.
v2 honestly returns `false` with a log warning explaining why hooking is impossible: apps
submit frames through per-connection `ISurfaceComposerClient` handles, not a single shared
SurfaceFlinger service handle.

#### 3h. Legacy compat exports

`nativeGetActiveLayers`, `nativeGetAllFps`, `nativeGetFrameTimestamps`, etc. are kept so
existing Kotlin call sites don't hit `UnsatisfiedLinkError`. Deprecated but still functional.

### 4. `ShellManager.kt` — Privilege-aware shell

**Path:** `app/src/main/java/com/ivarna/mkm/shell/ShellManager.kt`

Prioritization: **Shizuku → Root (libsu) → Local shell**

This is the callback passed to `FpsBinder.computeFps()`. Every `dumpsys` command goes through
this chain.

---

## Comparison: MKM JNI vs Scene Native Binder

| Aspect | MKM (libfpsbinder.so v2) | Scene/vtools_en (binder*.so) |
|--------|--------------------------|------------------------------|
| **Library type** | Shared library loaded via `System.loadLibrary` | Standalone native executable `.so` |
| **Privilege model** | Shizuku elevates shell commands; C++ only parses text | Binary needs its own root/UID 2000 |
| **SurfaceFlinger access** | Via `dumpsys` text (shell) or `AIBinder_dump()` (if root) | Direct Binder IPC (`BBinder::transact`) |
| **FPS sources** | gfxinfo framestats (primary), SF --latency (fallback) | gfxinfo framestats, FPSGO, SF service call |
| **Text parsing** | C++ (regex + string streams) | Not needed — gets structured Parcels |
| **API level targeting** | One `.so`, works across API levels | 4 `.so` files (binder12–15, one per API level) |
| **Shell overhead** | One fork per FPS read via Shizuku | None for Binder path; normal shell for gfxinfo |
| **Can do FPS limit** | No (declared as not implemented) | Yes (`Dumpsys::fpsLimit`) |
| **Can do display mode** | No | Yes (`Dumpsys::displayMode`) |
| **Persistence** | JNI loaded in app process | Forked per invocation |
| **NDK dependency** | `liblog` only | Full Binder/AIDL stack |
| **Build system** | CMake, NDK r29, C++17 | Unknown (prebuilt `.so` in APK) |

### Why MKM didn't go the Scene route

1. **Shizuku privilege model.** Scene's native executables need direct root/UID 2000 access.
   Shizuku doesn't elevate the app process — it elevates individual shell commands spawned
   through its service. MKM's text-in approach works with Shizuku; Scene's approach requires
   Magisk/KernelSU root.

2. **Maintenance burden.** Scene ships 4 API-level-specific binder binaries (12–15). Each
   must be updated when AOSP Binder internals change. MKM's one C++ file that parses text
   is immune to Binder ABI changes.

3. **Scope.** MKM only needs FPS reading — not display mode switching or FPS capping. A
   text-parsing JNI library is the right level of complexity.

---

## Files

| File | Role |
|------|------|
| `app/src/main/cpp/fps_binder.cpp` | C++ JNI library (786 lines) — all parsing, FPS math, Binder integration |
| `app/src/main/cpp/CMakeLists.txt` | CMake config: C++17, single source, links `liblog` |
| `app/src/main/java/com/mkm/fps/FpsBinder.kt` | Kotlin JNI bridge — external declarations + `computeFps()` |
| `app/src/main/java/com/ivarna/mkm/utils/FpsMonitor.kt` | Top-level entry — privilege check, foreground detection, Choreographer fallback |
| `app/src/main/java/com/ivarna/mkm/shell/ShellManager.kt` | Privileged shell (Shizuku → Root → Local) |
| `app/src/main/java/com/ivarna/mkm/service/OverlayService.kt` | Consumer — reads FPS every 500ms for overlay display |
| `app/build.gradle.kts` | NDK config: `ndkVersion = "29.0.14206865"` |

---

## Fallback Chain

```
Shizuku/Root granted?
  └── YES → dumpsys gfxinfo framestats (C++ parsed via JNI)
        └── NO DATA? → dumpsys SurfaceFlinger --latency + --list (C++ parsed via JNI)
              └── NO DATA? → return -1.0
  └── NO → Choreographer (MKM's own UI FPS, pure Kotlin, EMA-smoothed)
```

---

## Known Limitations

1. **SurfaceFlinger --latency regression (Android 15/16).** Detected by the `kMinRealRows`
   check in `parseLatency()` — gracefully falls back to gfxinfo rather than showing 0 FPS.

2. **gfxinfo doesn't measure GPU scene frames.** OpenGL/Vulkan SurfaceView apps (like PUBG
   or Genshin Impact) have real GPU frames that gfxinfo can't see. The `--latency` fallback
   handles these, but doesn't work on Android 16.

3. **No FPS limiting.** `nativeSetFpsLimit` is stubbed — `ISurfaceComposer::setFrameRate`
   only exists behind the AIDL interface, with no stable NDK wrapper.

4. **Shizuku process boundary.** Self-contained C++ entry points (`nativeGetFpsForPackage`,
   `sfDump`) can't use Shizuku elevation. The text-in entry points are the intended path
   for Shizuku setups.

5. **FrameCounter legacy exports.** `nativeGetActiveLayers` and friends are kept for compat
   but provide degraded output (frame counts always `0` since the new `--list` format
   doesn't print per-layer counters).

---

## References

| Source | What it provides |
|--------|-----------------|
| [vtools_en 4.7.1](https://github.com/ramabondanp/vtools_en) | gfxinfo parsing, FPSGO parsing, SF service call fallback |
| Scene 9.3.5 APK | Native Binder executables (`binder12-15.so`) for direct SF IPC |
| [SoloX #303](https://github.com/smart-test-ti/SoloX/issues/303) | Documented `--latency` regression on Android 15/16 |
| [alibaba/mobileperf](https://github.com/alibaba/mobileperf) | gfxinfo-first strategy, `--latency` unreliability notes |
