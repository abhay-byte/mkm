# How Per-App FPS Measurement Works (Root/Shell)

## The Problem

`dumpsys gfxinfo <pkg>` (without `framestats`) gives aggregate frame counts — useful for
averages over long periods, but wrong for real-time FPS. It counts **Android View-system
frames** (buttons, layouts, Compose recompositions), NOT GPU-rendered scene frames from
OpenGL/Vulkan apps using SurfaceView.

**Example from benchmark on Xiaomi 2311DRK48I (Android 16):**
- On-screen GPU benchmark FPS: 40→3, avg 19.5
- `dumpsys gfxinfo` delta FPS (plain): 59→109
- These are measuring **different things** — gfxinfo sees UI shell frames, not GPU scene.

---

## Method 1: `dumpsys gfxinfo <pkg> framestats` (vtools/Scene approach)

**Source:** [ramabondanp/vtools_en](https://github.com/ramabondanp/vtools_en) (analyzed at tag 4.7.1),
Scene APK 9.3.5 (decompiled)

### How it works

1. Get foreground package: `dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'`
2. Parse package name with regex: `([a-zA-Z0-9._-]+)/`
3. Run: `dumpsys gfxinfo $packageName framestats`
4. Parse CSV header for `FrameCompleted` column index
5. For each row, extract the FrameCompleted nanosecond timestamp
6. Count frames in the last 1-second window → FPS = count
7. When foreground app changes, run `dumpsys gfxinfo $packageName reset` to clear stats

### Implementation in vtools_en

The vtools_en source has a layered FPS stack:

```
FloatFpsWatch.kt (overlay UI → updates every 1s)
  └── SurfaceFlingerFpsUtils2.kt (orchestrator)
        ├── GfxInfoFpsUtils.kt (primary: gfxinfo framestats)
        ├── FPSGO kernel module: /sys/kernel/fpsgo/fstb/fpsgo_status
        └── service call SurfaceFlinger 1013 (legacy fallback)
```

**Key detail from `GfxInfoFpsUtils.kt`:** Uses `KeepShell` for persistent root shell sessions
to avoid shell-spawn overhead on every FPS read. Has a 2-second staleness timeout that resets
stats when no new frames arrive.

**Key detail from `SurfaceFlingerFpsUtils2.kt`:** Takes the maximum FPS across all threads
matching a package in `fpsgo_status`. Falls through gfxinfo → fpsgo → service call chain.

### CSV Format

```
Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,
PerformTraversalsStart,DrawStart,FrameDeadline,FrameStartTime,FrameInterval,WorkloadTarget,
SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,
QueueBufferDuration,GpuCompleted,SwapBuffersCompleted,DisplayPresentTime,CommandSubmissionCompleted,
```

`FrameCompleted` is at index **17** (0-based). Column order is stable across Android versions
but always parse the header to be safe.

### Implementation (Kotlin)

```kotlin
class GfxInfoFpsUtils(private val shellExec: (String) -> String) {
    private var lastActivePackage: String? = null
    private val frameTimeBuffer = ArrayList<Long>()
    private var lastProcessedFrameTime = 0L
    private var columnIndex = -1

    fun getFps(): Float? {
        val pkg = getTopPackage() ?: return null

        if (pkg != lastActivePackage) {
            lastActivePackage = pkg
            frameTimeBuffer.clear()
            lastProcessedFrameTime = 0L
            columnIndex = -1
            shellExec("dumpsys gfxinfo $pkg reset")
            return null
        }

        val output = shellExec("dumpsys gfxinfo $pkg framestats")
        if (output.isBlank()) return null

        val lines = output.split("\n")

        if (columnIndex == -1) {
            val header = lines.firstOrNull { it.contains("FrameCompleted") } ?: return null
            columnIndex = header.split(",").indexOf("FrameCompleted")
            if (columnIndex == -1) return null
        }

        var maxTimestamp = lastProcessedFrameTime

        for (line in lines) {
            val parts = line.split(",")
            if (parts.size <= columnIndex) continue
            val ts = parts[columnIndex].trim().toLongOrNull() ?: continue
            if (ts <= lastProcessedFrameTime) continue
            frameTimeBuffer.add(ts)
            if (ts > maxTimestamp) maxTimestamp = ts
        }
        lastProcessedFrameTime = maxTimestamp

        val nowNs = System.nanoTime()
        frameTimeBuffer.removeAll { nowNs - it > 1_000_000_000L }

        return if (frameTimeBuffer.isEmpty()) null else frameTimeBuffer.size.toFloat()
    }

    private fun getTopPackage(): String? {
        val line = shellExec("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        if (line.isBlank()) return null
        return Regex("([a-zA-Z0-9._-]+)/").find(line)?.groupValues?.get(1)
    }
}
```

### Limitations

- **Only tracks View-system frames** (HWUI pipeline). Does NOT track GPU scene frames from
  SurfaceView/TextureView apps using OpenGL/Vulkan.
- Good for: normal apps (Chrome, Settings, Launcher, Compose/View games)
- Bad for: OpenGL/Vulkan games (PUBG, Genshin Impact, benchmark tools)

---

## Method 2: `dumpsys SurfaceFlinger --latency <layer>` (Scene approach)

**Source:** Scene APK 8.3.7 (decompiled — uses native daemon for shell execution)

### How it works

1. Get foreground package (same as Method 1)
2. Get SurfaceFlinger layer name: `dumpsys SurfaceFlinger --list`
3. Parse out the layer matching the foreground package:
   e.g. `com.pubg.imobile/com.epicgames.UE4Game.GameActivity#12345`
4. Run: `dumpsys SurfaceFlinger --latency '<layer_name>'`
5. Parse frame presentation timestamps (3 columns: vsync, frame complete, present)
6. Count frames in time window → FPS

### Implementation Notes

Scene maintains game-specific SurfaceView name mappings:
```
SurfaceView - com.tencent.tmgp.sgame/com.tencent.tmgp.sgame.SGameActivity#0
SurfaceView - com.miHoYo.Yuanshen/com.miHoYo.GetMobileInfo.MainActivity#0
```

For SurfaceView apps, the --latency layer is different from the ActivityRecord layer.
Look for `SurfaceView - <package>/<activity>#N` format.

### Tested On Device

`dumpsys SurfaceFlinger --latency 'com.ivarna.finalbenchmark2/...'` returned **only the refresh
period** (`8333333` = 8.33ms = 120Hz) — no frame history data on Android 16. This method may
require specific kernel/driver support or an older Android version.

---

## Method 3: Scene's Native Binder Approach (Scene 9.3.5)

### What it is

Scene bundles **native ELF executables** (`binder12.so` through `binder15.so`, one per Android
API level) in `assets/toolkit/`. These are NOT shared libraries — they are standalone native
executables that:

1. Connect to the Android Binder service manager directly (`defaultServiceManager()`)
2. Register a custom `SceneService` (extending `BBinder`) with the system
3. Implement a native `android::Dumpsys` class that talks to SurfaceFlinger via Binder IPC
4. Expose `fpsLimit(int displayId, float fps)` to set display refresh rate
5. Expose `displayMode(int mode)` to switch display modes
6. Expose `startDumpThread` / `stopDumpThread` for background SurfaceFlinger monitoring

### Binder .so symbols (from strings analysis)

```
_ZN7android7Dumpsys4mainEiPKPc...   → Dumpsys::main(int, char**, string&)
_ZN7android7Dumpsys8fpsLimitEif     → Dumpsys::fpsLimit(int, float)
_ZN7android7Dumpsys11displayModeEi  → Dumpsys::displayMode(int)
_ZN7android7Dumpsys15startDumpThreadEiRKNS_8String16ERKNS_6VectorIS1_EE
_ZNK7android7Dumpsys9writeDump...
_ZN12SceneService10onTransact...    → SceneService (custom BBinder subclass)
_Z14binder_connectv                 → binder_connect() — main entry
_Z10addServicev                     → addService() — registers with service manager
main
```

### How the binder binaries differ from shell-based measurement

Instead of spawning `dumpsys` as a shell command (which forks a process and marshals text
through stdio), these native binaries:

- **Talk directly to SurfaceFlinger** via Binder IPC (method call on `ISurfaceComposer`)
- **Avoid shell overhead** — no `/system/bin/sh` fork, no text parsing of dumpsys output
- **Get structured Parcel data** from the Binder transaction, not text
- **Run as a separate process** (`exec()`'d by the app), so they need their own UID elevation

This is architecturally similar to what `service call SurfaceFlinger 1013` does (which
serializes the same Binder transaction to hex text), but without the double-transcoding
(Binder→Parcel text→parsing→usable data vs. Binder→structured data directly).

### Why MKM chose shell-over-JNI instead of native binder executables

MKM uses Shizuku for elevated shell access. Shizuku elevates commands **through its own
service process**, not the app process. A native binder executable can't use Shizuku — it
would need its own root/shell elevation path. MKM's approach passes `dumpsys` text from
Shizuku-invoked shell commands to a JNI library for parsing, keeping the privilege boundary
clean.

---

## Method 4: Kernel Sysfs `measured_fps` (device-dependent)

### Paths checked

```
/sys/class/drm/sde-crtc-0/measured_fps          # Qualcomm
/sys/class/graphics/fb0/measured_fps             # Legacy
/sys/devices/platform/soc/.../measured_fps       # Qualcomm full path
```

Not available on MediaTek mt6897 (Dimensity 8300).

### Discovery

```
find /sys -name measured_fps 2>/dev/null | grep crtc
find /sys -name fps 2>/dev/null | grep crtc
```

---

## Method 5: `service call SurfaceFlinger 1013` (vtools fallback)

Returns a global frame counter (not per-app). Parses hex from Parcel output:
```
Result: Parcel(00001023...)
```

Not available on Android 16 (no output). Service codes change between Android versions.

---

## Method 6: FPSGO Kernel Module (MediaTek devices)

Path: `/sys/kernel/fpsgo/fstb/fpsgo_status`

Returns tabular per-thread FPS:
```
tid  name         currentFPS  ...
1234 surfaceflinger  60
5678 com.game       45
```

vtools_en's `SurfaceFlingerFpsUtils2` parses this by matching `name` column against the
foreground package name and taking the maximum FPS across all matching threads.

Not available on this device.

---

## Device Test Results (Xiaomi 2311DRK48I, Android 16, MediaTek mt6897)

| Test | Input | Result | Accuracy |
|------|-------|--------|----------|
| GPU benchmark | `dumpsys gfxinfo framestats` | 53-66 FPS | **Wrong** — benchmark on-screen: 5.7-58.3, avg 19.5 FPS |
| Launcher idle | `dumpsys gfxinfo framestats` | 0 FPS | Correct |
| Launcher scrolling | `dumpsys gfxinfo framestats` | 108 FPS | Correct — 120Hz display, smooth scroll |
| Plain `dumpsys gfxinfo` | Total frames delta | 59-109 FPS | **Wrong** — same issue, measures View-system frames |
| `SurfaceFlinger --latency` | Any layer name | No frame data | Not working on this device |
| `service call SF 1013` | SurfaceFlinger | No output | Not working on this device |
| `measured_fps` sysfs | Kernel paths | Not found | Not available on MediaTek |
| `fpsgo_status` | Kernel path | Not found | Not available |

### Verdict

`dumpsys gfxinfo $pkg framestats` correctly measures **per-app UI FPS** for View/Compose apps.
For OpenGL/Vulkan games using SurfaceView, it measures only the UI overlay frames, not GPU
scene frames. SurfaceFlinger-based approaches don't work on this Android 16 device.

---

## Recommendation for MKM

**Use `dumpsys gfxinfo framestats` as the primary approach (Method 1), with
`SurfaceFlinger --latency` as a fallback for SurfaceView apps when available (Method 2).**

For 95% of apps users monitor, gfxinfo is accurate. GPU game FPS requires SurfaceFlinger
tracking which isn't available on Android 16 — the Choreographer fallback already handles
MKM's own UI FPS.

### Implemented fallback chain

```
hasElevatedAccess? → dumpsys gfxinfo $pkg framestats (C++ JNI parses → fps_binder.cpp)
    ↓ (no data from gfxinfo)
SurfaceFlinger --latency (C++ JNI parses → fps_binder.cpp)
    ↓ (no elevated access / no data)
Choreographer (MKM's own UI FPS — pure Kotlin)
```

---

## References

| Source | Approach | Works on Device? | Native Code? |
|--------|----------|------------------|--------------|
| [vtools_en](https://github.com/ramabondanp/vtools_en) 4.7.1 | `gfxinfo framestats` + FPSGO + SF service call | gfxinfo: Yes | `libnative-lib.so` (kernel prop reads) + `binder*.so` (native Binder dumpsys executables) |
| Scene 9.3.5 APK | Same chain as vtools_en + native Binder | gfxinfo: Yes, SF: No | `libnative-lib.so` + `binder12-15.so` (versioned for Android API levels) |
| **MKM (libfpsbinder.so v2)** | gfxinfo + SF latency, text-in parsing via C++ JNI | gfxinfo: Yes, SF: Partial | `fps_binder.cpp` (787 lines, C++17, links `liblog` only) |
| FPS Monitor 2.1.1 (rikka) | Native .so library (black box) | Unknown | Native .so |
| [Scene7_ExtremeGT](https://github.com/AmirulAndalib/Scene7_ExtremeGT) | Thermal bypass, not FPS measurement | N/A | N/A |
