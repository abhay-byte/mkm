# Adreno GPU FPS — All Known Approaches

## Legend
- ✅ **Tested — Works** (reliable, ±5% accuracy)
- ⚠️ **Tested — Unreliable** (±20-40% off, not production-usable)
- ❌ **Tested — Doesn't Work** (garbage output)
- 🔬 **Untested** (theory only, never run on device)

---

## Tier 1 — Ftrace: Direct GPU Events

### ⚠️ `kgsl/adreno_cmdbatch_submitted` — Gap-based burst detection
**Current method in gpu-fps-snapdragon.sh**

Fires 5-6× per GPU frame in sub-command bursts. Uses gap detection (>12ms = new frame) across merged app contexts. Accuracy ~60% — drifts high and low, not reliable for production.

```
grep "ctx=17 |ctx=21 " trace | awk '{ts=$4} NR==1{f=1;next} {if((ts-p)*1000>12)f++;p=ts} END{print f}'
```

### ❌ `kgsl/adreno_cmdbatch_submitted` — Raw counting
Fires 5-6× per frame. Direct counting gives ~200+ FPS for a 24 FPS scene. Worthless without gap detection.

### ❌ `kgsl/adreno_cmdbatch_retired`
Barely fires (0-3 events in 2s). Gap-based: 1-3 bursts max. Useless.

### 🔬 `kgsl/adreno_cmdbatch_done`
Untested. Might be 1:1 since it fires after entire batch completes, not per sub-command.

### 🔬 `kgsl/adreno_drawctxt_switch`
Untested. Fires once per GPU context switch. If app owns one context = 1:1 per frame.

### 🔬 `kgsl/adreno_syncobj_retired` / `adreno_syncobj_submitted`
Untested. Sync objects are Vulkan frame-boundary markers. Likely closest to true 1:1.

### 🔬 `kgsl/adreno_preempt_done` / `adreno_preempt_trigger`
Untested. Preemption events may align with frame boundaries.

---

## Tier 2 — Ftrace: Fence-Based Signals

### ❌ `dma_fence/dma_fence_signaled` (kgsl-timeline) — Single highest context
**Original broken approach.** Only counted busiest context, ignored 40-60% of events. PID prefix match fragile.

### ❌ `dma_fence/dma_fence_signaled` (kgsl-timeline) — All contexts summed ÷2
Summed all app contexts then ÷2 (assumed Vulkan ratio). Gave 37-52 FPS for a 24 FPS scene. Ratio varies by workload — not a constant.

### 🔬 `dma_fence/dma_fence_init` (kgsl-timeline)
Untested. Counts fence *creations* not completions. Different ratio, maybe better.

### 🔬 `kgsl/kgsl_timeline_signal`
Untested. Same data as dma_fence_signaled but pre-filtered to KGSL only. Might have cleaner ratio.

### 🔬 `kgsl/kgsl_timeline_wait` / `kgsl_timeline_fence_alloc`
Untested. Wait-start vs alloc patterns could correlate with frame boundaries.

---

## Tier 3 — Display-Side (Global, Not Per-App)

### ✅ `dma_fence/dma_fence_signaled` — `driver=sde_fence:crtc199`
Works. 1:1 with physical display vsync. Confirmed ~112 FPS on 120Hz panel. **Global only** — can't isolate per-app FPS.

### 🔬 `dma_fence/dma_fence_signaled` — `driver=sde_fence:conn69`
Untested. Connector-side fence signals. Same FPS as CRTC but different timing.

### 🔬 `drm/drm_vblank_event` / `drm_vblank_event_delivered`
Untested. Standard DRM vblank event. Same concept as sde_fence:crtc.

### 🔬 `/sys/kernel/debug/dri/0/crtc-*/vblanks`
Untested. File-based vblank counter. Poll without ftrace overhead.

### 🔬 `sde/sde_perf_crtc_update`
Untested. SDE performance tracker. May count actual composition frames.

---

## Tier 4 — Sysfs / DebugFS (No Ftrace)

### ✅ `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage`
Works. GPU utilization %. Shows 99% during benchmark. Not FPS but useful correlator.

### 🔬 `/sys/class/kgsl/kgsl-3d0/gpu_clock_stats`
Untested. GPU frequency residency histogram. Not FPS.

### 🔬 `/sys/kernel/debug/dri/0/state`
Untested. DRM atomic state dump. Could be polled for frame transitions.

---

## Tier 5 — Android Framework (User-Space)

### ❌ `dumpsys gfxinfo <pkg> framestats`
Works for OpenGL/skia apps. Returns 0 for Vulkan native apps (3DMark, most benchmarks). Useless for GPU-bound games.

### ❌ `dumpsys SurfaceFlinger --latency`
SurfaceFlinger sees everything as one composited layer. Per-layer timestamps measure SF's own composition, not the game's GPU frames. Useless for per-app GPU FPS.

### ❌ Choreographer frame callbacks
Java-level frame timing (Choreographer.postFrameCallback). Pure native/Vulkan apps bypass this entirely. Returns 0 or SF composition frames, not GPU frames.

---

## Summary

| Method | Tested? | Accuracy |
|--------|---------|----------|
| `adreno_cmdbatch_submitted` (gap-burst) | Yes | ~60% — unreliable |
| `adreno_cmdbatch_submitted` (raw count) | Yes | Garbage |
| `adreno_cmdbatch_retired` | Yes | Garbage |
| `dma_fence_signaled` (highest ctx) | Yes | Garbage |
| `dma_fence_signaled` (sum ÷ 2) | Yes | ~50% — unreliable |
| `sde_fence:crtc` (display vsync) | Yes | 100% — but global only |
| `gpu_busy_percentage` | Yes | Works — utilization only |
| `dumpsys gfxinfo` | Yes | 0 for Vulkan |
| `dumpsys SurfaceFlinger --latency` | Yes | Measures SF, not GPU |
| Choreographer callbacks | Yes | Bypassed by native/Vulkan |
| `adreno_cmdbatch_done` | No | Unknown |
| `adreno_drawctxt_switch` | No | Unknown |
| `adreno_syncobj_retired/submitted` | No | Unknown — highest potential |
| `kgsl_timeline_signal` | No | Unknown |
| `dma_fence_init` (kgsl) | No | Unknown |
| `drm_vblank_event` | No | Unknown |
| SurfaceFlinger latency | No | Unknown |
