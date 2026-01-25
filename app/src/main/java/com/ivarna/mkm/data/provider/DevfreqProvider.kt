package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.DevfreqStatus
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.DevfreqScripts

object DevfreqProvider {
    private var cachedPath: String? = null

    fun getDevfreqStatus(): DevfreqStatus {
        val pathResult = getPath()
        val path = pathResult.first
        val pathDebug = pathResult.second

        if (path.isEmpty()) {
            return DevfreqStatus(
                isSupported = false,
                debugInfo = "Path detection failed:\n$pathDebug"
            )
        }

        val result = ShellManager.exec(DevfreqScripts.getDevfreqInfo(path))
        if (!result.isSuccess) {
            return DevfreqStatus(
                controllerPath = path,
                isSupported = false,
                debugInfo = "Read failed for $path:\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}\nEXIT: ${result.exitCode}\n\nPath Log:\n$pathDebug"
            )
        }

        val output = result.stdout
        var gov = "unknown"
        var avail = emptyList<String>()
        var freq = "0"
        var availFreq = emptyList<String>()

        output.lines().forEach { line ->
            when {
                line.startsWith("GOV=") -> gov = line.removePrefix("GOV=")
                line.startsWith("AVAIL=") -> avail = line.removePrefix("AVAIL=").split("\\s+".toRegex()).filter { it.isNotEmpty() }
                line.startsWith("FREQ=") -> freq = line.removePrefix("FREQ=")
                line.startsWith("AVAIL_FREQ=") -> availFreq = line.removePrefix("AVAIL_FREQ=").split("\\s+".toRegex()).filter { it.isNotEmpty() }
            }
        }

        return DevfreqStatus(
            controllerPath = path,
            currentGovernor = gov,
            availableGovernors = avail,
            currentFreq = freq,
            availableFrequencies = availFreq,
            isSupported = true,
            debugInfo = "Success.\nPath: $path\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}\nEXIT: ${result.exitCode}"
        )
    }

    private fun getPath(): Pair<String, String> {
        cachedPath?.let { return Pair(it, "Using cached path") }
        
        val result = ShellManager.exec(DevfreqScripts.findDevfreqDir())
        val debugLog = "CMD: findDevfreqDir\nEXIT: ${result.exitCode}\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}"
        
        if (result.stdout.isNotBlank()) {
            val p = result.stdout.trim().lines().firstOrNull() ?: ""
            if (p.isNotEmpty() && p.contains("/")) {
                cachedPath = p
                return Pair(p, debugLog)
            }
        }
        return Pair("", debugLog)
    }
}
