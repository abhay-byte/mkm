# FPS Monitoring on Android

How to read accurate FPS from apps and games on Android devices — covering ADB shell methods, tools, and on-device apps.

---

## Methods Overview

| Method | Accuracy | Root Needed | PC Needed | Best For |
|--------|----------|-------------|-----------|----------|
| `SurfaceFlinger --latency` | High | No | Yes (ADB) | Games, SurfaceView apps |
| `dumpsys gfxinfo` | High | No | Yes (ADB) | Standard View UI apps |
| GetFPS scripts | High | No | Yes (ADB) | Automated/scripted monitoring |
| GameBench FPS Monitor | High | No | No | Quick sessions, charts |
| Scene (omarea) | High | Yes | No | Full device perf management |

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

Returns detailed per-frame timing data. Calculate FPS from the number of frames divided by the time window.

**Limitation:** Does not work for games/SurfaceView apps — they don't report to gfxinfo.

---

## 3. On-Device Apps (No PC Needed)

### GameBench FPS Monitor

- **Free**, no root, no account required
- Floating overlay with real-time FPS, janks, battery temperature
- Session recording with charts and export
- **Limits:** 5 min per session, 20 min per day
- Android 11+
- [Play Store](https://play.google.com/store/apps/details?id=com.gamebench.fpsmonitor)

### Scene (omarea)

- **Open source**, requires root
- Full device performance management
- Includes FPS monitoring overlay
- Also covers: governors, ZRAM/SWAP, Magisk/Xposed, backup
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

## 4. Building In-App FPS Monitoring

To add FPS monitoring directly into an app:

### Choreographer (Compose / View)

```kotlin
Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
    // calculate FPS from frame delta
}
```

### SurfaceFlinger (system-level)

Use `SurfaceControl` APIs (requires system permissions or root) to query `SurfaceFlinger` timestamps for any window.

### FrameMetrics (Android 7+)

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

# GetFPS one-liner
adb shell <<'EOF'
source /data/local/tmp/get_fps.sh
EOF
```
