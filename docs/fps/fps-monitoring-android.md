# FPS Monitoring on Android

How to read accurate FPS from apps and games on Android devices — covering ADB shell methods,
native Binder IPC, tools, and on-device apps.

---

## Methods Overview

| Method | Accuracy | Root Needed | PC Needed | Native Code | Best For |
|--------|----------|-------------|-----------|-------------|----------|
| `SurfaceFlinger --latency` | High | No | Yes (ADB) | No | Games, SurfaceView apps |
| `dumpsys gfxinfo framestats` | High | No | Yes (ADB) | No | Standard View UI apps |
| Native Binder IPC (Scene) | High | Yes | No | Yes (C++ .so exec) | Direct system-level monitoring |
| Shell + JNI parsing (MKM) | High | Yes (Shizuku) | No | Yes (C++ JNI) | In-app monitoring with Shizuku |
| GetFPS scripts | High | No | Yes (ADB) | No | Automated/scripted monitoring |
| GameBench FPS Monitor | High | No | No | No | Quick sessions, charts |
| Scene (omarea/vtools_en) | High | Yes | No | Yes (binder .so) | Full device perf management |

---

## 1. ADB — SurfaceFlinger (Games / SurfaceView)

The most reliable way to get FPS from games and GPU-rendered apps that use `SurfaceView`.

### List available surfaces

```bash
adb shell dumpsys SurfaceFlinger --list
```

This returns all active surface names. Games typically show as `SurfaceView[<package>/<activity>]#0`.

### Get frame timestamps

```bash
adb shell dumpsys SurfaceFlinger --latency <surface_name>
```

**Output format:**

```
16954612                          ← refresh period (ns, ~16.95ms for 60Hz)
7657467895508 7657482691352 7657493499756   ← 128 lines, 3 timestamps each:
7657484466553 7657499645964 7657511077881     A) app started drawing
7657500793457 7657516600576 7657527404785     B) vsync before SF submitted frame
...                                          C) after SF submitted frame to h/w
```

### Calculate FPS

From the timestamps:
- **Frame time** = C − A (nanoseconds)
- **FPS** = 1,000,000,000 / average(frame time)
- **Jank** detected when `ceil((C − A) / refresh_period)` changes between frames

### Known regression (Android 15/16)

On some Android 15+ devices, `SurfaceFlinger --latency` returns only the refresh period line
with no frame data (`SoloX#303` regression). MKM detects this and falls back to gfxinfo.

### Automated script

Push the GetFPS scripts to the device:

```bash
adb push get_fps.sh /data/local/tmp
adb push utils.sh /data/local/tmp
adb shell
source /data/local/tmp/get_fps.sh
```

Prints FPS of the current top resumed app. Options:
- `-p <package>` — target a specific package
- `-t` — show average render time instead
- Loop with `watch -tn1 "eval source get_fps.sh"`

### Comprehensive shell script

The [fps.sh](https://gist.github.com/yudenzel/49fe4e4169e15b68fd45475b3cb11bd3) gist adds jank detection, smoothness scoring, and CSV output:

```bash
sh fps.sh -t 60 -w "SurfaceView[com.example.game/...]#0"
```

Metrics: FU(s), LU(s), FPS, Frames, jank, MFS(ms), OKT, SS(%).

---

## 2. ADB — gfxinfo (Standard UI)

For apps using standard Android View hierarchy (not SurfaceView/OpenGL):

```bash
adb shell dumpsys gfxinfo <package> framestats
```

Returns detailed per-frame timing data in CSV format. The `FrameCompleted` column (index 17)
contains nanosecond timestamps for each frame. Calculate FPS by counting timestamps in the
last 1-second window.

**Limitation:** Does not work for games/SurfaceView apps — they don't report to gfxinfo.

---

## 3. Native Binder IPC (Scene / vtools_en Approach)

Scene bundles **native ELF executables** as `.so` files in `assets/toolkit/` — one per Android
API level (`binder12.so` through `binder15.so`). These are standalone binaries that:

1. Connect to the Android Binder service manager via `defaultServiceManager()`
2. Register a custom `SceneService` (a `BBinder` subclass) with the system
3. Implement a native `android::Dumpsys` class that talks to SurfaceFlinger directly
4. Call `ISurfaceComposer` methods to read frame data as structured Parcels

### Key symbols decoded

```
binder_connect()              → Main entry — connects to servicemanager
addService()                  → Registers SceneService with the system
Dumpsys::fpsLimit(id, fps)    → Set display refresh rate cap
Dumpsys::displayMode(mode)    → Switch display modes
Dumpsys::startDumpThread()    → Background SurfaceFlinger monitoring thread
Dumpsys::writeDump()          → Read frame data from SF via Binder
SceneService::onTransact()    → Custom Binder transaction handler
```

### Architecture comparison

| Aspect | Shell dumpsys | Native Binder |
|--------|--------------|---------------|
| Process | Forks `/system/bin/dumpsys` | `exec()`s native binary |
| Communication | Text through stdio pipe | Binder IPC (Parcel) |
| Elevation | Inherits shell UID | Needs own root/UID 2000 |
| Overhead | Process fork + text serialization | Direct method call |
| Parsing | String/regex on text output | Structured Parcel data |
| Portability | Works on all devices | API-level-specific .so files |

### Why MKM chose shell-over-JNI instead

MKM uses **Shizuku** for elevated access. Shizuku elevates commands through its own service
process — not the app process. A native Binder executable can't use Shizuku's elevation path.
MKM's approach passes raw `dumpsys` text from Shizuku-invoked shell commands to a C++ JNI
library (`libfpsbinder.so`) for parsing, which keeps the privilege boundary clean.

---

## 4. On-Device Apps (No PC Needed)

### GameBench FPS Monitor

- **Free**, no root, no account required
- Floating overlay with real-time FPS, janks, battery temperature
- Session recording with charts and export
- **Limits:** 5 min per session, 20 min per day
- Android 11+
- [Play Store](https://play.google.com/store/apps/details?id=com.gamebench.fpsmonitor)

### Scene (omarea/vtools_en)

- **Open source** ([ramabondanp/vtools_en](https://github.com/ramabondanp/vtools_en), tag 4.7.1)
- Requires root
- Full device performance management
- Includes FPS monitoring overlay with session recording
- Native Binder binaries for direct SurfaceFlinger communication
- Also covers: governors, ZRAM/SWAP, Magisk/Xposed, backup
- FPS stack: `gfxinfo framestats` → FPSGO (`/sys/kernel/fpsgo/`) → `service call SF 1013`
- [APKPure](https://apkpure.net/scene/com.omarea.vtools/download)

### Real-time FPS Monitor

- Two modes: **UI benchmarking** (system smoothness) and **Content FPS** (screen analysis for actual visual frame rate)
- No root needed
- [Play Store](https://play.google.com/store/apps/details?id=com.tribalfs.realtimefps)

### FPS Meter / GPU FPS Tracker

- Lightweight floating overlay
- Game library management
- Various options on Play Store and F-Droid

---

## 5. Building In-App FPS Monitoring

### Option A: Choreographer (Compose / View) — no root needed

```kotlin
Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
    // calculate FPS from frame delta
}
```

Only measures **your own app's** UI frames. Not per-app.

### Option B: Shell + JNI parsing (MKM approach) — requires Shizuku/root

```
Shizuku → shellExec("dumpsys gfxinfo <pkg> framestats")
    → raw text → JNI (libfpsbinder.so) → parsed FPS
```

Privilege boundary: Kotlin handles shell elevation, C++ handles fast text parsing.

### Option C: Native Binder (Scene approach) — requires root

```
exec("binder14.so") → direct Binder IPC → structured frame data
```

Requires the native binary to run with elevated privileges itself.

### Option D: SurfaceFlinger (system-level)

Use `SurfaceControl` APIs (requires system permissions or root) to query `SurfaceFlinger`
timestamps for any window.

### Option E: FrameMetrics (Android 7+)

```kotlin
window.addOnFrameMetricsAvailableListener({ _, frameMetrics, _ ->
    val frameDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
    // track frame timing
    }, Handler(Looper.getMainLooper()))
```

---

## Quick Reference

```bash
# Quick FPS check for game (list surfaces first)
adb shell dumpsys SurfaceFlinger --list
adb shell dumpsys SurfaceFlinger --latency SurfaceView[com.example.game/...]#0 | awk 'NR>1{print $3-$1}' | awk '{sum+=$1; n++} END{print "FPS:", n/(sum/1000000000)}'

# gfxinfo for regular apps
adb shell dumpsys gfxinfo com.example.app framestats

# Service call for global frame counter (may not work on Android 15+)
adb shell service call SurfaceFlinger 1013

# GetFPS one-liner
adb shell <<'EOF'
source /data/local/tmp/get_fps.sh
EOF
```
