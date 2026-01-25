package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.UfsStatus
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.UfsScripts

object UfsProvider {
    private var cachedPath: String? = null

    fun getUfsStatus(): UfsStatus {
        val pathResult = getPath()
        val path = pathResult.first
        val pathDebug = pathResult.second

        if (path.isEmpty()) {
            return UfsStatus(
                isSupported = false,
                debugInfo = "Path detection failed:\n$pathDebug"
            )
        }

        val result = ShellManager.exec(UfsScripts.getUfsInfo(path))
        if (!result.isSuccess) {
            return UfsStatus(
                controllerPath = path,
                isSupported = false,
                debugInfo = "Governor read failed for $path:\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}\nEXIT: ${result.exitCode}\n\nPath Log:\n$pathDebug"
            )
        }

        val output = result.stdout
        var gov = "unknown"
        var avail = emptyList<String>()
        var freq = "0"
        var minFreq = "0"
        var maxFreq = "0"
        var availFreq = emptyList<String>()

        output.lines().forEach { line ->
            when {
                line.startsWith("GOV=") -> gov = line.removePrefix("GOV=")
                line.startsWith("AVAIL=") -> avail = line.removePrefix("AVAIL=").split("\\s+".toRegex()).filter { it.isNotEmpty() }
                line.startsWith("FREQ=") -> freq = line.removePrefix("FREQ=")
                line.startsWith("MIN_FREQ=") -> minFreq = line.removePrefix("MIN_FREQ=")
                line.startsWith("MAX_FREQ=") -> maxFreq = line.removePrefix("MAX_FREQ=")
                line.startsWith("AVAIL_FREQ=") -> availFreq = line.removePrefix("AVAIL_FREQ=").split("\\s+".toRegex()).filter { it.isNotEmpty() }
            }
        }

        return UfsStatus(
            controllerPath = path,
            currentGovernor = gov,
            availableGovernors = avail,
            currentFreq = freq,
            minFreq = minFreq,
            maxFreq = maxFreq,
            availableFrequencies = availFreq,
            isSupported = true,
            debugInfo = "Success.\nPath: $path\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}\nEXIT: ${result.exitCode}"
        )
    }

    // Returns Pair<Path, DebugLog>
    private fun getPath(): Pair<String, String> {
        cachedPath?.let { return Pair(it, "Using cached path") }
        
        val result = ShellManager.exec(UfsScripts.findUfsDir())
        val debugLog = "CMD: findUfsDir\nEXIT: ${result.exitCode}\nSTDOUT: ${result.stdout}\nSTDERR: ${result.stderr}"
        
        // Check stdout even if exitCode != 0, as some shell implementations might behave oddly
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
