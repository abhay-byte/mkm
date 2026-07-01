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

    // Per-app FPS via dumpsys gfxinfo framestats
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
            val appResult = readAppFps()
            if (appResult.fps >= 0f) return appResult
        }
        return FpsResult(choreoFps, 0)
    }

    private fun readAppFps(): FpsResult {
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

        return if (frameTimestamps.isEmpty()) FpsResult(-1f, 0)
               else FpsResult(frameTimestamps.size.toFloat(), 0)
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
