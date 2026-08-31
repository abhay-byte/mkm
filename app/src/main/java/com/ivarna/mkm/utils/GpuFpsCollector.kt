package com.ivarna.mkm.utils

import android.os.SystemClock
import android.util.Log
import com.ivarna.mkm.data.model.FpsSample
import com.ivarna.mkm.data.model.FpsSource
import com.ivarna.mkm.shell.ShellManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Root-only GPU FPS collector via ftrace kernel tracing with non-root [FpsMonitor] fallback.
 *
 * Implements:
 * - Snapdragon / Adreno: adreno_cmdbatch_submitted inflight drop counting
 * - MediaTek / Mali: dma_fence/dma_fence_signaled timeline event counting
 * - Fallback: FpsMonitor.readFps()
 */
object GpuFpsCollector {

    private const val TAG = "GpuFpsCollector"
    private const val TRACE_PATH = "/sys/kernel/tracing"
    private const val DMA_EVENT = "dma_fence/dma_fence_signaled"
    private const val SUBMIT_EVENT = "kgsl/adreno_cmdbatch_submitted"

    enum class Platform {
        SNAPDRAGON,
        MEDIATEK,
        UNKNOWN
    }

    private var cachedPlatform: Platform? = null
    private var tracingProbeResult: Boolean? = null
    private var lastPid = 0
    private var cachedCtxIds = ""

    private val SNAPDRAGON_PATTERN = Regex(
        "^(pineapple|kona|lahaina|taro|kalama|msm|sdm|sm[0-9]|cliffs|anorak|pitti)",
        RegexOption.IGNORE_CASE
    )
    private val MEDIATEK_PATTERN = Regex(
        "^(mt[0-9]|mt6[0-9]*|mt7[0-9]*|mt8[0-9]*)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Detects device chipset platform from ro.board.platform. Cached for process lifetime.
     */
    suspend fun detectPlatform(): Platform = withContext(Dispatchers.IO) {
        cachedPlatform?.let { return@withContext it }
        val platformStr = ShellManager.exec("getprop ro.board.platform").stdout.trim().lowercase()
        val detected = when {
            SNAPDRAGON_PATTERN.containsMatchIn(platformStr) -> Platform.SNAPDRAGON
            MEDIATEK_PATTERN.containsMatchIn(platformStr) -> Platform.MEDIATEK
            else -> Platform.UNKNOWN
        }
        Log.i(TAG, "Detected platform: $detected (prop: $platformStr)")
        cachedPlatform = detected
        detected
    }

    /**
     * Root-only probe to verify ftrace sysfs write availability.
     * Uses root specifically — never non-root or Shizuku uid=2000.
     * Clears any leftover tracing_on / events from previous force-stops.
     */
    suspend fun probeTracing(): Boolean = withContext(Dispatchers.IO) {
        tracingProbeResult?.let { return@withContext it }

        if (!Shell.getShell().isRoot) {
            Log.i(TAG, "Root not available; ftrace tracing disabled.")
            tracingProbeResult = false
            return@withContext false
        }

        // Clean up any leftover kernel tracer state before probing
        shutdown()

        val probeResult = execRoot("echo 0 > $TRACE_PATH/tracing_on 2>/dev/null && echo ok")
        val isAvailable = probeResult.isSuccess && probeResult.stdout.contains("ok")
        Log.i(TAG, "ftrace probe result: $isAvailable (exit=${probeResult.exitCode})")
        tracingProbeResult = isAvailable
        isAvailable
    }

    /**
     * Collects one FPS sample over the specified sampling window (default 2s).
     * If [fallbackOnly] is true, bypasses ftrace and uses [FpsMonitor] fallback directly.
     */
    suspend fun sample(windowSec: Float = 2f, fallbackOnly: Boolean = false): FpsSample? = withContext(Dispatchers.IO) {
        val appInfo = FpsMonitor.foregroundApp() ?: Pair("Unknown", 0)
        val pkg = appInfo.first
        val pid = appInfo.second

        if (fallbackOnly) {
            return@withContext sampleFallback(pkg, pid, windowSec)
        }

        val platform = detectPlatform()
        val hasTracing = probeTracing()

        if (!hasTracing || platform == Platform.UNKNOWN || pid <= 0) {
            return@withContext sampleFallback(pkg, pid, windowSec)
        }

        try {
            when (platform) {
                Platform.SNAPDRAGON -> sampleSnapdragon(pkg, pid, windowSec)
                Platform.MEDIATEK -> sampleMediaTek(pkg, pid, windowSec)
                Platform.UNKNOWN -> sampleFallback(pkg, pid, windowSec)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sampling ftrace on $platform, falling back", e)
            sampleFallback(pkg, pid, windowSec)
        }
    }

    private suspend fun sampleSnapdragon(pkg: String, pid: Int, windowSec: Float): FpsSample {
        val pfx = pid.toString().take(3)
        if (pid != lastPid || cachedCtxIds.isBlank()) {
            cachedCtxIds = discoverSnapdragonCtxs(pfx)
            lastPid = pid
        }

        if (cachedCtxIds.isBlank()) {
            cachedCtxIds = discoverSnapdragonCtxs(pfx)
            if (cachedCtxIds.isBlank()) {
                return sampleFallback(pkg, pid, windowSec)
            }
        }

        val ctxGrep = cachedCtxIds.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString("|") { "ctx=$it[^0-9]" }

        if (ctxGrep.isBlank()) return sampleFallback(pkg, pid, windowSec)

        val t0: Long
        val tEnd: Long
        val output: String

        try {
            // Setup ftrace buffer & submit event
            execRoot(
                "echo 0 > $TRACE_PATH/tracing_on\n" +
                "echo 0 > $TRACE_PATH/events/$DMA_EVENT/enable 2>/dev/null\n" +
                "echo > $TRACE_PATH/trace\n" +
                "echo 1 > $TRACE_PATH/events/$SUBMIT_EVENT/enable 2>/dev/null\n" +
                "echo 1 > $TRACE_PATH/tracing_on"
            )

            t0 = SystemClock.uptimeMillis()
            delay((windowSec * 1000).toLong().coerceAtLeast(500L))
            tEnd = SystemClock.uptimeMillis()

            output = execRoot(
                "echo 0 > $TRACE_PATH/tracing_on\n" +
                "echo 0 > $TRACE_PATH/events/$SUBMIT_EVENT/enable 2>/dev/null\n" +
                "grep -E '($ctxGrep)' $TRACE_PATH/trace 2>/dev/null | " +
                "awk '{for(i=1;i<=NF;i++) if(\$i ~ /^inflight=/) {sub(/inflight=/,\"\",\$i); print \$i}}' | " +
                "awk 'NR==1 { prev=\$1; f=1; next } { if (\$1 < prev) f++; prev=\$1 } END { print NR, f+0 }'"
            ).stdout.trim()
        } finally {
            // Belt-and-suspenders: ensure tracing_on=0 and events disabled even if cancelled
            execRoot(
                "echo 0 > $TRACE_PATH/tracing_on 2>/dev/null\n" +
                "echo 0 > $TRACE_PATH/events/$SUBMIT_EVENT/enable 2>/dev/null"
            )
        }

        val parts = output.split(Regex("\\s+"))
        val events = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val rawFrames = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val actualSpanSec = ((tEnd - t0) / 1000f).coerceAtLeast(0.1f)

        val nowMs = System.currentTimeMillis()
        return if (rawFrames < 2 || events < 3) {
            FpsSample(
                tMs = nowMs,
                fps = 0f,
                frameMs = 0f,
                pkg = pkg,
                pid = pid,
                source = FpsSource.ADRENO_INFLIGHT,
                events = events,
                idle = true
            )
        } else {
            val fps = (rawFrames / actualSpanSec).coerceAtLeast(0f)
            val frameMs = if (fps > 0f) 1000f / fps else 0f
            FpsSample(
                tMs = nowMs,
                fps = fps,
                frameMs = frameMs,
                pkg = pkg,
                pid = pid,
                source = FpsSource.ADRENO_INFLIGHT,
                events = events,
                idle = false
            )
        }
    }

    private suspend fun discoverSnapdragonCtxs(pfx: String): String {
        val result = execRoot(
            "echo 0 > $TRACE_PATH/tracing_on\n" +
            "echo > $TRACE_PATH/trace\n" +
            "echo 1 > $TRACE_PATH/events/$DMA_EVENT/enable 2>/dev/null\n" +
            "echo 1 > $TRACE_PATH/tracing_on\n" +
            "sleep 0.5\n" +
            "echo 0 > $TRACE_PATH/tracing_on\n" +
            "echo 0 > $TRACE_PATH/events/$DMA_EVENT/enable 2>/dev/null\n" +
            "grep 'driver=kgsl-timeline.*($pfx' $TRACE_PATH/trace 2>/dev/null | " +
            "grep -o 'kgsl-3d0_[0-9]*' | sed 's/kgsl-3d0_//' | sort -nu | tr '\\n' ' '"
        )
        return result.stdout.trim()
    }

    private suspend fun sampleMediaTek(pkg: String, pid: Int, windowSec: Float): FpsSample {
        val t0: Long
        val tEnd: Long
        val output: String

        try {
            execRoot(
                "echo 0 > $TRACE_PATH/tracing_on\n" +
                "echo > $TRACE_PATH/trace\n" +
                "echo 1 > $TRACE_PATH/events/$DMA_EVENT/enable 2>/dev/null\n" +
                "echo 1 > $TRACE_PATH/tracing_on"
            )

            t0 = SystemClock.uptimeMillis()
            delay((windowSec * 1000).toLong().coerceAtLeast(500L))
            tEnd = SystemClock.uptimeMillis()

            output = execRoot(
                "echo 0 > $TRACE_PATH/tracing_on\n" +
                "echo 0 > $TRACE_PATH/events/$DMA_EVENT/enable 2>/dev/null\n" +
                "awk '/dma_fence_signaled.*driver=mali.*timeline=0-$pid" + "_/ { c++ } END { print c+0 }' $TRACE_PATH/trace 2>/dev/null"
            ).stdout.trim()
        } finally {
            // Belt-and-suspenders: ensure tracing_on=0 and events disabled even if cancelled
            execRoot(
                "echo 0 > $TRACE_PATH/tracing_on 2>/dev/null\n" +
                "echo 0 > $TRACE_PATH/events/$DMA_EVENT/enable 2>/dev/null"
            )
        }

        val events = output.toIntOrNull() ?: 0
        val actualSpanSec = ((tEnd - t0) / 1000f).coerceAtLeast(0.1f)
        val nowMs = System.currentTimeMillis()

        return if (events < 2) {
            FpsSample(
                tMs = nowMs,
                fps = 0f,
                frameMs = 0f,
                pkg = pkg,
                pid = pid,
                source = FpsSource.MALI_DMA_FENCE,
                events = events,
                idle = true
            )
        } else {
            val fps = (events / actualSpanSec).coerceAtLeast(0f)
            val frameMs = if (fps > 0f) 1000f / fps else 0f
            FpsSample(
                tMs = nowMs,
                fps = fps,
                frameMs = frameMs,
                pkg = pkg,
                pid = pid,
                source = FpsSource.MALI_DMA_FENCE,
                events = events,
                idle = false
            )
        }
    }

    private suspend fun sampleFallback(pkg: String, pid: Int, windowSec: Float = 2f): FpsSample {
        // Guarantee proper sampling window cadence for fallback path
        delay((windowSec * 1000).toLong().coerceAtLeast(500L))
        val fpsResult = FpsMonitor.readFps()
        val fps = fpsResult.fps
        val frameMs = if (fps > 0f) 1000f / fps else 0f
        return FpsSample(
            tMs = System.currentTimeMillis(),
            fps = fps,
            frameMs = frameMs,
            pkg = pkg,
            pid = pid,
            source = FpsSource.FPS_MONITOR,
            events = 0,
            idle = fps <= 0f
        )
    }

    /**
     * Stops tracing and disables ftrace events. Idempotent and safe.
     */
    fun shutdown() {
        if (Shell.getShell().isRoot) {
            try {
                Shell.cmd(
                    "echo 0 > $TRACE_PATH/tracing_on 2>/dev/null || true",
                    "echo 0 > $TRACE_PATH/events/$DMA_EVENT/enable 2>/dev/null || true",
                    "echo 0 > $TRACE_PATH/events/$SUBMIT_EVENT/enable 2>/dev/null || true"
                ).exec()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to shutdown ftrace", e)
            }
        }
    }

    private fun execRoot(command: String): ShellManager.CommandResult {
        return try {
            val result = Shell.cmd(command).exec()
            ShellManager.CommandResult(
                result.code,
                result.out.joinToString("\n").trim(),
                result.err.joinToString("\n").trim()
            )
        } catch (e: Exception) {
            ShellManager.CommandResult(-1, "", e.message ?: "Root execution error")
        }
    }
}
