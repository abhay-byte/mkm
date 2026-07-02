package com.ivarna.mkm.utils

import android.util.Log
import android.view.Choreographer
import com.mkm.fps.FpsBinder
import com.ivarna.mkm.shell.ShellManager

/**
 * FPS Monitor — delegates to the native NDK FpsBinder library for high-performance
 * C++ parsing of system FPS metrics.
 */
object FpsMonitor {

    private const val TAG = "FpsMonitor"

    data class FpsResult(val fps: Float, val jankCount: Int)

    // ── Choreographer fallback ───────────────────────────────────
    @Volatile private var choreoFps = 0f
    @Volatile private var displayRefreshRate = 60f
    private var lastFrameTimeNs = 0L
    @Volatile private var choreoRunning = false
    private var frameCallback: Choreographer.FrameCallback? = null

    // Native JNI bridge instance
    private val fpsBinder = FpsBinder()

    // ─────────────────────────────────────────────────────────────
    // Choreographer
    // ─────────────────────────────────────────────────────────────

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

    //
    // Public entry point
    // ─────────────────────────────────────────────────────────────

    fun readFps(): FpsResult {
        if (ShellManager.hasElevatedAccess()) {
            val pkg = foregroundPackage()
            if (pkg != null && !pkg.contains("com.ivarna.mkm", ignoreCase = true)) {
                try {
                    // Try C++ JNI paths (gfxinfo, SurfaceFlinger latency, etc.)
                    val fps = fpsBinder.computeFps(pkg) { cmd ->
                        ShellManager.exec(cmd).stdout
                    }
                    if (fps > 0.0) {
                        return FpsResult(fps.toFloat(), 0)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to calculate FPS via JNI FpsBinder", e)
                }
                // Fallback: when monitoring external apps, the overlay's own
                // Choreographer fires at the display compositor rate — which
                // IS the GPU app's actual frame rate on this display pipeline.
                if (choreoFps > 0f) {
                    return FpsResult(choreoFps, 0)
                }
            }
        }
        return FpsResult(choreoFps, 0)
    }

    // ─────────────────────────────────────────────────────────────
    // Foreground package detection
    // ─────────────────────────────────────────────────────────────

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
