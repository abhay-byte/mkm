package com.ivarna.mkm.data.provider

import com.ivarna.mkm.data.model.DiscoveredFrequencySource
import com.ivarna.mkm.data.model.FrequencyCapability
import com.ivarna.mkm.data.model.FrequencyCapabilityParser
import com.ivarna.mkm.shell.GpuScripts
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.SysfsTuningExecutor

data class GpuFrequencyDiscoveryResult(
    val capability: FrequencyCapability,
    val sources: List<DiscoveredFrequencySource>,
    val knownPointsOnly: Boolean
)

/** Discovers only frequency values actually exposed by the selected GPU driver. */
object GpuFrequencyDiscovery {
    fun discoverGpuFrequencies(
        path: String,
        knownPoints: Iterable<Long> = emptyList(),
        read: (String) -> String = SysfsTuningExecutor::read,
        exists: (String) -> Boolean = SysfsTuningExecutor::exists,
        extraPaths: Iterable<String> = discoverVendorPaths(path)
    ): GpuFrequencyDiscoveryResult {
        val candidates = standardPaths(path) + extraPaths
        val sources = candidates.distinct().mapNotNull { sourcePath ->
            if (!isTrustedSourcePath(sourcePath, path)) return@mapNotNull null
            if (!exists(sourcePath)) return@mapNotNull null
            val values = normalize(read(sourcePath), sourcePath)
            values.takeIf { it.isNotEmpty() }?.let {
                DiscoveredFrequencySource(sourcePath, it)
            }
        }
        val merged = sources.flatMap { it.values }.distinct().sorted()
        if (merged.isNotEmpty()) {
            return GpuFrequencyDiscoveryResult(
                capability = FrequencyCapability.Discrete(merged),
                sources = sources,
                knownPointsOnly = false
            )
        }

        val known = FrequencyCapabilityParser.normalizeLongs(knownPoints)
        return GpuFrequencyDiscoveryResult(
            capability = if (known.isNotEmpty()) FrequencyCapability.Discrete(known)
            else FrequencyCapability.Unavailable("No GPU frequency table is exposed by this kernel"),
            sources = emptyList(),
            knownPointsOnly = known.isNotEmpty()
        )
    }

    private fun standardPaths(path: String): List<String> = buildList {
        addAll(listOf(
        "$path/available_frequencies",
        "$path/stats/time_in_state",
        "$path/time_in_state",
        "$path/opp_table/operating-points",
        "$path/opp_table/opp-hz"
        ))
        val lower = path.lowercase()
        if (lower.contains("kgsl")) {
            addAll(listOf(
                "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies",
                "/sys/class/kgsl/kgsl-3d0/freq_table_mhz"
            ))
        }
        if (lower.contains("mali")) {
            addAll(listOf(
                "/sys/kernel/ged/hal/gpu_frequency_table",
                "/sys/kernel/ged/hal/gpu_freq_table"
            ))
        }
    }

    private fun discoverVendorPaths(path: String): List<String> {
        if (!SysfsTuningExecutor.isSafeSysfsPath(path)) return emptyList()
        val result = ShellManager.exec(
            GpuScripts.discoverGpuFrequencyPaths(path),
            ShellManager.PrivilegeRequirement.ELEVATED_SYSFS
        )
        return result.stdout.lines()
            .map(String::trim)
            .filter(SysfsTuningExecutor::isSafeSysfsPath)
            .filter { isTrustedSourcePath(it, path) }
            .distinct()
    }

    private fun isTrustedSourcePath(source: String, selectedPath: String): Boolean {
        if (source.startsWith("$selectedPath/")) return true
        val lower = source.lowercase()
        val selected = selectedPath.lowercase()
        val sameVendor = when {
            selected.contains("kgsl") -> lower.contains("kgsl")
            selected.contains("mali") -> lower.contains("mali") || lower.contains("ged")
            selected.contains("pvr") || selected.contains("rgx") ->
                lower.contains("pvr") || lower.contains("rgx")
            else -> false
        }
        val name = lower.substringAfterLast('/')
        return sameVendor && (name !in setOf("freq", "frequency", "clock_mhz") ||
            lower.contains("pwrlevels") || lower.contains("opp"))
    }

    private fun normalize(raw: String, source: String): List<Long> {
        val firstColumnOnly = source.contains("time_in_state", ignoreCase = true) ||
            source.endsWith("operating-points", ignoreCase = true) ||
            source.endsWith("opp-frequency", ignoreCase = true)
        val values = if (firstColumnOnly) {
            raw.lines().mapNotNull { line -> parseValue(line.trim().split(Regex("[\\s,;]+")).firstOrNull()) }
        } else {
            raw.lines().flatMap { line ->
                line.trim().split(Regex("[\\s,;]+"))
                    .mapNotNull(::parseValue)
            }
        }.filter { it > 0L }

        val multiplier = when {
            source.contains("mhz", ignoreCase = true) -> 1_000_000L
            source.contains("khz", ignoreCase = true) -> 1_000L
            else -> when {
                values.isEmpty() -> 1L
                values.max() < 10_000L -> 1_000_000L
                values.max() < 10_000_000L -> 1_000L
                else -> 1L
            }
        }
        return values.mapNotNull { value ->
            if (value > Long.MAX_VALUE / multiplier) null else value * multiplier
        }.filter { it > 0L }.distinct().sorted()
    }

    private fun parseValue(token: String?): Long? = token?.trim()?.trimEnd('%')?.let { value ->
        value.toLongOrNull()
            ?: value.substringAfterLast('=').substringAfterLast(':').toLongOrNull()
    }
}
