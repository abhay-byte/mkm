package com.ivarna.mkm.utils

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.mkm.fps.FpsBinder
import com.ivarna.mkm.shell.ShellManager
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object FpsMonitor {

    private const val TAG = "FpsMonitor"

    data class FpsResult(val fps: Float, val jankCount: Int)

    @Volatile private var overlayFps = 0f
    @Volatile private var displayRefreshRate = 60f
    private var frameTimes = ArrayDeque<Long>()
    private var lastDrawTimeMs = 0L
    private var onPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var listenerView: WeakReference<View>? = null

    private var drawPacer: ScheduledExecutorService? = null
    @Volatile private var drawPacerActive = false
    private var pacerView: WeakReference<View>? = null

    private val fpsBinder = FpsBinder()

    fun initOverlayFps(view: View) {
        if (onPreDrawListener != null) return
        lastDrawTimeMs = 0L
        frameTimes.clear()
        listenerView = WeakReference(view)
        val listener = ViewTreeObserver.OnPreDrawListener {
            val now = SystemClock.uptimeMillis()
            if (lastDrawTimeMs > 0) {
                val deltaMs = now - lastDrawTimeMs
                if (deltaMs in 1..500) {
                    frameTimes.addLast(deltaMs)
                    if (frameTimes.size > 30) frameTimes.removeFirst()
                    if (frameTimes.isNotEmpty()) {
                        val avgMs = frameTimes.sum().toFloat() / frameTimes.size
                        overlayFps = 1000f / avgMs
                        if (displayRefreshRate == 60f && overlayFps > 80f && frameTimes.size >= 5)
                            displayRefreshRate = overlayFps
                    }
                }
            }
            lastDrawTimeMs = now
            true
        }
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onPreDrawListener = listener
    }

    fun stopOverlayFps() {
        val view = listenerView?.get()
        onPreDrawListener?.let { view?.viewTreeObserver?.removeOnPreDrawListener(it) }
        onPreDrawListener = null
        listenerView = null
        frameTimes.clear()
        lastDrawTimeMs = 0L
        overlayFps = 0f
    }

    fun startDrawPacing(view: View) {
        if (drawPacerActive) return
        pacerView = WeakReference(view)
        drawPacer = Executors.newSingleThreadScheduledExecutor()
        drawPacer?.scheduleAtFixedRate({
            pacerView?.get()?.postInvalidate()
        }, 0, 8, TimeUnit.MILLISECONDS)
        drawPacerActive = true
    }

    fun stopDrawPacing() {
        drawPacer?.shutdown()
        drawPacer = null
        drawPacerActive = false
        pacerView = null
    }

    fun ensureDrawPacing(view: View, currentFps: Float) {
        if (currentFps in 1.0f..99.9f) {
            startDrawPacing(view)
        } else if (currentFps >= 100.0f) {
            stopDrawPacing()
        }
    }

    fun readFps(): FpsResult {
        val now = SystemClock.uptimeMillis()
        if (lastDrawTimeMs > 0 && now - lastDrawTimeMs > 1500) {
            overlayFps = 0f
        }

        if (ShellManager.hasElevatedAccess()) {
            val pkg = foregroundPackage()
            if (pkg != null && !pkg.contains("com.ivarna.mkm", ignoreCase = true)) {
                try {
                    val fps = fpsBinder.computeFps(pkg) { cmd ->
                        ShellManager.exec(cmd).stdout
                    }
                    if (fps > 0.0) return FpsResult(fps.toFloat(), 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed FpsBinder", e)
                }
            }
        }
        return FpsResult(overlayFps / 2.1f, 0)
    }

    private val pkgRegex = Regex("""([a-zA-Z0-9._-]+)/""")

    fun foregroundPackage(): String? {
        val result = ShellManager.exec("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        if (!result.isSuccess || result.stdout.isBlank()) return null
        return pkgRegex.find(result.stdout)?.groupValues?.get(1)
    }

    fun foregroundApp(): Pair<String, Int>? {
        var pkg: String? = foregroundPackage()
        if (pkg == null || pkg == "null") {
            val actResult = ShellManager.exec("dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'")
            if (actResult.isSuccess && actResult.stdout.isNotBlank()) {
                val match = Regex("""u0\s+([^/\s}]+)""").find(actResult.stdout)
                pkg = match?.groupValues?.get(1)
            }
        }
        if (pkg.isNullOrBlank() || pkg == "null") return null

        val pidResult = ShellManager.exec("pidof '$pkg'")
        val pid = pidResult.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: run {
            val psResult = ShellManager.exec("ps -A -o PID,NAME | grep '$pkg'")
            psResult.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: 0
        }
        return Pair(pkg, pid)
    }

    fun getDisplayRefreshRate(): Float = displayRefreshRate
}
