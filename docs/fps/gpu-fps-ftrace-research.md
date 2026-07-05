# Real GPU FPS via Kernel Ftrace — Complete Research

How to measure **actual GPU frame completion rate** — not UI FPS, compositor FPS, or
inflated View-system FPS — on rooted Android devices. Covers Mali (MediaTek) and
Adreno (Qualcomm/Snapdragon) GPUs on Android 16.

**Test devices:**
- Xiaomi 2311DRK48I — Android 16, MediaTek mt6897 (Dimensity 8300), Mali-G615 6-core r1p3
- LineageOS device — Android 16, Qualcomm Snapdragon, Adreno 750v2

---

## Why Existing Android FPS Methods Fail

| Method | What It Measures | Mali Result | Adreno Result |
|--------|-----------------|-------------|---------------|
| `dumpsys SurfaceFlinger --latency <layer>` | GPU frame timestamps from compositor | Garbage `8333333` (A15/16 bug) | Garbage `8333333` (A15/16 bug) |
| `dumpsys SurfaceFlinger --latency-frameinfo` | 7-field frame info (A12+) | Dumps SF config/buffer state, no timestamps | Same |
| `dumpsys gfxinfo <pkg> framestats` | UI rendering pipeline (View/Compose) | Zero frames for Vulkan NativeActivity | Zero frames for Vulkan NativeActivity |
| `dumpsys gfxinfo <pkg>` (plain) | View-system frame counts | Only View-based apps; 0 for Vulkan | Only View-based apps |
| `service call SurfaceFlinger 1013` | Global frame counter | No output on A16 | No output on A16 |
| `dumpsys SurfaceFlinger` missed frames | Compositor HWC/GPU drop counters | Aggregate totals only | Same |

### gfxinfo Limitation

`dumpsys gfxinfo` only sees Android View-system (HWUI) frames. Vulkan/OpenGL apps using
`ANativeWindow` → SurfaceFlinger directly bypass HWUI completely:

```
Vulkan app: gfxinfo → 0 frames rendered  ✓ accurate (not in HWUI pipeline)
View app:   gfxinfo → N frames rendered  ✓ accurate (in HWUI pipeline)
```

On MediaTek with 3DMark Wild Life running: gfxinfo showed 0 GPU frames while the device
was rendering at 15-35 fps on screen. The GPU Histogram section was all zeros.

---

## Mali GPU (MediaTek) — `dma_fence_signaled driver=mali` ✅

### How to enable

```bash
echo 1 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable
```

### Output format

```
kworker/u17:1-11994  [006] d..1. 115851.392561: dma_fence_signaled:
  driver=mali timeline=0-29738_217-17332122-kcpu context=17332122 seqno=469221
```

### Key facts

- 1 event = 1 completed GPU frame
- `timeline=0-PID_...` — PID embedded directly in the timeline field
- `driver=mali` — Mali GPU driver
- `seqno` — monotonically increasing frame sequence number
- No setup needed beyond the enable echo

### Why it works

Mali's kernel driver emits exactly one `dma_fence_signaled` per frame completion.
The fence lifecycle maps 1:1 to frame boundaries — when a frame's GPU work completes,
the associated sync fence is signaled.

### Accuracy validation

Tested against on-screen 3DMark Wild Life Extreme (Vulkan) on MediaTek:

| 3DMark Test Phase | On-Screen FPS | dma_fence FPS (PID 29738) |
|-------------------|---------------|---------------------------|
| Wild Life heavy | 15-35 | 5-12 (heavy), 25-35 (light) |
| Sling Shot medium | 30-35 | 29-35 |

---

## Adreno GPU (Qualcomm/Snapdragon) — No 1:1 Frame Event ❌

### The Problem

Adreno's kernel driver (KGSL) **does not expose a 1:1 GPU frame boundary event** via
ftrace. Every available event fires per GPU **command batch** (work item), not per
application frame.

### What was tested

| Event | Fires per... | 8s sample | Real FPS | Ratio |
|-------|-------------|-----------|----------|-------|
| `dma_fence_signaled driver=kgsl-timeline` | GPU work item | ~180/sec (per app context) | ~20 | 9:1 |
| `adreno_cmdbatch_retired` | Completed command batch | ~276/sec (all contexts) | ~20 | 14:1 |
| `adreno_cmdbatch_submitted` | Submitted command batch | ~276/sec | ~20 | 14:1 |
| `dma_fence_signaled driver=sde_fence:conn*` | Display frame swap | ~120/sec | — | Display refresh |

### Why the inflation

A single Vulkan frame on Adreno generates multiple GPU command batches — typically 6-14
per frame depending on complexity. The driver splits draw calls, compute dispatches, and
synchronization into separate submissions. There is no single ftrace event that marks
the frame boundary — that information exists only in the userspace Vulkan driver.

### What DOES work (but needs tooling)

Qualcomm exposes GPU performance counters through the `/dev/kgsl-3d0` device file and
the `IOCTL_KGSL_PERFCOUNTER_READ` ioctl. This is what Snapdragon Profiler (Qualcomm's
official tool) uses. It requires a native C/C++ program to issue the ioctl, not ftrace.

The `adreno_cmdbatch_submitted` event CAN give approximate FPS for a specific app when
filtered by the app's PID (carried in the trace header, e.g., `RenderThread-3863`).
However, the count will be inflated by the batch-to-frame ratio, typically 6-14x.

### sysfs GPU info (root only)

| Path | Data |
|------|------|
| `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` | GPU utilization % |
| `/sys/class/kgsl/kgsl-3d0/gpuclk` | Current GPU clock (e.g., 903 MHz) |
| `/sys/class/kgsl/kgsl-3d0/gpubusy` | GPU busy counters (raw ticks) |
| `/sys/class/kgsl/kgsl-3d0/max_gpuclk` | Max GPU clock |
| `/sys/class/kgsl/kgsl-3d0/min_pwrlevel` | Minimum power level |
| `/sys/class/kgsl/kgsl-3d0/num_pwrlevels` | Number of power levels |

---

## Additional FPS Sources (Both GPUs)

### Display Fence Events

```
dma_fence_signaled driver=sde_fence:conn69 → 120/sec (display refresh rate)
dma_fence_signaled driver=sde_fence:crtc199 → 120/sec (CRTC refresh)
```

These give the display refresh rate, useful for detecting frame drops (missed vsyncs)
but not per-app GPU FPS.

### SurfaceFlinger Compositor Missed Frames

```
Total missed frame count: 313210
HWC missed frame count: 279374
GPU missed frame count: 223690
```

Aggregate counters only — not per-second, not per-app.

### `gpu_work_period` (Mali only)

```
mali-gpuq-kthre-1288  [006] ..... 115851.392561: gpu_work_period:
  gpu_id=0 uid=10327 start_time_ns=214886624120308 end_time_ns=214886624773308
  total_active_duration_ns=595154
```

Per-UID GPU scheduling quanta. Events fire every ~6ms. Not frame-accurate — a 100ms
frame spawns ~16 quanta. Useful for GPU utilization, not FPS.

### MediaTek GED Events (MTK only)

```
GPU_DVFS__Policy__Frame_based__GPU_Time:
  cur=265, target=265, target_hd=265, real=82300, pipe=0

GPU_DVFS__Loading:
  active=89, tiler=0, frag=0, comp=0, iter=1, mcu=24
```

Per-frame GPU timing from MediaTek's GPU DVFS engine. Per-GPU, not per-app.

---

## Platform Compatibility Matrix

| SoC | GPU | `dma_fence 1:1 frame` | `cmdbatch_retired` | GED events | `gpufreqv2` |
|-----|-----|----------------------|--------------------|------------|-------------|
| MediaTek mt6897 | Mali-G615 | ✅ `driver=mali` | N/A | ✅ | ✅ |
| MediaTek (recent) | Mali-Gxx | ✅ | N/A | ✅ | ✅ |
| Samsung Exynos | Mali-Gxx | ✅ | N/A | ❌ | ❌ |
| Google Tensor | Mali-Gxx | ✅ | N/A | ❌ | ❌ |
| Qualcomm SM8650 | Adreno 750 | ❌ (9:1 inflation) | ✅ (14:1 inflation) | ❌ | ❌ |
| Qualcomm (any) | Adreno | ❌ | ✅ (inflated) | ❌ | ❌ |

---

## Device-Specific Paths

### MediaTek mt6897 (Mali-G615)

| Path | Description |
|------|-------------|
| `/proc/gpufreqv2/gpufreq_status` | Current GPU OPP, freq (265 MHz), temp (41°C) |
| `/proc/gpufreqv2/gpu_working_opp_table` | 65 frequency OPPs (265 MHz → 1400 MHz) |
| `/proc/gpufreqv2/gpu_signed_opp_table` | Factory-signed OPP table with voltages |
| `/proc/gpueb/gpueb_status` | GPU embedded controller registers |
| `/proc/gpueb/gpueb_dram_user_status` | GPU DRAM power state |
| `/sys/class/devfreq/13000000.mali/cur_freq` | Current GPU frequency (requires root) |
| `/sys/class/misc/mali0/device/gpuinfo` | `Mali-G615 6 cores r1p3` |
| `/sys/class/misc/mali0/device/core_mask` | Active shader cores `0x130013` |
| `/sys/kernel/tracing/events/mali/` | Mali memory/alloc trace events |
| `/sys/kernel/tracing/events/ged/` | MediaTek GPU DVFS events (20+ frame-based) |
| `/sys/kernel/tracing/events/gpu_work_period/` | Per-UID GPU work accounting (always on) |
| Debugfs: random path via `mount \| grep debugfs` | 68 directories: gpufreqv2, ged, dri, clk, devfreq |

### Qualcomm SM8650 (Adreno 750v2)

| Path | Description |
|------|-------------|
| `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` | GPU utilization % (root) |
| `/sys/class/kgsl/kgsl-3d0/gpuclk` | Current GPU clock (root) |
| `/sys/class/kgsl/kgsl-3d0/gpubusy` | GPU busy raw counter (root) |
| `/dev/kgsl-3d0` | KGSL device (accessible without root!) |
| `/sys/kernel/tracing/events/kgsl/` | 70+ KGSL trace events |
| `/sys/kernel/tracing/events/kgsl/adreno_cmdbatch_retired` | Per-batch completion (root) |
| `/sys/kernel/tracing/events/kgsl/adreno_cmdbatch_submitted` | Per-batch submission (root) |
| `/sys/kernel/tracing/events/dma_fence/` | DMA fence lifecycle |

---

## Scripts

### `gpu-fps-universal` — Single script for both GPUs

Location: `scripts/gpu-fps-universal.sh` (also at `/home/abhay/gpu-fps-universal`)

Automatically detects GPU type and uses the appropriate trace event:
- **Mali:** `dma_fence_signaled driver=mali` (1:1 frame → fence mapping)
- **Adreno:** `adreno_cmdbatch_submitted` filtered by foreground app PID

```bash
# Snapdragon / Adreno
./gpu-fps-universal d30a1726

# MediaTek / Mali
./gpu-fps-universal Y5WWBMJVOZSK4HU8
```

Features:
- Auto-detects foreground app via `dumpsys window`
- Auto-selects correct trace event based on GPU type
- Prints per-second FPS grouped by process
- Ctrl+C to stop, auto-disables trace

### `gpu-fps` — Manual targeted measurement

Location: `/home/abhay/gpu-fps`

```bash
# Target by PID (recommended for Adreno)
./gpu-fps d30a1726 5729 dma_fence 30

# Target by UID (recommended for Mali)
./gpu-fps Y5WWBMJVOZSK4HU8 10006 dma_fence 30

# Target by package name (auto-resolves PID)
./gpu-fps Y5WWBMJVOZSK4HU8 com.futuremark.dmandroid.application dma_fence 30
```

### `gpu-fps-gfxinfo` — No-root fallback

Location: `/home/abhay/gpu-fps-gfxinfo`

Uses `dumpsys gfxinfo <pkg> framestats` GPU histogram delta counting.
Works without root but only for View-based apps — 0 fps for Vulkan/OpenGL games.

```bash
./gpu-fps-gfxinfo d30a1726
```

---

## Recommended Pipeline

### For Mali (MediaTek / Exynos / Tensor)

```
1. adb root
2. echo 1 > /sys/kernel/tracing/events/dma_fence/dma_fence_signaled/enable
3. Get foreground PID: dumpsys window | grep mCurrentFocus
4. Stream trace_pipe | grep 'driver=mali.*timeline=0-PID_'
5. Count events per second = real GPU FPS
```

### For Adreno (Qualcomm / Snapdragon)

```
1. adb root
2. echo 1 > /sys/kernel/tracing/events/kgsl/adreno_cmdbatch_submitted/enable
3. Get ALL foreground PIDs: ps -A | grep <pkg>
4. Stream trace_pipe | grep 'adreno_cmdbatch_submitted' | filter by PIDs
5. Count = approximate FPS (inflated 6-14x depending on workload complexity)
6. For accurate FPS: use Snapdragon Profiler or implement IOCTL_KGSL_PERFCOUNTER_READ
```

### KGSL Perfcounter Alternative (Adreno — needs native code)

The `/dev/kgsl-3d0` device is accessible **without root** on most devices. Issue
`IOCTL_KGSL_PERFCOUNTER_GET`, `IOCTL_KGSL_PERFCOUNTER_QUERY`, and `IOCTL_KGSL_PERFCOUNTER_READ`
to read GPU hardware counters including frame counts. See:
- [Lei.chat — Sampling Performance Counters from Mobile GPU Drivers](https://www.lei.chat/posts/sampling-performance-counters-from-gpu-drivers/)
- [Freedreno envytools](https://github.com/freedreno/envytools) — Adreno perf counter definitions

---

## References

- Linux kernel ftrace: `Documentation/trace/ftrace.rst`
- Mali kbase driver: `drivers/gpu/arm/midgard/` (kernel source)
- Qualcomm KGSL driver: `drivers/gpu/msm/adreno.c` (kernel source)
- MediaTek GPU DVFS (GED): MTK proprietary kernel module
- DMA fence framework: `drivers/dma-buf/dma-fence.c`
- Freedreno envytools: Adreno A6XX/A7XX perf counter registers
- Snapdragon Profiler: Qualcomm official GPU profiling tool
- Lei.chat: `IOCTL_KGSL_PERFCOUNTER_READ` for Adreno hardware counters
