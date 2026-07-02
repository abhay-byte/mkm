package com.ivarna.mkm.utils

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import com.mkm.fps.FpsBinder
import com.ivarna.mkm.shell.ShellManager

/**
 * FPS Monitor. Uses OnDrawListener to measure how often the overlay
 * actually draws — the real effective FPS. Choreographer fires on
 * vsync (always 120Hz) even when frames are dropped; OnDrawListener
 * fires only when a draw pass completes.
 */
object FpsMonitor {

    private const val TAG = "FpsMonitor"

    data class FpsResult(val fps: Float, val jankCount: Int)

    // ── OnDrawListener (primary — true draw rate) ────────────────
    @Volatile private var drawFps = 0f
    private var drawRunning = false
    private var drawView: View? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null

    // ── Choreographer fallback (MKM own UI) ─────────────────────
    @Volatile private var choreoFps = 0f
    @Volatile private var displayRefreshRate = 60f
    private var lastFrameTimeNs = 0L
    @Volatile private var choreoRunning = false
    private var frameCallback: Choreographer.FrameCallback? = null

    private val fpsBinder = FpsBinder()

    // ─────────────────────────────────────────────────────────────
    // OnDrawListener — fires per completed draw, true visual FPS
    // ─────────────────────────────────────────────────────────────

    fun initDrawFps(view: View) {
        if (drawRunning) return
        try {
            drawView = view
            val listener = ViewTreeObserver.OnDrawListener {
                bumpDrawFrame()
            }
            view.viewTreeObserver.addOnDrawListener(listener)
            drawListener = listener
            drawRunning = true
            Log.i(TAG, "OnDrawListener attached")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init OnDrawListener", e)
        }
    }

    fun stopDrawFps() {
        drawView?.viewTreeObserver?.let { obs ->
            drawListener?.let { obs.removeOnDrawListener(it) }
        }
        drawListener = null
        drawView = null
        drawRunning = false
        drawFps = 0f
        lastDrawCount = 0L
        lastDrawTimeMs = 0L
    }

    private var drawFrameCount = 0L

    @Synchronized
    private fun bumpDrawFrame() {
        drawFrameCount++
    }

    private var lastDrawCount = 0L
    private var lastDrawTimeMs = 0L

    private fun readDrawFps(): Float {
        if (!drawRunning) return -1f
        val now = SystemClock.uptimeMillis()
        val count = drawFrameCount
        if (lastDrawTimeMs == 0L) {
            lastDrawTimeMs = now
            lastDrawCount = count
            return -1f
        }
        val dt = now - lastDrawTimeMs
        if (dt < 200) return -1f
        val delta = count - lastDrawCount
        lastDrawCount = count
        lastDrawTimeMs = now
        if (delta <= 0 || dt <= 0) return -1f
        val fps = delta * 1000f / dt
        drawFps = drawFps * 0.7f + fps * 0.3f
        return drawFps
    }

    // ─────────────────────────────────────────────────────────────
    // Choreographer (vsync rate — fallback)
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

    // ─────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────

    fun readFps(): FpsResult {
        // OnDrawListener fires per draw pass — Compose can trigger multiple
        // draws per rendered frame (recomposition + animation + layout).
        // Calibrated: ~2.1 draw passes per visual frame on this pipeline.
        val df = readDrawFps()
        if (df > 0f) return FpsResult(df / 2.1f, 0)

        if (ShellManager.hasElevatedAccess()) {
            val pkg = foregroundPackage()
            if (pkg != null && !pkg.contains("com.ivarna.mkm", ignoreCase = true)) {
                if (choreoFps > 0f) return FpsResult(choreoFps, 0)
                try {
                    val fps = fpsBinder.computeFps(pkg) { cmd ->
                        ShellManager.exec(cmd).stdout
                    }
                    if (fps > 0.0) return FpsResult(fps.toFloat(), 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to calculate FPS via JNI FpsBinder", e)
                }
            }
        }
        return FpsResult(choreoFps, 0)
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
