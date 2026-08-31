package com.ivarna.mkm.data.provider

import android.opengl.EGL14
import android.opengl.GLES20
import android.util.Log
import com.ivarna.mkm.data.model.ApplyResult
import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.FrequencyCapabilityParser
import com.ivarna.mkm.data.model.FrequencyRangePlanner
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.data.model.GpuTuningCapabilities
import com.ivarna.mkm.data.model.RangeReadback
import com.ivarna.mkm.data.model.RangeTransactionResult
import com.ivarna.mkm.data.model.RangeWriteTransaction
import com.ivarna.mkm.data.model.ScalarReadbackVerifier
import com.ivarna.mkm.shell.GpuScripts
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.SysfsTuningExecutor
import com.ivarna.mkm.utils.ShellUtils
import java.io.File

object GpuProvider {
    private const val TAG = "GpuProvider"
    private var cachedPath: String? = null
    private var cachedGpuModel: String? = null

    /** Explicit diagnostic/redetection hook; normal refreshes retain identity. */
    fun clearCache() {
        cachedPath = null
        cachedGpuModel = null
    }

    fun getGpuStatus(): GpuStatus = getGpuStatus(allowRedetection = true)

    private fun getGpuStatus(allowRedetection: Boolean): GpuStatus {
        val path = getPath().first
        if (path.isEmpty()) return GpuStatus(capabilityReason = "GPU devfreq device was not detected")

        // Prefer the same elevated read path used by mutations when available;
        // a permitted Shizuku shell can still be denied by vendor sysfs policy.
        val result = if (ShellManager.hasRoot() || ShellManager.hasShizuku()) {
            ShellManager.exec(GpuScripts.getGpuInfo(path), ShellManager.PrivilegeRequirement.ELEVATED_SYSFS)
        } else {
            ShellManager.exec(GpuScripts.getGpuInfo(path))
        }
        if (!result.isSuccess && !hasTuningNode(path)) {
            // A cached devfreq directory can survive after its driver nodes
            // disappear. Force one redetection rather than presenting stale
            // capabilities or attempting writes against the old device.
            cachedPath = null
            return if (allowRedetection) getGpuStatus(allowRedetection = false)
            else GpuStatus(capabilityReason = "GPU devfreq device disappeared while reading its capabilities")
        }
        var load = 0f
        var curFreq = 0L
        var minFreq = 0L
        var maxFreq = 0L
        var targetFreq = 0L
        var governor = "unknown"
        var availableGovernors = emptyList<String>()
        var availableFrequencies = emptyList<Long>()
        var frequencyAvailable = false

        if (result.isSuccess) {
            result.stdout.lines().forEach { line ->
                when {
                    line.startsWith("GOV=") -> governor = line.removePrefix("GOV=").trim().ifEmpty { "unknown" }
                    line.startsWith("AVAIL=") -> availableGovernors = line.removePrefix("AVAIL=")
                        .split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
                    line.startsWith("CUR_FREQ=") -> curFreq = line.removePrefix("CUR_FREQ=").trim().toLongOrNull() ?: 0L
                    line.startsWith("FREQ_AVAILABLE=") -> frequencyAvailable = line.removePrefix("FREQ_AVAILABLE=").trim() == "1"
                    line.startsWith("MIN_FREQ=") -> minFreq = line.removePrefix("MIN_FREQ=").trim().toLongOrNull() ?: 0L
                    line.startsWith("MAX_FREQ=") -> maxFreq = line.removePrefix("MAX_FREQ=").trim().toLongOrNull() ?: 0L
                    line.startsWith("TARGET_FREQ=") -> targetFreq = line.removePrefix("TARGET_FREQ=").trim().toLongOrNull() ?: 0L
                    line.startsWith("AVAIL_FREQ=") -> availableFrequencies = FrequencyCapabilityParser.normalize(
                        listOf(line.removePrefix("AVAIL_FREQ="))
                    )
                    line.startsWith("LOAD=") -> {
                        val raw = line.removePrefix("LOAD=").trim().toFloatOrNull() ?: 0f
                        load = if (raw > 1f) raw / 100f else raw
                    }
                }
            }
        }

        val knownPoints = listOf(curFreq, minFreq, maxFreq, targetFreq)
        val capability = if (availableFrequencies.isNotEmpty()) {
            FrequencyCapability.Discrete(availableFrequencies)
        } else {
            val known = FrequencyCapabilityParser.normalizeLongs(knownPoints)
            if (known.isNotEmpty()) FrequencyCapability.Discrete(known)
            else FrequencyCapability.Unavailable("No GPU frequency table is exposed by this kernel")
        }
        val uiFrequencies = FrequencyCapabilityParser.valuesForUi(capability).map(Long::toString)
        val rangeFilesExist = SysfsTuningExecutor.exists("$path/min_freq") && SysfsTuningExecutor.exists("$path/max_freq")
        val reason = when {
            !result.isSuccess -> "GPU metrics could not be read: ${result.stderr.ifBlank { "unknown error" }}"
            !rangeFilesExist -> "GPU frequency controls are unavailable on this kernel"
            capability is FrequencyCapability.Unavailable -> capability.reason
            else -> null
        }
        val sysfsName = File(path).name
        val renderer = getGpuModel()

        return GpuStatus(
            loadPercent = load.coerceIn(0f, 1f),
            currentFreq = if (frequencyAvailable && curFreq > 0L) formatRawFrequency(curFreq) else "N/A",
            minFreq = if (minFreq > 0L) formatRawFrequency(minFreq) else "N/A",
            maxFreq = if (maxFreq > 0L) formatRawFrequency(maxFreq) else "N/A",
            targetFreq = if (targetFreq > 0L) formatRawFrequency(targetFreq) else "N/A",
            rawMinFreq = minFreq.takeIf { it > 0L }?.toString() ?: "",
            rawMaxFreq = maxFreq.takeIf { it > 0L }?.toString() ?: "",
            rawTargetFreq = targetFreq.takeIf { it > 0L }?.toString() ?: "",
            governor = governor,
            availableGovernors = availableGovernors,
            availableFrequencies = uiFrequencies,
            model = renderer,
            renderer = renderer,
            sysfsPath = sysfsName,
            frequencyAvailable = frequencyAvailable,
            freqRequiresRoot = path.isNotEmpty() && !frequencyAvailable,
            frequencyCapability = capability,
            governorWritable = SysfsTuningExecutor.canWrite("$path/governor") && availableGovernors.isNotEmpty(),
            minWritable = SysfsTuningExecutor.canWrite("$path/min_freq") && SysfsTuningExecutor.canWrite("$path/max_freq"),
            maxWritable = SysfsTuningExecutor.canWrite("$path/min_freq") && SysfsTuningExecutor.canWrite("$path/max_freq"),
            targetWritable = SysfsTuningExecutor.canWrite("$path/target_freq"),
            capabilityReason = reason,
            tuningCapabilities = GpuTuningCapabilities(
                path = path,
                governors = availableGovernors,
                frequencies = capability,
                governorWritable = SysfsTuningExecutor.canWrite("$path/governor") && availableGovernors.isNotEmpty(),
                minWritable = SysfsTuningExecutor.canWrite("$path/min_freq") && SysfsTuningExecutor.canWrite("$path/max_freq"),
                maxWritable = SysfsTuningExecutor.canWrite("$path/min_freq") && SysfsTuningExecutor.canWrite("$path/max_freq"),
                targetWritable = SysfsTuningExecutor.canWrite("$path/target_freq"),
                requiresRoot = path.isNotEmpty() && !frequencyAvailable,
                reason = reason
            )
        )
    }

    fun applyGovernor(governor: String): ApplyResult {
        if (!SysfsTuningExecutor.isSafeValue(governor)) return ApplyResult.Failed("Invalid GPU governor")
        val path = getPath().first.takeIf { it.isNotEmpty() }
            ?: return ApplyResult.Failed("GPU devfreq device is unavailable")
        val supported = SysfsTuningExecutor.read("$path/available_governors")
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        if (supported.isEmpty() || governor !in supported) {
            return ApplyResult.Failed("Governor '$governor' is not advertised by the GPU driver")
        }
        val before = SysfsTuningExecutor.read("$path/governor")
        val result = SysfsTuningExecutor.write("$path/governor", governor)
        if (!result.isSuccess) return failed("governor", result)
        val after = SysfsTuningExecutor.read("$path/governor")
        val outcome = ScalarReadbackVerifier.verify(
            requested = governor,
            actual = after,
            adjustedReason = "GPU driver selected a different governor."
        )
        Log.i(TAG, "domain=GPU path=$path vendor=${classifyGpuPath(path)} request=governor:$governor before=$before backend=${result.backend} after=$after result=${outcome::class.simpleName}")
        return outcome
    }

    fun applyRange(desiredMin: Long? = null, desiredMax: Long? = null): ApplyResult {
        if (desiredMin == null && desiredMax == null) return ApplyResult.Failed("No GPU frequency requested")
        val path = getPath().first.takeIf { it.isNotEmpty() }
            ?: return ApplyResult.Failed("GPU devfreq device is unavailable")
        if (!SysfsTuningExecutor.exists("$path/min_freq") || !SysfsTuningExecutor.exists("$path/max_freq")) {
            return ApplyResult.Failed("GPU min/max frequency controls are unavailable on this kernel")
        }
        val currentMin = SysfsTuningExecutor.readLong("$path/min_freq")
        val currentMax = SysfsTuningExecutor.readLong("$path/max_freq")
        if (currentMin == null || currentMax == null) return ApplyResult.Failed("GPU frequency bounds are unavailable")
        if (currentMin > currentMax) return ApplyResult.Failed("GPU driver reports an invalid frequency range")
        val capability = getGpuCapability(path, currentMin, currentMax)
        if (!listOfNotNull(desiredMin, desiredMax).all { supportedFrequency(it, capability) }) {
            return ApplyResult.Failed("Requested GPU frequency is not advertised by the GPU driver")
        }
        val plan = when {
            desiredMin != null && desiredMax != null -> FrequencyRangePlanner.plan(currentMin, currentMax, desiredMin, desiredMax)
            desiredMax != null -> FrequencyRangePlanner.forMax(currentMin, currentMax, desiredMax)
            else -> FrequencyRangePlanner.forMin(currentMin, currentMax, desiredMin!!)
        }
        val before = "min=$currentMin,max=$currentMax"
        val writeLog = mutableListOf<String>()
        var lastWriteResult: ShellManager.CommandResult? = null
        val transaction = RangeWriteTransaction.execute(
            plan = plan,
            write = { step ->
                val file = if (step.isMin) "min_freq" else "max_freq"
                lastWriteResult = SysfsTuningExecutor.write("$path/$file", step.value.toString())
                if (lastWriteResult!!.isSuccess) writeLog += "$file=OK(${lastWriteResult!!.backend})"
                lastWriteResult!!.isSuccess
            },
            readImmediate = { readRange(path) },
            readFinal = {
                Thread.sleep(150L)
                readRange(path)
            }
        )
        if (transaction is RangeTransactionResult.Failed) {
            val step = transaction.failedStep
            val file = step?.let { if (it.isMin) "min_freq" else "max_freq" } ?: "read-back"
            val writeResult = lastWriteResult
            val stderr = writeResult?.stderr?.takeIf { it.isNotBlank() }
                ?: transaction.readback?.let { "readback=min=${it.min},max=${it.max}" }
            return if (writeResult != null && !writeResult.isSuccess) {
                failed(file, writeResult)
            } else {
                ApplyResult.Failed("GPU frequency $file failed: ${transaction.reason}", stderr)
            }
        }
        val verified = transaction as RangeTransactionResult.Verified
        val first = verified.immediate
        val after = verified.final
        val requested = "min=${plan.min},max=${plan.max}"
        val outcome = when {
            after.min != plan.min || after.max != plan.max -> ApplyResult.Adjusted(
                requested, "min=${after.min},max=${after.max}", "GPU driver clamped or overrode the requested range."
            )
            plan.adjusted -> ApplyResult.Adjusted(requested, requested, plan.adjustmentReason ?: "Range adjusted")
            else -> ApplyResult.Applied(requested, requested)
        }
        Log.i(TAG, "domain=GPU path=$path vendor=${classifyGpuPath(path)} request=$requested before=$before plan=${plan.steps.joinToString { if (it.isMin) "min=${it.value}" else "max=${it.value}" }} writes=${writeLog.joinToString()} afterFirst=$first after=$after result=${outcome::class.simpleName}")
        return outcome
    }

    fun applyTarget(freq: Long): ApplyResult {
        val path = getPath().first.takeIf { it.isNotEmpty() }
            ?: return ApplyResult.Failed("GPU devfreq device is unavailable")
        if (!SysfsTuningExecutor.exists("$path/target_freq")) return ApplyResult.Failed("GPU target frequency is unavailable on this kernel")
        val min = SysfsTuningExecutor.readLong("$path/min_freq") ?: freq
        val max = SysfsTuningExecutor.readLong("$path/max_freq") ?: freq
        if (!supportedFrequency(freq, getGpuCapability(path, min, max))) {
            return ApplyResult.Failed("Requested GPU target is not advertised by the GPU driver")
        }
        val result = SysfsTuningExecutor.write("$path/target_freq", freq.toString())
        if (!result.isSuccess) return failed("target_freq", result)
        val actual = SysfsTuningExecutor.read("$path/target_freq").toLongOrNull() ?: 0L
        if (actual <= 0L) return ApplyResult.Failed("GPU target frequency read-back unavailable")
        val outcome = ScalarReadbackVerifier.verify(
            requested = freq.toString(),
            actual = actual.toString(),
            adjustedReason = "GPU driver selected a different target frequency."
        )
        Log.i(TAG, "domain=GPU path=$path vendor=${classifyGpuPath(path)} request=target:$freq backend=${result.backend} after=$actual result=${outcome::class.simpleName}")
        return outcome
    }

    fun setGovernor(governor: String): Boolean = applyGovernor(governor) !is ApplyResult.Failed

    fun setFrequency(freq: String, type: Int): Boolean {
        val value = freq.toLongOrNull() ?: return false
        val result = when (type) {
            0 -> applyRange(desiredMin = value)
            1 -> applyRange(desiredMax = value)
            2 -> applyTarget(value)
            else -> ApplyResult.Failed("Unknown GPU frequency control")
        }
        return result !is ApplyResult.Failed
    }

    private fun getPath(): Pair<String, String> {
        cachedPath?.let { path ->
            if (hasTuningNode(path)) return path to "Using cached path"
            cachedPath = null
        }
        val result = if (ShellManager.hasRoot() || ShellManager.hasShizuku()) {
            ShellManager.exec(GpuScripts.findGpuPath(), ShellManager.PrivilegeRequirement.ELEVATED_SYSFS)
        } else {
            ShellManager.exec(GpuScripts.findGpuPath())
        }
        val debugLog = "CMD: findGpuPath\nEXIT: ${result.exitCode}\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}"
        val path = result.stdout.trim().lines()
            .firstOrNull { SysfsTuningExecutor.isSafeSysfsPath(it) }
            .orEmpty()
        if (path.isNotEmpty()) {
            cachedPath = path
            return path to debugLog
        }
        return "" to debugLog
    }

    private fun getGpuCapability(path: String, currentMin: Long, currentMax: Long): FrequencyCapability {
        val available = SysfsTuningExecutor.read("$path/available_frequencies")
        val stats = SysfsTuningExecutor.read("$path/stats/time_in_state")
            .lines()
            .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
        val legacyStats = SysfsTuningExecutor.read("$path/time_in_state")
            .lines()
            .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
        return FrequencyCapabilityParser.fromDiscreteSources(
            sources = listOf(
                listOf(available),
                stats,
                legacyStats
            ),
            rangeMin = currentMin,
            rangeMax = currentMax,
            knownPoints = listOf(
                currentMin,
                currentMax,
                SysfsTuningExecutor.readLong("$path/cur_freq") ?: 0L,
                SysfsTuningExecutor.readLong("$path/target_freq") ?: 0L
            )
        )
    }

    private fun supportedFrequency(value: Long, capability: FrequencyCapability): Boolean = when (capability) {
        is FrequencyCapability.Discrete -> value in capability.values
        is FrequencyCapability.Range -> value in capability.min..capability.max
        is FrequencyCapability.Unavailable -> false
    }

    private fun readRange(path: String): RangeReadback = RangeReadback(
        min = SysfsTuningExecutor.readLong("$path/min_freq") ?: 0L,
        max = SysfsTuningExecutor.readLong("$path/max_freq") ?: 0L
    )

    private fun hasTuningNode(path: String): Boolean {
        if (!SysfsTuningExecutor.isSafeSysfsPath(path)) return false
        return File(path).exists() && listOf(
            "governor", "cur_freq", "cur_frequency", "available_frequencies"
        ).any { SysfsTuningExecutor.exists("$path/$it") }
    }

    private fun failed(path: String, result: ShellManager.CommandResult): ApplyResult {
        Log.e(TAG, "domain=GPU path=$path backend=${result.backend} exit=${result.exitCode} stderr=${result.stderr}")
        return ApplyResult.Failed("GPU write failed for $path", result.stderr)
    }

    private fun classifyGpuPath(path: String): String = when {
        path.contains("kgsl", ignoreCase = true) -> "Adreno/KGSL"
        path.contains("mali", ignoreCase = true) -> "Mali/MediaTek"
        path.contains("pvr", ignoreCase = true) || path.contains("rgx", ignoreCase = true) -> "PowerVR"
        else -> "Unknown"
    }

    private fun formatRawFrequency(raw: Long): String = ShellUtils.formatFreq(if (raw > 10_000_000L) raw / 1000L else raw)

    private fun getGpuModel(): String {
        cachedGpuModel?.let { return it }
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)
            val configAttribs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
            EGL14.eglMakeCurrent(display, surface, surface, context)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            renderer?.takeIf { it.isNotBlank() }?.also { cachedGpuModel = it } ?: "Unknown GPU"
        } catch (_: Exception) {
            val path = cachedPath ?: ""
            if (path.contains("mali", true)) "Mali GPU" else if (path.contains("kgsl", true)) "Adreno GPU" else "Unknown GPU"
        }
    }
}
