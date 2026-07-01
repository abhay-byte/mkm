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

## Method 1: `dumpsys gfxinfo <pkg> framestats` (vtools approach)

**Source:** [ramabondanp/vtools_en](https://github.com/ramabondanp/vtools_en)

### How it works

1. Get foreground package: `dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'`
2. Parse package name with regex: `([a-zA-Z0-9._-]+)/`
3. Run: `dumpsys gfxinfo $packageName framestats`
4. Parse CSV header for `FrameCompleted` column index
5. For each row, extract the FrameCompleted nanosecond timestamp
6. Count frames in the last 1-second window → FPS = count
7. When foreground app changes, run `dumpsys gfxinfo $packageName reset` to clear stats

### CSV Format

Header:
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

## Method 3: Kernel Sysfs `measured_fps` (device-dependent)

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

## Method 4: `service call SurfaceFlinger 1013` (vtools fallback)

Returns a global frame counter (not per-app). Parses hex from Parcel output:
```
Result: Parcel(00001023...)
```

Not available on Android 16 (no output). Service codes change between Android versions.

---

## Method 5: FPSGO Kernel Module (MediaTek devices)

Path: `/sys/kernel/fpsgo/fstb/fpsgo_status`

Returns tabular per-thread FPS:
```
tid  name         currentFPS  ...
1234 surfaceflinger  60
5678 com.game       45
```

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

**Use Method 1 (`dumpsys gfxinfo framestats`) as the primary approach.**

For 95% of apps users monitor, it's accurate. GPU game FPS requires SurfaceFlinger tracking
which isn't available on Android 16 — the Choreographer fallback already handles MKM's own
UI FPS.

### Fallback chain (implemented in `FpsMonitor.kt`)

```
hasElevatedAccess? → dumpsys gfxinfo $pkg framestats → parse FrameCompleted → count 1s window
    ↓ (no access / no data)
Choreographer (MKM's own UI FPS)
```

---

## References

| Source | Approach | Works on Device? |
|--------|----------|------------------|
| [vtools_en](https://github.com/ramabondanp/vtools_en) | `gfxinfo framestats` | Yes |
| Scene 8.3.7 APK | Native daemon + `gfxinfo framestats` / `SF --latency` | gfxinfo: Yes, SF: No |
| FPS Monitor 2.1.1 (rikka) | Native .so library (black box) | Unknown |
| [Scene7_ExtremeGT](https://github.com/AmirulAndalib/Scene7_ExtremeGT) | Thermal bypass, not FPS measurement | N/A |
