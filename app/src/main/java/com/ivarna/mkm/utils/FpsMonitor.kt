package com.ivarna.mkm.utils

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver

object FpsMonitor {

    private const val TAG = "FpsMonitor"

    data class FpsResult(val fps: Float, val jankCount: Int)

    // ── OnDrawListener — true draw rate ──────────────────────
    private var drawFrameCount = 0L
    private var lastDrawCount = 0L
    private var lastDrawTimeMs = 0L
    private var drawRunning = false
    private var drawListener: ViewTreeObserver.OnDrawListener? = null

    // ── Choreographer fallback ───────────────────────────────
    @Volatile private var choreoFps = 0f
    @Volatile private var displayRefreshRate = 60f
    private var lastFrameTimeNs = 0L
    @Volatile private var choreoRunning = false
    private var frameCallback: Choreographer.FrameCallback? = null

    fun initDrawFps(view: View) {
        if (drawRunning) return
        try {
            val listener = ViewTreeObserver.OnDrawListener { drawFrameCount++ }
            view.viewTreeObserver.addOnDrawListener(listener)
            drawListener = listener
            drawRunning = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init OnDrawListener", e)
        }
    }

    private fun readDrawFps(): Float {
        if (!drawRunning) return -1f
        val now = SystemClock.uptimeMillis()
        val count = drawFrameCount
        if (lastDrawTimeMs == 0L) { lastDrawTimeMs = now; lastDrawCount = count; return -1f }
        val dt = now - lastDrawTimeMs
        if (dt < 200) return -1f
        val delta = count - lastDrawCount
        lastDrawCount = count
        lastDrawTimeMs = now
        if (delta <= 0 || dt <= 0) return -1f
        return delta * 1000f / dt
    }

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
        frameCallback = null; choreoRunning = false; choreoFps = 0f; lastFrameTimeNs = 0L
    }

    fun readFps(): FpsResult {
        val df = readDrawFps()
        if (df > 0f) {
            val fps = if (df < 100f) df / 2.1f else df
            return FpsResult(fps, 0)
        }
        return FpsResult(choreoFps, 0)
    }

    fun getDisplayRefreshRate(): Float = displayRefreshRate
}
