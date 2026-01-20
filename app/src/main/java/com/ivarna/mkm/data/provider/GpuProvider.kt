package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.utils.ShellUtils
import java.io.File
import android.opengl.EGL14
import android.opengl.GLES20

object GpuProvider {
    private val maliPath = "/sys/class/misc/mali0/device/devfreq/13000000.mali"
    private val adrenoPath = "/sys/class/kgsl/kgsl-3d0"
    
    private var cachedGpuModel: String? = null

    private fun getGpuPath(): String? {
        if (File(maliPath).exists()) return maliPath
        if (File(adrenoPath).exists()) return adrenoPath
        return null
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
            val path = getGpuPath()
            if (path == maliPath) "Mali GPU" else if (path == adrenoPath) "Adreno GPU" else "Unknown GPU"
        }
    }

    fun getGpuStatus(): GpuStatus {
        val path = getGpuPath()
        
        // Load
        var load = 0f
        if (path == maliPath) {
            load = ShellUtils.readFile("/sys/kernel/ged/hal/gpu_utilization").toFloatOrNull() ?: 0f
        } else if (path == adrenoPath) {
            load = ShellUtils.readFile("$adrenoPath/gpubusy").split(Regex("\\s+"))
                .getOrNull(0)?.toFloatOrNull() ?: 0f
        }

        // Frequencies and Governor
        val devfreqPath = if (path == adrenoPath) "$adrenoPath/devfreq" else path
        
        var curFreq = ShellUtils.readFile(if (path == adrenoPath) "$adrenoPath/gpuclk" else "$devfreqPath/cur_freq").toLongOrNull() ?: 0L
        var minFreq = ShellUtils.readFile("$devfreqPath/min_freq").toLongOrNull() ?: 0L
        var maxFreq = ShellUtils.readFile("$devfreqPath/max_freq").toLongOrNull() ?: 0L
        var targetFreq = ShellUtils.readFile("$devfreqPath/target_freq").toLongOrNull() ?: 0L
        
        val rawMinFreq = ShellUtils.readFile("$devfreqPath/min_freq")
        val rawMaxFreq = ShellUtils.readFile("$devfreqPath/max_freq")
        val rawTargetFreq = ShellUtils.readFile("$devfreqPath/target_freq")
        
        val governor = ShellUtils.readFile("$devfreqPath/governor")
        val availableGovernors = ShellUtils.readFile("$devfreqPath/available_governors")
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        
        val availableFrequencies = ShellUtils.readFile("$devfreqPath/available_frequencies")
            .split(Regex("\\s+")).filter { it.isNotBlank() }

        // Frequency normalization (Hz vs KHz vs MHz)
        if (curFreq > 10000000) curFreq /= 1000
        if (minFreq > 10000000) minFreq /= 1000
        if (maxFreq > 10000000) maxFreq /= 1000
        if (targetFreq > 10000000) targetFreq /= 1000

        // Identification
        val sysfsName = if (path == maliPath) "13000000.mali" else if (path == adrenoPath) "kgsl-3d0" else "Unknown"
        val renderer = getGpuModel()

        return GpuStatus(
            loadPercent = load / 100f,
            currentFreq = ShellUtils.formatFreq(curFreq),
            minFreq = ShellUtils.formatFreq(minFreq),
            maxFreq = ShellUtils.formatFreq(maxFreq),
            targetFreq = ShellUtils.formatFreq(targetFreq),
            rawMinFreq = rawMinFreq,
            rawMaxFreq = rawMaxFreq,
            rawTargetFreq = rawTargetFreq,
            governor = if (governor.isEmpty()) "unknown" else governor,
            availableGovernors = if (availableGovernors.isEmpty()) listOf("dummy", "performance", "powersave") else availableGovernors,
            availableFrequencies = if (availableFrequencies.isEmpty()) listOf("265000000", "500000000", "1400000000") else availableFrequencies,
            model = renderer,
            renderer = renderer,
            sysfsPath = sysfsName
        )
    }

    fun setGovernor(governor: String): Boolean {
        val path = getGpuPath() ?: return false
        val devfreqPath = if (path == adrenoPath) "$adrenoPath/devfreq" else path
        return ShellUtils.writeFile("$devfreqPath/governor", governor)
    }

    fun setFrequency(freq: String, type: Int): Boolean {
        // type: 0=min, 1=max, 2=target
        val path = getGpuPath() ?: return false
        val devfreqPath = if (path == adrenoPath) "$adrenoPath/devfreq" else path
        val file = when(type) {
            0 -> "min_freq"
            1 -> "max_freq"
            2 -> "target_freq"
            else -> return false
        }
        return ShellUtils.writeFile("$devfreqPath/$file", freq)
    }
}
