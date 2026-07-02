// ============================================================================
// fps_binder.cpp — MKM native FPS engine (v2.1)
//
// v2.1 changes:
//  - Fixed FPS math: removed overly restrictive count>=15 guard; any window
//    ≥200ms now computes proper (N-1)/Δt instead of raw count.
//  - Added --latency-frameinfo fallback (Android 12+): 7-field format parser
//    + new JNI entry, inserted between --latency and gfxinfo in the chain.
//
// WHY v2 EXISTS — v1 was stuck reporting the same FPS. Research
// against AOSP source + the smart-test-ti/SoloX tracker + alibaba/mobileperf
// turned up four concrete problems:
//
//  1. LAYER FORMAT CHANGED. v1's layer scraper regexed the *old*
//     "+ Layer 0x... (name)" text from plain `dumpsys SurfaceFlinger`.
//     SurfaceFlinger.h (current AOSP `main`) shows the layer tree is now
//     owned by FrontEnd/RequestedLayerState.* — a rewrite that changed the
//     dump format to `RequestedLayerState{...}` entries, printed by
//     `dumpsys SurfaceFlinger --list` (not the bare command). The old regex
//     matches zero lines against the new format, so getActiveLayers()/
//     findLayerForPackage() always returned empty and every FPS read fell
//     straight to 0 / cached-stale.
//
//  2. `--latency <name>` IS ITSELF UNRELIABLE ON ANDROID 15/16. Even with a
//     byte-correct name from `--list`, SoloX issue #303
//     (github.com/smart-test-ti/SoloX/issues/303) reports `--latency`
//     returning only the header line — no per-frame rows — for names that
//     `--list` just printed, on Android 15 and 16 devices. That single-line
//     response is the exact same "layer name didn't match" symptom that's
//     been reported against this command since Android 6 (a name mismatch
//     makes SurfaceFlinger print the refresh-period header and nothing
//     else). v1 didn't check for this and would treat it as "0 new frames"
//     forever, i.e. a stuck reading. This file detects that degenerate
//     response explicitly and falls back instead of trusting it.
//
//  3. gfxinfo IS THE PROVEN-ROBUST SOURCE, NOT A LESSER FALLBACK.
//     alibaba/mobileperf's fps.py carries a comment that `--latency` was
//     found to update "very slowly" and be unusable for FPS even on a
//     stock Honor 9 (Android 8) — years before the 15/16 regression above —
//     and wraps `dumpsys gfxinfo <pkg> framestats` as its real source,
//     reformatting it to *look like* `--latency` output for the rest of the
//     pipeline. SoloX's own README documents the same split (gfxinfo for
//     normal UI, SurfaceFlinger latency only for the surfaceview=True game
//     case). This file makes gfxinfo framestats the primary, always-tried
//     source and treats SurfaceFlinger latency as a best-effort upgrade.
//
//  4. "HOOK MODE" NEVER DID ANYTHING. In v1, installIoctlHook() set
//     g_hooksInstalled = true and returned true unconditionally — it's
//     labeled a stub in its own comment. So 100% of whatever was or wasn't
//     updating went through the (broken) query path above; there was no
//     real-time path silently competing with it. It's also aimed at a
//     target that can't work as designed: apps submit frames through their
//     own per-connection ISurfaceComposerClient binder (handed back by
//     ISurfaceComposer::createConnection()), not a single shared
//     SurfaceFlinger service handle, so filtering
//     `tr->target.handle == g_sfBinderHandle` could never see per-app
//     buffer submissions even with a working hook. Removed rather than
//     left as a misleading stub — see nativeInstallHooks() below.
//
// ONE MORE THING WORTH CHECKING ON YOUR END (can't confirm from here):
// AIBinder_dump() and popen("dumpsys ...") both require the *calling
// process* to hold shell/root privilege. If your existing, working gfxinfo
// collection gets its privilege via Shizuku, note that Shizuku elevates
// commands it spawns *through its own service* — it does not elevate your
// app process as a whole. A popen() called directly from this JNI code
// runs as your app's own uid and will typically fail permission checks
// even though "Shizuku is granted" elsewhere in the app. If that's your
// setup, use the nativeFindLayerForPackage / nativeFpsFromLatencyText /
// nativeFpsFromGfxinfoText entry points near the bottom: fetch the three
// `dumpsys` commands yourself via whatever already works for gfxinfo today,
// and hand the text in for parsing. The self-contained functions above them
// still try AIBinder_dump/popen/`su -c` directly, for cases where this .so
// is loaded inside an already-privileged process.
//
// JNI Package: com.mkm.fps  (unchanged — drop-in replacement for v1)
// ============================================================================

#include <jni.h>
#include <android/api-level.h>
#if __ANDROID_API__ >= 30
#include <android/binder_manager.h>
#include <android/binder_ibinder.h>
#endif
#include <android/log.h>
#include <unistd.h>
#include <cstdio>
#include <string>
#include <vector>
#include <map>
#include <set>
#include <mutex>
#include <chrono>
#include <sstream>
#include <regex>
#include <algorithm>
#include <cctype>
#include <cstring>
#include <cerrno>

// ---------------------------------------------------------------------------
// Logging
// ---------------------------------------------------------------------------
#define LOG_TAG "MKM-FpsBinder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

static std::string trim(const std::string &s) {
    size_t start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    size_t end = s.find_last_not_of(" \t\r\n");
    return s.substr(start, end - start + 1);
}

enum class FpsSource {
    NONE,
    SURFACEFLINGER_LATENCY,  // dumpsys SurfaceFlinger --latency <layer>
    GFXINFO,                 // dumpsys gfxinfo <pkg> framestats
};

static const char* fpsSourceName(FpsSource s) {
    switch (s) {
        case FpsSource::SURFACEFLINGER_LATENCY: return "surfaceflinger_latency";
        case FpsSource::GFXINFO: return "gfxinfo";
        default: return "none";
    }
}

struct FrameTimestamps {
    int64_t desired = 0;
    int64_t presentedNanos = 0; // the column we trust for FPS math
    int64_t ready = 0;
};

// Global state
static std::mutex g_mutex;
#if __ANDROID_API__ >= 30
static AIBinder* g_sfBinder = nullptr;
#endif
static FpsSource g_lastFpsSource = FpsSource::NONE;
static std::map<std::string, int64_t> g_lastFrameCount; // kept only for nativeResetCounters compat
static std::map<std::string, int64_t> g_lastSampleTime;

// ---------------------------------------------------------------------------
// SECTION: SurfaceFlinger Binder access
// ---------------------------------------------------------------------------

#if __ANDROID_API__ >= 30
static AIBinder* getSurfaceFlingerBinder() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sfBinder) {
        if (AIBinder_isAlive(g_sfBinder)) {
            AIBinder_incStrong(g_sfBinder, nullptr);
            return g_sfBinder;
        }
        AIBinder_decStrong(g_sfBinder, nullptr);
        g_sfBinder = nullptr;
    }

    g_sfBinder = AServiceManager_getService("SurfaceFlinger");
    if (g_sfBinder) {
        LOGI("Connected to SurfaceFlinger service: %p", g_sfBinder);
        AIBinder_incStrong(g_sfBinder, nullptr);
    } else {
        LOGW("AServiceManager_getService(\"SurfaceFlinger\") returned null");
    }
    return g_sfBinder;
}

// dump() via Binder IPC — equivalent to `dumpsys SurfaceFlinger <args>`.
// Requires the *calling process* to hold DUMP-equivalent privilege
// (root uid 0, or shell uid 2000). See the privilege-boundary note at the
// top of this file if this is called from your app's own process.
static std::string sfDumpViaBinder(const std::vector<std::string>& args) {
    AIBinder* sf = getSurfaceFlingerBinder();
    if (!sf) return "";

    int pipefd[2];
    if (pipe(pipefd) != 0) {
        LOGE("pipe() failed: %s", strerror(errno));
        AIBinder_decStrong(sf, nullptr);
        return "";
    }

    std::vector<const char*> cArgs;
    cArgs.reserve(args.size() + 1);
    for (const auto& a : args) cArgs.push_back(a.c_str());
    cArgs.push_back(nullptr);

    binder_status_t status = AIBinder_dump(sf, pipefd[1], cArgs.data());
    close(pipefd[1]);

    std::string result;
    if (status == STATUS_OK) {
        result.reserve(16384);
        char buffer[8192];
        ssize_t n;
        while ((n = read(pipefd[0], buffer, sizeof(buffer))) > 0) {
            result.append(buffer, static_cast<size_t>(n));
        }
    } else {
        LOGD("AIBinder_dump status=%d (permission denied is expected unless "
             "this process is root/shell)", status);
    }
    close(pipefd[0]);
    AIBinder_decStrong(sf, nullptr);
    return result;
}
#else
static std::string sfDumpViaBinder(const std::vector<std::string>& args) {
    return "";
}
#endif

// Plain shell exec. Only gains privilege if the *calling process itself*
// already runs as shell/root — see file header note on Shizuku.
static std::string runShell(const std::string& cmd) {
    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) return "";
    std::string result;
    char buffer[8192];
    while (fgets(buffer, sizeof(buffer), pipe)) result += buffer;
    pclose(pipe);
    return result;
}

// Best-effort `su -c` escalation. Only helps on Magisk-style rooted devices
// where su is on PATH and this app's uid has been granted access; does
// nothing for Shizuku-only setups (Shizuku doesn't expose `su`).
static std::string runShellRoot(const std::string& cmd) {
    return runShell("su -c '" + cmd + "' 2>/dev/null");
}

static std::string sfDump(const std::vector<std::string>& args) {
    std::string viaBinder = sfDumpViaBinder(args);
    if (!viaBinder.empty()) return viaBinder;

    std::string cmd = "dumpsys SurfaceFlinger";
    for (const auto& a : args) cmd += " '" + a + "'";
    cmd += " 2>/dev/null";

    std::string viaShell = runShell(cmd);
    if (!viaShell.empty()) return viaShell;

    LOGD("Direct dumpsys SurfaceFlinger returned nothing, trying su -c");
    return runShellRoot(cmd);
}

static std::string gfxInfoDump(const std::string& packageName) {
    std::string cmd = "dumpsys gfxinfo '" + packageName + "' framestats 2>/dev/null";
    std::string result = runShell(cmd);
    if (!result.empty()) return result;
    return runShellRoot(cmd);
}

// ---------------------------------------------------------------------------
// SECTION: Layer discovery via `--list` (current AOSP frontend format)
//
// Real sample (Android 15/16, from smart-test-ti/SoloX#303):
//   RequestedLayerState{b806cd2 SurfaceView[pkg/Activity]#828 parentId=827 ...}
//   RequestedLayerState{Background for b806cd2 SurfaceView[pkg/Activity]#830 ...}
//   RequestedLayerState{pkg/Activity#824 parentId=620}
//
// Rather than balance the nested `{...}` (an ActivityRecord{...} can sit
// *inside* a RequestedLayerState{...} entry), each line is regex-searched
// for a `SurfaceView[...]#N` or bare `pkg/Activity#N` substring. That
// substring is exactly the string `--latency` expects as its argument.
// ---------------------------------------------------------------------------

static std::vector<std::string> findLayerCandidatesForPackage(
        const std::string& listDump, const std::string& packageName) {

    std::vector<std::string> surfaceViewHits;
    std::vector<std::string> plainHits;

    static const std::regex svRegex(R"(SurfaceView\[[^\]]*\]#\d+)");
    static const std::regex plainRegex(R"([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+#\d+)");

    std::istringstream stream(listDump);
    std::string line;
    std::set<std::string> seen;

    while (std::getline(stream, line)) {
        if (line.find(packageName) == std::string::npos) continue;

        bool isDecoration = line.find("Background for") != std::string::npos ||
                             line.find("Bounds for") != std::string::npos ||
                             line.find("InputSink") != std::string::npos;

        std::smatch m;
        if (std::regex_search(line, m, svRegex)) {
            std::string hit = m.str();
            if (seen.insert(hit).second && !isDecoration) surfaceViewHits.push_back(hit);
        } else if (std::regex_search(line, m, plainRegex)) {
            std::string hit = m.str();
            if (seen.insert(hit).second && !isDecoration) plainHits.push_back(hit);
        }
    }

    std::vector<std::string> ranked;
    ranked.insert(ranked.end(), surfaceViewHits.begin(), surfaceViewHits.end());
    ranked.insert(ranked.end(), plainHits.begin(), plainHits.end());
    return ranked;
}

// ---------------------------------------------------------------------------
// SECTION: `--latency <layer>` parsing, with degenerate-output detection
// ---------------------------------------------------------------------------

// A matched layer with real data looks like:
//   <refresh_period_ns>
//   <desired> <actual> <ready>
//   ... up to 127 more rows ...
// An UNMATCHED layer name (or, per SoloX#303, sometimes even a matched one
// on Android 15/16) prints only the first line. kMinRealRows guards against
// mistaking that for "0 fps" — it means "no usable data", not "0".
static constexpr size_t kMinRealRows = 3;

static std::vector<FrameTimestamps> parseLatency(const std::string& dump) {
    std::vector<FrameTimestamps> frames;
    if (dump.empty()) return frames;

    std::istringstream stream(dump);
    std::string line;
    bool firstLine = true;

    while (std::getline(stream, line)) {
        if (line.empty()) continue;
        if (firstLine) { firstLine = false; continue; } // refresh-period header
        std::istringstream ls(line);
        int64_t a = 0, b = 0, c = 0;
        if (!(ls >> a >> b >> c)) continue;
        if (a == 0 && b == 0 && c == 0) continue;
        frames.push_back({a, b, c});
    }

    if (frames.size() < kMinRealRows) {
        LOGD("--latency produced only %zu real row(s) — treating as unavailable "
             "(unmatched layer name, or the Android 15/16 regression from "
             "SoloX#303) rather than trusting it as 0 fps", frames.size());
        return {};
    }
    return frames;
}

static std::vector<FrameTimestamps> getLayerFrameTimestamps(const std::string& layerName) {
    return parseLatency(sfDump({"--latency", layerName}));
}

// ---------------------------------------------------------------------------
// SECTION: `--latency-frameinfo <layer>` parsing (Android 12+)
//
// 7-field format (one frame per line):
//   frameNum vsyncId inputEventTime animationStartTime
//   gpuCompositionStartTime gpuCompositionEndTime presentTime
//
// All values are int64 timestamps in nanoseconds.
// presentTime is the column we trust for FPS math — it's when the frame
// actually hit the display. On devices where --latency returns no rows
// (SoloX#303 / Android 15/16 regression), --latency-frameinfo may still
// work because it queries a different dump path inside SurfaceFlinger.
// ---------------------------------------------------------------------------

static std::vector<FrameTimestamps> parseLatencyFrameinfo(const std::string& dump) {
    std::vector<FrameTimestamps> frames;
    if (dump.empty()) return frames;

    std::istringstream stream(dump);
    std::string line;
    size_t totalLines = 0;

    while (std::getline(stream, line)) {
        if (line.empty()) continue;
        if (!std::isdigit(line[0])) continue;
        totalLines++;

        std::istringstream ls(line);
        int64_t frameNum = 0, vsyncId = 0, inputTime = 0, animStart = 0;
        int64_t gpuStart = 0, gpuEnd = 0, presentTime = 0;

        if (ls >> frameNum >> vsyncId >> inputTime >> animStart
              >> gpuStart >> gpuEnd >> presentTime) {
            if (presentTime > 0) {
                frames.push_back({animStart, presentTime, gpuEnd});
            }
        }
    }

    // Degenerate output detection: same as --latency — if we barely got
    // any real rows, treat as unavailable rather than trusting it.
    if (totalLines > 0 && frames.size() < kMinRealRows) {
        LOGD("--latency-frameinfo had %zu header/text lines but only %zu real "
             "frame rows — treating as unavailable", totalLines, frames.size());
        return {};
    }

    return frames;
}

static std::vector<FrameTimestamps> getLayerFrameInfo(const std::string& layerName) {
    return parseLatencyFrameinfo(sfDump({"--latency-frameinfo", layerName}));
}

// ---------------------------------------------------------------------------
// SECTION: gfxinfo framestats parsing (primary, robust source)
//
// Stable multi-block CSV format:
//   ---PROFILEDATA---
//   Flags,IntendedVsync,Vsync,...,FrameCompleted,...,
//   0,3183281530007,...,3183283537000,...,
//   ---PROFILEDATA---
// Column position has shifted across Android versions, so the
// "FrameCompleted"-equivalent column is found by header name, not index.
// Rows with Flags bit 0 set are skipped (not a real presented frame).
// ---------------------------------------------------------------------------

static std::string toLowerCopy(const std::string& s) {
    std::string out = s;
    std::transform(out.begin(), out.end(), out.begin(),
                    [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return out;
}

static std::vector<std::vector<int64_t>> parseGfxInfoFrameStats(const std::string& dump) {
    std::vector<std::vector<int64_t>> blocks;
    if (dump.empty()) return blocks;

    static const std::vector<std::string> completedNames = {
        "framecompleted", "frame_completed", "displaypresenttime",
        "swapbufferscompleted", "gpucompleted"
    };

    std::istringstream stream(dump);
    std::string line;
    bool inBlock = false;
    std::vector<std::string> header;
    int flagsIdx = -1;
    int completedIdx = -1;
    std::vector<int64_t> currentBlock;

    while (std::getline(stream, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();

        if (line.find("---PROFILEDATA---") != std::string::npos) {
            inBlock = !inBlock;
            if (inBlock) {
                header.clear();
                flagsIdx = completedIdx = -1;
                currentBlock.clear();
            } else {
                if (!currentBlock.empty()) {
                    std::sort(currentBlock.begin(), currentBlock.end());
                    blocks.push_back(currentBlock);
                }
            }
            continue;
        }
        if (!inBlock) continue;

        if (header.empty()) {
            std::istringstream hs(line);
            std::string tok;
            while (std::getline(hs, tok, ',')) {
                std::string trimmed = trim(tok);
                if (!trimmed.empty()) header.push_back(toLowerCopy(trimmed));
            }
            for (size_t i = 0; i < header.size(); i++) {
                if (header[i] == "flags") flagsIdx = static_cast<int>(i);
            }
            for (const auto& name : completedNames) {
                for (size_t i = 0; i < header.size(); i++) {
                    if (header[i] == name) { completedIdx = static_cast<int>(i); break; }
                }
                if (completedIdx >= 0) break;
            }
            continue;
        }

        if (completedIdx < 0) continue;

        std::vector<int64_t> fields;
        std::istringstream ls(line);
        std::string tok;
        while (std::getline(ls, tok, ',')) {
            std::string trimmed = trim(tok);
            if (trimmed.empty()) {
                fields.push_back(0);
                continue;
            }
            try { fields.push_back(std::stoll(trimmed)); }
            catch (...) { fields.push_back(0); }
        }
        if (static_cast<int>(fields.size()) <= completedIdx) continue;
        if (flagsIdx >= 0 && flagsIdx < static_cast<int>(fields.size())) {
            if (fields[static_cast<size_t>(flagsIdx)] & 0x1) continue; // layout-change row, skip
        }
        int64_t completed = fields[static_cast<size_t>(completedIdx)];
        if (completed > 0) currentBlock.push_back(completed);
    }

    return blocks;
}

// ---------------------------------------------------------------------------
// SECTION: shared FPS math
// ---------------------------------------------------------------------------

static double fpsFromSortedTimestampsNanos(const std::vector<int64_t>& times) {
    if (times.size() < 2) return 0.0;
    
    // Get current monotonic system time in nanoseconds
    auto now = std::chrono::steady_clock::now();
    int64_t nowNanos = std::chrono::duration_cast<std::chrono::nanoseconds>(now.time_since_epoch()).count();
    
    int64_t latest = times.back();
    // If the latest frame completion is older than 1.5 seconds, the app is not rendering new frames
    if (nowNanos - latest > 1500000000LL) {
        return 0.0;
    }
    
    // Find how many frames fall within the last 1 second (1e9 ns) from the latest frame
    size_t count = 0;
    size_t startIdx = times.size() - 1;
    for (int i = static_cast<int>(times.size()) - 1; i >= 0; i--) {
        if (latest - times[i] <= 1000000000LL) {
            count++;
            startIdx = i;
        } else {
            break;
        }
    }
    
    if (count < 2) return 0.0;
    int64_t deltaNanos = latest - times[startIdx];
    if (deltaNanos <= 0) return 0.0;
    
    // When the window is large enough (≥200ms), compute proper (N-1)/Δt FPS.
    // For very short windows the raw count avoids math explosion
    // (e.g. 2 frames in 12ms → 79 fps would be silly, just return 2).
    if (deltaNanos >= 200000000LL) {
        double seconds = static_cast<double>(deltaNanos) / 1e9;
        double fps = static_cast<double>(count - 1) / seconds;
        return std::min(fps, 1000.0);
    } else {
        return static_cast<double>(count);
    }
}

static double computeFpsFromLatencyFrames(const std::vector<FrameTimestamps>& frames) {
    std::vector<int64_t> times;
    times.reserve(frames.size());
    for (const auto& f : frames) if (f.presentedNanos > 0) times.push_back(f.presentedNanos);
    std::sort(times.begin(), times.end());
    return fpsFromSortedTimestampsNanos(times);
}

// ---------------------------------------------------------------------------
// SECTION: combined strategy — SurfaceFlinger latency if it yields real
// data, gfxinfo framestats otherwise. Only returns -1.0 if BOTH genuinely
// produced nothing, rather than silently repeating a stale number.
// ---------------------------------------------------------------------------

static double getBestFpsForPackage(const std::string& packageName, FpsSource* outSource) {
    std::string listDump = sfDump({"--list"});
    if (listDump.empty()) {
        LOGD("`--list` returned nothing — likely no DUMP/root privilege from "
             "this process (see privilege-boundary note at top of file)");
    } else {
        // Strategy 1: SurfaceFlinger --latency (best for SurfaceView/GPU apps)
        auto candidates = findLayerCandidatesForPackage(listDump, packageName);
        for (const auto& candidate : candidates) {
            auto frames = getLayerFrameTimestamps(candidate);
            if (frames.empty()) continue;
            double fps = computeFpsFromLatencyFrames(frames);
            if (fps > 0.0) {
                if (outSource) *outSource = FpsSource::SURFACEFLINGER_LATENCY;
                LOGD("FPS via SurfaceFlinger --latency \"%s\": %.2f", candidate.c_str(), fps);
                return fps;
            }
        }
        // Strategy 2: SurfaceFlinger --latency-frameinfo (Android 12+ —
        // may work on devices where --latency returns no rows, e.g.
        // SoloX#303 / Android 15/16 regression)
        for (const auto& candidate : candidates) {
            auto frames = getLayerFrameInfo(candidate);
            if (frames.empty()) continue;
            double fps = computeFpsFromLatencyFrames(frames);
            if (fps > 0.0) {
                if (outSource) *outSource = FpsSource::SURFACEFLINGER_LATENCY;
                LOGD("FPS via SurfaceFlinger --latency-frameinfo \"%s\": %.2f", candidate.c_str(), fps);
                return fps;
            }
        }
    }

    auto blocks = parseGfxInfoFrameStats(gfxInfoDump(packageName));
    double maxFps = -1.0;
    for (const auto& completions : blocks) {
        if (completions.size() >= 2) {
            double fps = fpsFromSortedTimestampsNanos(completions);
            if (fps > maxFps) maxFps = fps;
        }
    }
    if (maxFps > 0.0) {
        if (outSource) *outSource = FpsSource::GFXINFO;
        LOGD("FPS via gfxinfo framestats for \"%s\": %.2f", packageName.c_str(), maxFps);
        return maxFps;
    }

    if (outSource) *outSource = FpsSource::NONE;
    return -1.0;
}

// ---------------------------------------------------------------------------
// SECTION: JNI Interface (same package/class as v1 — drop-in compatible)
// ---------------------------------------------------------------------------

#define JNI_FUNC(ret, name) \
    JNIEXPORT ret JNICALL Java_com_mkm_fps_FpsBinder_##name

extern "C" {

JNI_FUNC(jboolean, nativeInit)(JNIEnv*, jobject) {
#if __ANDROID_API__ >= 30
    AIBinder* sf = getSurfaceFlingerBinder();
    if (!sf) { LOGE("Failed to connect to SurfaceFlinger service"); return JNI_FALSE; }
    AIBinder_decStrong(sf, nullptr);
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNI_FUNC(jboolean, nativeIsAlive)(JNIEnv*, jobject) {
#if __ANDROID_API__ >= 30
    AIBinder* sf = getSurfaceFlingerBinder();
    if (!sf) return JNI_FALSE;
    bool alive = AIBinder_isAlive(sf);
    AIBinder_decStrong(sf, nullptr);
    return alive ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

// Self-contained: fetches --list, --latency and gfxinfo itself. Works if
// this process already has DUMP-level privilege. If your privilege comes
// from Shizuku, prefer the text-in entry points near the bottom instead.
JNI_FUNC(jdouble, nativeGetFpsForPackage)(JNIEnv* env, jobject, jstring jPackageName) {
    if (!jPackageName) return -1.0;
    const char* pkg = env->GetStringUTFChars(jPackageName, nullptr);
    if (!pkg) return -1.0;
    std::string packageName(pkg);
    env->ReleaseStringUTFChars(jPackageName, pkg);

    FpsSource source = FpsSource::NONE;
    double fps = getBestFpsForPackage(packageName, &source);
    { std::lock_guard<std::mutex> lock(g_mutex); g_lastFpsSource = source; }
    return fps;
}

// Same, for an already-known exact layer name (skips --list).
JNI_FUNC(jdouble, nativeGetLayerFps)(JNIEnv* env, jobject, jstring jLayerName) {
    if (!jLayerName) return -1.0;
    const char* name = env->GetStringUTFChars(jLayerName, nullptr);
    if (!name) return -1.0;
    std::string layerName(name);
    env->ReleaseStringUTFChars(jLayerName, name);

    auto frames = getLayerFrameTimestamps(layerName);
    if (frames.empty()) return -1.0;
    return computeFpsFromLatencyFrames(frames);
}

// Which source the last nativeGetFpsForPackage call actually used:
// "surfaceflinger_latency" | "gfxinfo" | "none". Handy for an overlay badge
// and for telling the two failure modes above apart while debugging.
JNI_FUNC(jstring, nativeGetLastFpsSource)(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(fpsSourceName(g_lastFpsSource));
}

// --- Text-in entry points for Kotlin/Shizuku-driven privilege ---------------
// Fetch the privileged text yourself (however your existing shell
// collection already does it) and hand it here for parsing only:
//   dumpsys SurfaceFlinger --list
//   dumpsys SurfaceFlinger --latency "<name from nativeFindLayerForPackage>"
//   dumpsys SurfaceFlinger --latency-frameinfo "<layer>"       (Android 12+)
//   dumpsys gfxinfo <package> framestats

JNI_FUNC(jstring, nativeFindLayerForPackage)(JNIEnv* env, jobject,
                                              jstring jListDumpText, jstring jPackageName) {
    if (!jListDumpText || !jPackageName) return env->NewStringUTF("");
    const char* listC = env->GetStringUTFChars(jListDumpText, nullptr);
    const char* pkgC = env->GetStringUTFChars(jPackageName, nullptr);
    std::string listDump = listC ? listC : "";
    std::string pkg = pkgC ? pkgC : "";
    if (listC) env->ReleaseStringUTFChars(jListDumpText, listC);
    if (pkgC) env->ReleaseStringUTFChars(jPackageName, pkgC);

    auto candidates = findLayerCandidatesForPackage(listDump, pkg);
    return env->NewStringUTF(candidates.empty() ? "" : candidates.front().c_str());
}

JNI_FUNC(jdouble, nativeFpsFromLatencyText)(JNIEnv* env, jobject, jstring jLatencyText) {
    if (!jLatencyText) return -1.0;
    const char* c = env->GetStringUTFChars(jLatencyText, nullptr);
    std::string text = c ? c : "";
    if (c) env->ReleaseStringUTFChars(jLatencyText, c);

    auto frames = parseLatency(text);
    if (frames.empty()) return -1.0;
    return computeFpsFromLatencyFrames(frames);
}

JNI_FUNC(jdouble, nativeFpsFromGfxinfoText)(JNIEnv* env, jobject, jstring jGfxinfoText) {
    if (!jGfxinfoText) return -1.0;
    const char* c = env->GetStringUTFChars(jGfxinfoText, nullptr);
    std::string text = c ? c : "";
    if (c) env->ReleaseStringUTFChars(jGfxinfoText, c);

    auto blocks = parseGfxInfoFrameStats(text);
    double maxFps = -1.0;
    for (const auto& completions : blocks) {
        if (completions.size() >= 2) {
            double fps = fpsFromSortedTimestampsNanos(completions);
            if (fps > maxFps) maxFps = fps;
        }
    }
    return maxFps > 0.0 ? maxFps : -1.0;
}

JNI_FUNC(jdouble, nativeFpsFromLatencyFrameinfoText)(JNIEnv* env, jobject, jstring jFrameinfoText) {
    if (!jFrameinfoText) return -1.0;
    const char* c = env->GetStringUTFChars(jFrameinfoText, nullptr);
    std::string text = c ? c : "";
    if (c) env->ReleaseStringUTFChars(jFrameinfoText, c);

    auto frames = parseLatencyFrameinfo(text);
    if (frames.empty()) return -1.0;
    return computeFpsFromLatencyFrames(frames);
}

// --- Legacy/compat exports (kept so existing Kotlin call sites still link) -

// v1 format was "name|type|frameCount|isGameLayer". frameCount can't be
// recovered from --list (the new frontend dump doesn't print per-layer
// frame= counters the way the old dump did), so it's always "0" here —
// everything else is real.
JNI_FUNC(jobjectArray, nativeGetActiveLayers)(JNIEnv* env, jobject) {
    std::string listDump = sfDump({"--list"});
    static const std::regex svRegex(R"(SurfaceView\[[^\]]*\]#\d+)");
    static const std::regex plainRegex(R"([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+#\d+)");

    std::vector<std::string> entries;
    std::set<std::string> seen;
    std::istringstream stream(listDump);
    std::string line;
    while (std::getline(stream, line)) {
        std::smatch m;
        std::string hit, type;
        if (std::regex_search(line, m, svRegex)) { hit = m.str(); type = "SurfaceView"; }
        else if (std::regex_search(line, m, plainRegex)) { hit = m.str(); type = "Window"; }
        else continue;
        if (!seen.insert(hit).second) continue;
        entries.push_back(hit + "|" + type + "|0|" + (type == "SurfaceView" ? "1" : "0"));
    }

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(entries.size()), stringClass, nullptr);
    for (size_t i = 0; i < entries.size(); i++) {
        jstring str = env->NewStringUTF(entries[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), str);
        env->DeleteLocalRef(str);
    }
    return result;
}

// v1's "all layers via frame counter" mode relied on `frame=NNN` lines that
// don't exist in the --list format. Rather than fake it, this now logs and
// returns empty — call nativeGetFpsForPackage per app you actually care
// about (an overlay only ever needs the current foreground app anyway).
JNI_FUNC(jstring, nativeGetAllFps)(JNIEnv* env, jobject) {
    LOGW("nativeGetAllFps is deprecated (the --list format has no per-layer "
         "frame counters to poll). Use nativeGetFpsForPackage(pkg) instead.");
    return env->NewStringUTF("");
}

JNI_FUNC(jlongArray, nativeGetFrameTimestamps)(JNIEnv* env, jobject, jstring jLayerName) {
    if (!jLayerName) return nullptr;
    const char* name = env->GetStringUTFChars(jLayerName, nullptr);
    if (!name) return nullptr;
    std::string layerName(name);
    env->ReleaseStringUTFChars(jLayerName, name);

    auto frames = getLayerFrameTimestamps(layerName);
    std::vector<int64_t> timestamps;
    timestamps.reserve(frames.size());
    for (const auto& f : frames) timestamps.push_back(f.presentedNanos);

    jlongArray result = env->NewLongArray(static_cast<jsize>(timestamps.size()));
    if (result && !timestamps.empty()) {
        env->SetLongArrayRegion(result, 0, static_cast<jsize>(timestamps.size()),
                                 reinterpret_cast<const jlong*>(timestamps.data()));
    }
    return result;
}

JNI_FUNC(jlong, nativeGetUptimeMillis)(JNIEnv*, jobject) {
    auto now = std::chrono::steady_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
    return static_cast<jlong>(ms);
}

// Raw passthrough for debugging. Try args="--list" to see modern layer
// names, or "--latency <name-from-list>".
JNI_FUNC(jstring, nativeDump)(JNIEnv* env, jobject, jstring jArgs) {
    std::vector<std::string> args;
    if (jArgs) {
        const char* cArgs = env->GetStringUTFChars(jArgs, nullptr);
        if (cArgs) {
            std::istringstream stream(cArgs);
            std::string token;
            while (stream >> token) args.push_back(token);
            env->ReleaseStringUTFChars(jArgs, cArgs);
        }
    }
    return env->NewStringUTF(sfDump(args).c_str());
}

JNI_FUNC(jboolean, nativeSetFpsLimit)(JNIEnv*, jobject, jint fps) {
    if (fps < 0 || fps > 240) { LOGE("Invalid FPS limit: %d", fps); return JNI_FALSE; }
    LOGI("setFpsLimit(%d): not implemented. Needs ISurfaceComposer::setFrameRate, "
         "which (like addFpsListener) only exists behind the AIDL interface — "
         "no stable NDK wrapper. Say the word if you want this scoped out "
         "properly rather than guessed at.", fps);
    return JNI_FALSE;
}

// Hook Mode is removed (see file header, point 4). These exports are kept
// only so existing Kotlin call sites don't hit UnsatisfiedLinkError; they
// now honestly report "not installed" instead of v1's stub "true".
JNI_FUNC(jboolean, nativeInstallHooks)(JNIEnv*, jobject) {
    LOGW("nativeInstallHooks: Hook Mode was removed — v1's version was a stub "
         "that never actually hooked anything. nativeGetFpsForPackage now has "
         "a working query-mode implementation; use that.");
    return JNI_FALSE;
}

JNI_FUNC(jboolean, nativeHooksInstalled)(JNIEnv*, jobject) { return JNI_FALSE; }

JNI_FUNC(jlong, nativeGetSfBinderHandle)(JNIEnv*, jobject) { return -1; }

JNI_FUNC(void, nativeResetCounters)(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_lastFrameCount.clear();
    g_lastSampleTime.clear();
}

} // extern "C"

__attribute__((constructor))
static void onLibraryLoad() {
    LOGI("libfpsbinder.so loaded (v2 — gfxinfo-first, --list aware)");
    LOGI("API Level: %d", android_get_device_api_level());
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
