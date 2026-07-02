# Proposed `binder.so` vs MKM Implementation — Analysis

Comparison of the proposed "complete binder.so implementation" against the live
`fps_binder.cpp` v2 in the repo to identify useful additions and architectural gaps.

---

## What the Proposed Code Has That We Don't

### 1. `--latency-frameinfo` Support (Android 12+)

The proposed code adds `getLayerFrameInfo()` which calls `dumpsys SurfaceFlinger
--latency-frameinfo <layer>`. This returns 7-field rows with more precise timing data:

```
frameNumber vsyncId inputEventTime animationStartTime
gpuCompositionStartTime gpuCompositionEndTime presentTime
```

**Status in MKM:** Not implemented. Only `--latency` (3-column format) is used.

**Usefulness:** Medium. On Android 12+ where `--latency` is broken (SoloX#303),
`--latency-frameinfo` may still work. It provides GPU composition timing in addition
to presentation timing.

**Recommendation:** Add as a first-try fallback between `--latency` and gfxinfo.
The parsing is straightforward (7 int64 fields) and would need:
- New JNI entry: `nativeFpsFromLatencyFrameinfoText(text)` 
- New text-in entry paired with a Kotlin `shellExec("dumpsys SurfaceFlinger --latency-frameinfo '$layer'")`
- Fall through if empty (device doesn't support it)

### 2. Old SurfaceFlinger Dump Format Parsing (`+ Layer 0x...`)

The proposed code has `parseLayers()` that regex-matches the old pre-AOSP-main format:

```
+ Layer 0x7a4c5c0e20 (SurfaceView[com.game/...]#0)
- Layer 0x7a4c5c0e20 (com.game/.../MainActivity#0)
  BufferStateLayer (com.game/.../MainActivity#0)
```

**Status in MKM:** Not implemented. Only the current `--list` format
(`RequestedLayerState{...}`) is parsed.

**Usefulness:** Low. The old format only appears from `dumpsys SurfaceFlinger` (bare, no
`--list`), and MKM uses `--list` exclusively. However, this could serve as a
compatibility fallback on Android 13 and below.

**Recommendation:** Skip for now. The `--list` format is universal on modern Android.
Only add if we get crash reports from Android 12-13 devices where `--list` has issues.

### 3. Frame Counter Delta Mode

The proposed code has `parseFrameCounters()` that reads `frame=NNNN` lines from the
old full dump format, then `computeFpsFromCounter()` for delta-based FPS.

**Status in MKM:** Acknowledged as deprecated. The v2 header explicitly states:
"the --list format has no per-layer frame counters to poll."

**Usefulness:** None on modern Android. The `frame=NNNN` lines only exist in the old
bare `dumpsys SurfaceFlinger` format, not in `--list`. MKM already has this correct.

### 4. Hook Mode with va_args

The proposed code has a `hooked_ioctl()` implementation using `va_args`, then provides
a GOT patching section with references to bytehook/xhook for real PLT hooking.

**Status in MKM:** Removed. v2 correctly documents why: apps submit frames through
per-connection `ISurfaceComposerClient` handles, not a single shared SF handle. Filtering
by `g_sfBinderHandle` can never see per-app buffer submissions.

**Usefulness:** None. MKM's v2 analysis is correct.

### 5. Deduplication via `std::set<std::string> seen`

The proposed code's `parseLayers()` uses a `seen` set to deduplicate layer names. The
actual `findLayerCandidatesForPackage()` already does this. Both are equivalent.

---

## What MKM's Actual Code Has That the Proposal Misses

### 1. Staleness Check (critical)

MKM v2 has a staleness check in `fpsFromSortedTimestampsNanos()`:

```cpp
if (nowNanos - latest > 1500000000LL) return 0.0;  // 1.5s stale → 0
```

The proposed code has no staleness detection — it would report stale FPS forever.
This is the bug that v1 had (the v2 rewrite fixed precisely this).

### 2. Low-Frame-Rate Protection

MKM v2 distinguishes between normal and low-frame-rate states:

```cpp
if (count >= 15 && deltaNanos >= 200000000LL) {
    // Normal: (n-1)/window → accurate FPS
} else {
    // Low rate: raw count → avoids math explosion (2 frames in 12ms → 79 FPS)
}
```

The proposed code always does `(n-1)/seconds`, which explodes for low frame rates.

### 3. Multi-Block gfxinfo Parsing

MKM v2 returns `std::vector<std::vector<int64_t>>` (multiple `---PROFILEDATA---` blocks)
and takes the maximum FPS across all blocks. The proposed code only handles a single block.

### 4. CSV Field Trimming

MKM v2 calls `trim()` on each CSV field before parsing, handling whitespace around
commas. The proposed code doesn't trim, which can cause parse failures with
indented/modified `dumpsys gfxinfo` output.

### 5. Source Tracking (`nativeGetLastFpsSource`)

MKM v2 exposes which source produced the FPS reading via `nativeGetLastFpsSource()`.
The proposed code has no introspection mechanism — an overlay can't show whether FPS
came from gfxinfo or SurfaceFlinger.

### 6. Latency-Frameinfo Already Considered (via `--latency-frameinfo`)

The proposed code mentions `--latency-frameinfo` in a duplicated code section (the file
has a bug where `getLayerFrameInfo` appears twice). The actual v2 code doesn't have it,
so this is the one genuinely useful feature from the proposal.

---

## Concrete Recommendation

### Worth adding immediately

Nothing urgent. The current implementation covers all the cases the proposal handles,
plus critical edge cases (staleness, low-frame-rate, multi-block gfxinfo) that the
proposal misses.

### Worth adding as a future enhancement

**`--latency-frameinfo` fallback** (Android 12+). This is the only feature in the proposal
that's not in the current code. It's a different dump format (7-field vs 3-field) that
may work on devices where `--latency` is broken. Add it between the `--latency` step
and the gfxinfo fallback in both `getBestFpsForPackage()` (C++) and `FpsBinder.computeFps()`
(Kotlin).

```cpp
// In getBestFpsForPackage(), after --latency fails:
for (const auto& candidate : candidates) {
    auto frames = parseLatencyFrameinfo(
        sfDump({"--latency-frameinfo", candidate}));
    if (frames.empty()) continue;
    double fps = computeFpsFromLatencyFrames(frames);
    if (fps > 0.0) return fps;
}
```

### Not worth adding

- **Old SF dump format parsing** — no known devices need it with `--list`
- **Frame counter delta mode** — dead format, `--list` doesn't have it
- **Hook/ioctl interception** — architecturally impossible for per-app tracking
- **`--latency` raw count FPS** — MKM's (n-1)/window math is more accurate

---

## FPS Source Priority Chain (Current vs Proposed)

| Priority | Current MKM v2 | Proposed |
|----------|---------------|----------|
| 1 | gfxinfo framestats (text-in) | `--latency` on layer |
| 2 | `--latency` (text-in, first candidate) | `--latency-frameinfo` on layer |
| 3 | Return -1.0 (fallthrough to Choreographer) | `--latency` all candidates |
| 4 | — | gfxinfo framestats |
| 5 | — | Frame counter delta |

**MKM's ordering is correct** — gfxinfo is proven robust, `--latency` is a best-effort
upgrade. The proposal's latency-first ordering would give degraded results on the
majority of View-based apps where gfxinfo is the only accurate source.

### Recommended future priority chain

```
1. gfxinfo framestats (primary — works for all View/Compose apps)
2. SurfaceFlinger --latency (SurfaceView GPU frames — broken on A15/A16)
3. SurfaceFlinger --latency-frameinfo (NEW — Android 12+, may work where --latency doesn't)
4. Return -1.0 (Choreographer fallback for MKM's own UI)
```
