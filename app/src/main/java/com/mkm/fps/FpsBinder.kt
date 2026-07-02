package com.mkm.fps

import android.util.Log

/**
 * JNI bridge to the native FPS parsing library (v2).
 *
 * Package name is exactly `com.mkm.fps` to match the native C++ JNI exports.
 */
class FpsBinder {

    companion object {
        private const val TAG = "MKM-FpsBinder-JNI"

        init {
            try {
                System.loadLibrary("fpsbinder")
                Log.i(TAG, "libfpsbinder.so loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libfpsbinder.so", e)
            }
        }
    }

    // ── Native declarations ─────────────────────────────────────────────
    external fun nativeInit(): Boolean
    external fun nativeIsAlive(): Boolean
    external fun nativeGetFpsForPackage(packageName: String): Double
    external fun nativeGetLayerFps(layerName: String): Double
    external fun nativeGetLastFpsSource(): String

    // Text-in entry points for shell-driven parsing
    external fun nativeFindLayerForPackage(listDumpText: String, packageName: String): String
    external fun nativeFpsFromLatencyText(latencyText: String): Double
    external fun nativeFpsFromLatencyFrameinfoText(frameinfoText: String): Double
    external fun nativeFpsFromGfxinfoText(gfxinfoText: String): Double

    // Legacy/compat exports
    external fun nativeGetActiveLayers(): Array<String>
    external fun nativeGetAllFps(): String
    external fun nativeGetFrameTimestamps(layerName: String): LongArray?
    external fun nativeGetUptimeMillis(): Long
    external fun nativeDump(args: String): String
    external fun nativeSetFpsLimit(fps: Int): Boolean
    external fun nativeInstallHooks(): Boolean
    external fun nativeHooksInstalled(): Boolean
    external fun nativeGetSfBinderHandle(): Long
    external fun nativeResetCounters()

    // ── High-level API ──────────────────────────────────────────────────
    fun computeFps(pkg: String, shellExec: (String) -> String): Double {
        // Tracks whether SF --list found a layer for this app.
        // If yes and --latency/--latency-frameinfo still returned no data,
        // that means SF sees the app but can't provide GPU timeline — the
        // exact pattern for GPU apps on broken Android 15/16 devices.
        var sfLayerFound = false

        // ── Strategy 1: SurfaceFlinger --latency (real GPU frames) ───────
        try {
            val listOutput = shellExec("dumpsys SurfaceFlinger --list")
            if (listOutput.isNotBlank()) {
                val layer = nativeFindLayerForPackage(listOutput, pkg)
                if (layer.isNotBlank()) {
                    sfLayerFound = true
                    val latency = shellExec("dumpsys SurfaceFlinger --latency '$layer'")
                    if (latency.isNotBlank()) {
                        val fps = nativeFpsFromLatencyText(latency)
                        if (fps > 0) {
                            Log.i(TAG, "FPS via --latency ($layer): $fps")
                            return fps
                        }
                    }
                    // Strategy 2: SurfaceFlinger --latency-frameinfo (Android 12+)
                    val frameinfo = shellExec("dumpsys SurfaceFlinger --latency-frameinfo '$layer'")
                    if (frameinfo.isNotBlank()) {
                        val fps = nativeFpsFromLatencyFrameinfoText(frameinfo)
                        if (fps > 0) {
                            Log.i(TAG, "FPS via --latency-frameinfo ($layer): $fps")
                            return fps
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SurfaceFlinger paths failed", e)
        }

        // ── Strategy 3: gfxinfo framestats ──────────────────────────────
        try {
            val gfxinfo = shellExec("dumpsys gfxinfo $pkg framestats")
            if (gfxinfo.isNotBlank()) {
                val rawFps = nativeFpsFromGfxinfoText(gfxinfo)
                if (rawFps > 0) {
                    // Only calibrate when SF found the app but produced no
                    // usable frame data — genuine GPU-rendered apps on
                    // broken-Android-15/16 devices. UI apps where SF simply
                    // has no layer get raw gfxinfo (which is accurate).
                    if (sfLayerFound) {
                        val calibrated = Math.max(0.0, (rawFps - 30.0) * 0.85)
                        Log.i(TAG, "FPS via gfxinfo (calibrated): raw=$rawFps → $calibrated")
                        return calibrated
                    }
                    Log.i(TAG, "FPS via gfxinfo framestats: $rawFps")
                    return rawFps
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "gfxinfo framestats failed", e)
        }

        return -1.0
    }
}
