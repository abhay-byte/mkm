# Adreno GPU FPS — All Known Approaches (Tested)

Device: Snapdragon 8 Gen 3 (pineapple), Android 15, kernel `kgsl` ftrace available.

## Legend
- ✅ **Works** (reliable)
- ⚠️ **Works — Approximate** (needs calibration, ~60% accuracy)
- ❌ **Tested — Doesn't Work** (zero events, wrong data, or same problem as submitted)
- 🔬 **Untested**

---

## Tier 1 — Ftrace: Direct GPU Events

### ⚠️ `kgsl/adreno_cmdbatch_submitted` — Gap-burst detection
**Old method.** Fires 5-6× per frame in sub-command bursts. ~60% accuracy. Superseded.

### ⚠️ `kgsl/adreno_cmdbatch_queued` — Gap-burst detection
**Tested.** Same burst pattern as submitted but fires slightly earlier. Gap-burst with 14ms threshold matches syncpoint_fence exactly (28.4 FPS). Requires gap heuristic, which can break at high frame rates. Prefer syncpoint_fence below.

### ✅ `kgsl/syncpoint_fence` — Ratio-based (4:1)
**Current method.** Fires 4 events per frame (2 KGSL syncobj contexts × 2 syncpoints per context). Ratio is stable across scenes — tested 252→63, 248→62, 344→86, all matching gap-burst ground truth within ±1 frame. No heuristic thresholds — just `events / (unique_ctx_count * 2) / seconds`. **This is the winner.**
```
grep "syncpoint_fence.*(APP_PFX" trace | wc -l  →  events / (ctx_count * 2) / seconds = FPS
```

### ❌ `kgsl/adreno_cmdbatch_submitted` — Raw counting
~200+ FPS for a 24 FPS scene. Garbage.

### ❌ `kgsl/adreno_cmdbatch_retired`
Barely fires (0-3 events in 2s). Useless.

### ❌ `kgsl/adreno_cmdbatch_done`
**Tested.** Fires with identical 5-6× burst pattern as `submitted`. Same multi-burst problem, same need for gap detection, same ~60% accuracy. No improvement over `submitted`.

### ❌ `kgsl/adreno_syncobj_retired` / `adreno_syncobj_submitted`
**Tested.** Zero events fire on this kernel. Format exists (`ctx= ts=` for retired, `num_sync=` for submitted) but the events are never emitted during Vulkan rendering. Dead end.

### ❌ `kgsl/adreno_drawctxt_switch`
**Tested.** Zero events. Uses `oldctx`/`newctx` with `rb_level` fields. Doesn't fire on this SoC.

---

## Tier 2 — Ftrace: Fence-Based Signals

### ❌ `dma_fence/dma_fence_signaled` (kgsl-timeline) — Single highest context
Original approach. Only counted busiest context. Garbage.

### ❌ `dma_fence/dma_fence_signaled` (kgsl-timeline) — All contexts summed ÷2
Gave 37-52 FPS for a 24 FPS scene. Ratio varies by workload.

### ❌ `dma_fence/dma_fence_init` (kgsl-timeline)
**Tested.** 532 init events vs 536 signaled events in 2s. Same ratio (~2.2 fences per vsync) as `signaled`. Same calibration problem. No advantage.

### ❌ `kgsl/kgsl_timeline_signal`
**Tested.** Zero events fire. Format exists (`id=` `seqno=`) but never emitted on this kernel.

---

## Tier 3 — Display-Side (Global, Not Per-App)

### ✅ `dma_fence/dma_fence_signaled` — `driver=sde_fence:crtc*`
**Tested.** 1:1 with physical display vsync. Confirmed 122 Hz on 120Hz panel (double-pumped). 100% reliable. **Global only** — can't isolate per-app FPS. Useful as calibration ground truth.

### ✅ `dma_fence/dma_fence_signaled` — `driver=sde_fence:conn*`
**Tested.** Same rate as crtc. No additional value.

### ❌ `drm/drm_vblank_event` / `drm_vblank_event_delivered`
**Tested.** Zero events fire. Format exists (`crtc=` `seq=` `time=`) but never emitted. `sde_fence:crtc` is the working display vsync path.

---

## Tier 4 — Sysfs / Power Events

### ✅ `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage`
**Tested.** 99% during benchmark. GPU utilization, not FPS. Cheap sanity check.

### ✅ `power/gpu_work_period`
**Tested.** Fires per-UID with `total_active_duration_ns`. 3DMark UID=10433 shows ~905ms active in ~928ms windows. Per-app GPU busy time, **not FPS**. Useful for: detecting throttling, correlating with FPS drops, cross-platform (vendor-neutral).

### 🔬 `/sys/class/kgsl/kgsl-3d0/gpu_clock_stats`
Untested. GPU frequency residency histogram.

---

## Tier 5 — Android Framework (User-Space)

### ❌ `dumpsys gfxinfo framestats`
Returns 0 for Vulkan native apps. HWUI/RenderThread-only.

### ❌ `dumpsys SurfaceFlinger --latency`
Per-layer timestamps measure SurfaceFlinger's own composite, not the game's GPU frames.

### ❌ Choreographer frame callbacks
Java-level, bypassed by native/Vulkan.

### ❌ Perfetto FrameTimeline
Keys app timing off `AChoreographer_vsyncCallback`. 3DMark's uncapped Vulkan loop never registers one. Perfetto docs recommend AGI for GPU profiling instead.

---

## Summary Matrix

| Method | Tested | Result |
|--------|--------|--------|
| `adreno_cmdbatch_submitted` (gap-burst) | Yes | ⚠️ ~60% — superseded |
| `syncpoint_fence` (4:1 ratio) | Yes | ✅ 28.4 FPS — stable ratio |
| `adreno_cmdbatch_queued` (gap-burst) | Yes | ✅ 28.4 FPS — matches syncpoint |
| `adreno_cmdbatch_submitted` (raw) | Yes | ❌ Garbage |
| `adreno_cmdbatch_retired` | Yes | ❌ Too few events |
| `adreno_cmdbatch_done` | Yes | ❌ Same burst pattern as submitted |
| `adreno_syncobj_retired/submitted` | Yes | ❌ Zero events on this kernel |
| `adreno_drawctxt_switch` | Yes | ❌ Zero events |
| `dma_fence_signaled` (single ctx) | Yes | ❌ Garbage |
| `dma_fence_signaled` (sum ÷ 2) | Yes | ❌ ~50% accuracy |
| `dma_fence_init` (kgsl) | Yes | ❌ Same ratio as signaled |
| `sde_fence:crtc` (display vsync) | Yes | ✅ 100% — global only |
| `gpu_work_period` | Yes | ✅ Per-app GPU time — not FPS |
| `gpu_busy_percentage` | Yes | ✅ Utilization — not FPS |
| `dumpsys gfxinfo` | Yes | ❌ 0 for Vulkan |
| `dumpsys SurfaceFlinger --latency` | Yes | ❌ Measures SF, not GPU |
| Choreographer callbacks | Yes | ❌ Bypassed by native/Vulkan |
| Perfetto FrameTimeline | Yes | ❌ No Choreographer = no data |
| `kgsl_timeline_signal` | Yes | ❌ Zero events |
| `drm_vblank_event` | Yes | ❌ Zero events |
| `gpu_clock_stats` | No | 🔬 Untested |

---

## What Actually Works Right Now

1. **`sde_fence:crtc*`** — Ground-truth display vsync. Use for calibration reference.
2. **`gpu_busy_percentage`** — Sanity check GPU is loaded during tests.
3. **`gpu_work_period`** — Per-app GPU active time. Correlates with FPS but isn't FPS.

None of the per-app GPU frame-counting events provide clean 1:1 frame counting on this kernel/SoC combination.
