package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.shell.GpuScripts
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.utils.ShellUtils
import java.io.File
import android.opengl.EGL14
import android.opengl.GLES20

object GpuProvider {
    private var cachedPath: String? = null
    private var cachedGpuModel: String? = null

    fun clearCache() {
        cachedPath = null
    }

    fun getGpuStatus(): GpuStatus {
        val pathResult = getPath()
        val path = pathResult.first
        
        // Default / Empty values
        var load = 0f
        var curFreq = 0L
        var minFreq = 0L
        var maxFreq = 0L
        var targetFreq = 0L
        var rawMinFreq = ""
        var rawMaxFreq = ""
        var rawTargetFreq = ""
        var governor = "unknown"
        var availableGovernors = listOf("dummy", "performance", "powersave")
        var availableFrequencies = listOf("265000000", "500000000", "1400000000")
        
        if (path.isNotEmpty()) {
             val result = ShellManager.exec(GpuScripts.getGpuInfo(path))
             if (result.isSuccess) {
                 result.stdout.lines().forEach { line ->
                     when {
                         line.startsWith("GOV=") -> governor = line.removePrefix("GOV=").takeIf { it.isNotEmpty() } ?: "unknown"
                         line.startsWith("AVAIL=") -> {
                             val allGovs = line.removePrefix("AVAIL=").split("\\s+".toRegex()).filter { it.isNotBlank() }
                             // Filter out APU-specific governors that should not be manually set
                             // These are MediaTek-specific and can cause crashes when set manually
                             val unsafeGovernors = setOf("apupassive-pe", "apupassive", "apuconstrain", "apuuser")
                             availableGovernors = allGovs.filter { it !in unsafeGovernors }
                         }
                         line.startsWith("CUR_FREQ=") -> curFreq = line.removePrefix("CUR_FREQ=").toLongOrNull() ?: 0L
                         line.startsWith("MIN_FREQ=") -> {
                             rawMinFreq = line.removePrefix("MIN_FREQ=")
                             minFreq = rawMinFreq.toLongOrNull() ?: 0L
                         }
                         line.startsWith("MAX_FREQ=") -> {
                             rawMaxFreq = line.removePrefix("MAX_FREQ=")
                             maxFreq = rawMaxFreq.toLongOrNull() ?: 0L
                         }
                         line.startsWith("TARGET_FREQ=") -> {
                              rawTargetFreq = line.removePrefix("TARGET_FREQ=")
                              targetFreq = rawTargetFreq.toLongOrNull() ?: 0L
                         }
                         line.startsWith("AVAIL_FREQ=") -> availableFrequencies = line.removePrefix("AVAIL_FREQ=").split("\\s+".toRegex()).filter { it.isNotBlank() }
                         line.startsWith("LOAD=") -> {
                             val rawLoad = line.removePrefix("LOAD=").toFloatOrNull() ?: 0f
                             // Heuristic: if load > 1, assume 0-100 scale, else 0-1 scale. 
                             // But my script attempts to normalize adreno percent. Mali might be raw.
                             // Let's assume if slightly > 1 it's percent.
                             load = if (rawLoad > 1f) rawLoad / 100f else rawLoad
                         }
                     }
                 }
                 
                 // Fallback: If load is 0, estimate based on frequency usage
                 if (load == 0f && availableFrequencies.isNotEmpty()) {
                     val maxAvail = availableFrequencies.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
                     if (maxAvail > 0 && curFreq > 0) {
                         load = curFreq.toFloat() / maxAvail.toFloat()
                     }
                 }
             }
        }
        
        // Fallback for defaults if empty from script
        // Also ensure APU governors are filtered from the fallback
        if (availableGovernors.isEmpty()) {
            availableGovernors = listOf("dummy", "performance", "powersave")
        } else {
            // Extra safety: filter again in case any APU governors slipped through
            val unsafeGovernors = setOf("apupassive-pe", "apupassive", "apuconstrain", "apuuser")
            availableGovernors = availableGovernors.filter { it !in unsafeGovernors }
        }
        if (availableFrequencies.isEmpty()) availableFrequencies = listOf("265000000", "500000000", "1400000000")

        val sysfsName = if (path.isNotEmpty()) File(path).name else "Unknown"
        val renderer = getGpuModel()

        // GPU frequencies from devfreq are in Hz, but formatFreq expects kHz
        // Convert Hz to kHz by dividing by 1000
        return GpuStatus(
            loadPercent = load,
            currentFreq = ShellUtils.formatFreq(curFreq / 1000),
            minFreq = ShellUtils.formatFreq(minFreq / 1000),
            maxFreq = ShellUtils.formatFreq(maxFreq / 1000),
            targetFreq = ShellUtils.formatFreq(targetFreq / 1000),
            rawMinFreq = rawMinFreq,
            rawMaxFreq = rawMaxFreq,
            rawTargetFreq = rawTargetFreq,
            governor = governor,
            availableGovernors = availableGovernors,
            availableFrequencies = availableFrequencies,
            model = renderer,
            renderer = renderer,
            sysfsPath = sysfsName
        )
    }

    private fun getPath(): Pair<String, String> {
        cachedPath?.let { return Pair(it, "Using cached path") }
        
        val result = ShellManager.exec(GpuScripts.findGpuPath())
        val debugLog = "CMD: findGpuPath\nEXIT: ${result.exitCode}\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}"
        
        if (result.stdout.isNotBlank()) {
            val p = result.stdout.trim().lines().firstOrNull() ?: ""
            if (p.isNotEmpty() && p.startsWith("/")) {
                cachedPath = p
                return Pair(p, debugLog)
            }
        }
        return Pair("", debugLog)
    }

    private fun getGpuModel(): String {
        cachedGpuModel?.let { return it }
        
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            
            val config = configs[0]
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
            
            EGL14.eglMakeCurrent(display, surface, surface, context)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            
            renderer?.also { cachedGpuModel = it } ?: "Unknown GPU"
        } catch (e: Exception) {
            val path = cachedPath ?: ""
            if (path.contains("mali", true)) "Mali GPU" else if (path.contains("kgsl", true)) "Adreno GPU" else "Unknown GPU"
        }
    }

    fun setGovernor(governor: String): Boolean {
        val path = getPath().first
        if (path.isEmpty()) return false
        return ShellManager.exec(GpuScripts.setGovernor(path, governor)).isSuccess
    }

    fun setFrequency(freq: String, type: Int): Boolean {
        // type: 0=min, 1=max, 2=target
        val path = getPath().first
        if (path.isEmpty()) return false
        val fileType = when(type) {
            0 -> "min_freq"
            1 -> "max_freq"
            2 -> "target_freq"
            else -> return false
        }
        return ShellManager.exec(GpuScripts.setFrequency(path, freq, fileType)).isSuccess
    }
}
