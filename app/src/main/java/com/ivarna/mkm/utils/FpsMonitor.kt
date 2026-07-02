package com.ivarna.mkm.utils

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import com.ivarna.mkm.shell.ShellManager

object FpsMonitor {

    private const val TAG = "FpsMonitor"
    private const val ONE_SECOND_NS = 1_000_000_000L

    data class FpsResult(val fps: Float, val jankCount: Int)

    // Choreographer fallback
    @Volatile private var choreoFps = 0f
    @Volatile private var displayRefreshRate = 60f
    private var lastFrameTimeNs = 0L
    @Volatile private var choreoRunning = false
    private var frameCallback: Choreographer.FrameCallback? = null

    // SurfaceFlinger frame counter (primary)
    private var sfPackage: String? = null
    private var sfLastCount = 0L
    private var sfLastTimeMs = 0L

    // Per-app FPS via dumpsys gfxinfo framestats (fallback)
    private var trackedPackage: String? = null
    private val frameTimestamps = ArrayList<Long>()
    private var lastProcessedTimestamp = 0L
    private var frameCompletedColumn = -1
    private var lastFrameSeenAtMs = 0L

    fun initChoreographer() {
        if (choreoRunning) return
        try {
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (lastFrameTimeNs != 0L) {
                        val deltaMs = (frameTimeNanos - lastFrameTimeNs) / 1_000_000f
                        if (deltaMs > 0f) {
                            if (displayRefreshRate == 60f && deltaMs < 12f)
                                displayRefreshRate = 1000f / deltaMs
                            val instant = 1000f / deltaMs
                            choreoFps = if (choreoFps == 0f) instant
                                        else choreoFps * 0.7f + instant * 0.3f
                        }
                    }
                    lastFrameTimeNs = frameTimeNanos
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            frameCallback = callback
            Choreographer.getInstance().postFrameCallback(callback)
            choreoRunning = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init Choreographer", e)
        }
    }

    fun stopChoreographer() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        choreoRunning = false
        choreoFps = 0f
        lastFrameTimeNs = 0L
    }

    fun readFps(): FpsResult {
        if (ShellManager.hasElevatedAccess()) {
            // Try SurfaceFlinger frame counter first (tracks all frames at SF level)
            val sfResult = readSfFps()
            if (sfResult.fps > 0f) return sfResult

            // Fall back to gfxinfo framestats (tracks HWUI View frames)
            val gfxResult = readGfxFps()
            if (gfxResult.fps >= 0f) return gfxResult
        }
        return FpsResult(choreoFps, 0)
    }

    // ── SurfaceFlinger frame counter ──────────────────────────

    private val sfFrameRegex = Regex("""frame=(\d+)""")

    private fun readSfFps(): FpsResult {
        val pkg = foregroundPackage() ?: return FpsResult(-1f, 0)
        if (pkg.contains("com.ivarna.mkm", ignoreCase = true)) return FpsResult(-1f, 0)

        if (pkg != sfPackage) {
            sfPackage = pkg
            sfLastCount = 0L
            sfLastTimeMs = 0L
            return FpsResult(-1f, 0)
        }

        val result = ShellManager.exec("dumpsys SurfaceFlinger 2>/dev/null")
        if (!result.isSuccess) return FpsResult(-1f, 0)

        // Find the visible surface layer matching this package
        // Format: "visible reason= buffer=XXXX frame=NNN"
        var frameCount = 0L
        val lines = result.stdout.lines()
        var inLayer = false
        for (line in lines) {
            if (line.contains(pkg, ignoreCase = true)) {
                inLayer = true
                continue
            }
            if (inLayer && "visible" in line) {
                val match = sfFrameRegex.find(line)
                if (match != null) {
                    frameCount = match.groupValues[1].toLongOrNull() ?: 0L
                    break
                }
            }
            if (inLayer && (line.isBlank() || !line.startsWith(" "))) {
                inLayer = false
            }
        }

        if (frameCount == 0L) return FpsResult(-1f, 0)

        val nowMs = SystemClock.elapsedRealtime()

        if (sfLastCount == 0L) {
            sfLastCount = frameCount
            sfLastTimeMs = nowMs
            return FpsResult(-1f, 0)
        }

        val delta = frameCount - sfLastCount
        val dt = nowMs - sfLastTimeMs
        sfLastCount = frameCount
        sfLastTimeMs = nowMs

        if (delta <= 0 || dt <= 0) return FpsResult(-1f, 0)

        return FpsResult(delta * 1000f / dt, 0)
    }

    // ── gfxinfo framestats fallback ──────────────────────────

    private fun readGfxFps(): FpsResult {
        val pkg = foregroundPackage() ?: return FpsResult(-1f, 0)
        if (pkg.contains("com.ivarna.mkm", ignoreCase = true)) return FpsResult(-1f, 0)

        if (pkg != trackedPackage) {
            trackedPackage = pkg
            frameTimestamps.clear()
            lastProcessedTimestamp = 0L
            frameCompletedColumn = -1
            ShellManager.exec("dumpsys gfxinfo $pkg reset")
            return FpsResult(-1f, 0)
        }

        val output = ShellManager.exec("dumpsys gfxinfo $pkg framestats").stdout
        if (output.isBlank()) {
            if (lastFrameSeenAtMs > 0 && SystemClock.elapsedRealtime() - lastFrameSeenAtMs > 2000) {
                trackedPackage = null
                frameTimestamps.clear()
                lastProcessedTimestamp = 0L
                frameCompletedColumn = -1
                ShellManager.exec("dumpsys gfxinfo $pkg reset")
            }
            return FpsResult(-1f, 0)
        }

        if (frameCompletedColumn == -1) {
            frameCompletedColumn = parseColumnIndex(output)
            if (frameCompletedColumn == -1) return FpsResult(-1f, 0)
        }

        var maxTimestamp = lastProcessedTimestamp
        var addedFrames = 0

        for (line in output.lines()) {
            val parts = line.split(",")
            if (parts.size <= frameCompletedColumn) continue
            val ts = parts[frameCompletedColumn].trim().toLongOrNull() ?: continue
            if (ts <= lastProcessedTimestamp) continue
            frameTimestamps.add(ts)
            addedFrames++
            if (ts > maxTimestamp) maxTimestamp = ts
        }

        lastProcessedTimestamp = maxTimestamp

        if (addedFrames > 0) {
            lastFrameSeenAtMs = SystemClock.elapsedRealtime()
        }

        val nowNs = System.nanoTime()
        val iterator = frameTimestamps.iterator()
        while (iterator.hasNext()) {
            if (nowNs - iterator.next() > ONE_SECOND_NS) iterator.remove()
        }

        return FpsResult(frameTimestamps.size.toFloat(), 0)
    }

    private fun parseColumnIndex(output: String): Int {
        val header = output.lines().firstOrNull { it.contains("FrameCompleted") } ?: return -1
        return header.split(",").indexOf("FrameCompleted")
    }

    private val pkgRegex = Regex("""([a-zA-Z0-9._-]+)/""")

    private fun foregroundPackage(): String? {
        val result = ShellManager.exec(
            "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
        )
        if (!result.isSuccess || result.stdout.isBlank()) return null
        return pkgRegex.find(result.stdout)?.groupValues?.get(1)
    }

    fun getDisplayRefreshRate(): Float = displayRefreshRate
}
