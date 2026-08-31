package com.ivarna.mkm.data.model

/** A frequency list or range that was actually observed on the device. */
sealed interface FrequencyCapability {
    data class Discrete(val values: List<Long>) : FrequencyCapability
    data class Range(val min: Long, val max: Long) : FrequencyCapability
    data class Unavailable(val reason: String) : FrequencyCapability
}

object FrequencyCapabilityParser {
    fun normalize(values: Iterable<String>): List<Long> = values
        .flatMap { it.trim().split(Regex("\\s+")) }
        .mapNotNull { it.toLongOrNull() }
        .filter { it > 0L }
        .distinct()
        .sorted()

    fun normalizeLongs(values: Iterable<Long>): List<Long> = values
        .filter { it > 0L }
        .distinct()
        .sorted()

    fun fromDiscreteSources(
        sources: Iterable<Iterable<String>>,
        rangeMin: Long?,
        rangeMax: Long?,
        knownPoints: Iterable<Long> = emptyList(),
        unavailableReason: String = "Frequency capability unavailable on this kernel"
    ): FrequencyCapability {
        for (source in sources) {
            val values = normalize(source)
            if (values.isNotEmpty()) return FrequencyCapability.Discrete(values)
        }
        val min = rangeMin ?: 0L
        val max = rangeMax ?: 0L
        if (min > 0L && max >= min) return FrequencyCapability.Range(min, max)
        val known = normalizeLongs(knownPoints)
        return if (known.isNotEmpty()) FrequencyCapability.Discrete(known)
        else FrequencyCapability.Unavailable(unavailableReason)
    }

    fun valuesForUi(capability: FrequencyCapability): List<Long> = when (capability) {
        is FrequencyCapability.Discrete -> capability.values
        is FrequencyCapability.Range -> listOf(capability.min, capability.max).distinct().sorted()
        is FrequencyCapability.Unavailable -> emptyList()
    }
}

data class RangeWritePlan(
    val min: Long,
    val max: Long,
    val steps: List<RangeWriteStep>,
    val adjusted: Boolean = false,
    val adjustmentReason: String? = null
)

data class RangeWriteStep(val isMin: Boolean, val value: Long)

data class RangeReadback(val min: Long, val max: Long) {
    val isValid: Boolean get() = min > 0L && max > 0L && min <= max
}

sealed interface RangeTransactionResult {
    data class Verified(val immediate: RangeReadback, val final: RangeReadback) : RangeTransactionResult
    data class FailedRolledBack(
        val reason: String,
        val restored: RangeReadback
    ) : RangeTransactionResult
    data class FailedStateChanged(
        val reason: String,
        val original: RangeReadback,
        val actual: RangeReadback
    ) : RangeTransactionResult
    data class Failed(
        val reason: String,
        val original: RangeReadback? = null,
        val actual: RangeReadback? = null
    ) : RangeTransactionResult
}

/** Executes an ordered range plan and rolls back any failed or unverifiable transaction. */
object RangeWriteTransaction {
    fun execute(
        original: RangeReadback,
        plan: RangeWritePlan,
        write: (RangeWriteStep) -> Boolean,
        readImmediate: () -> RangeReadback,
        readFinal: () -> RangeReadback
    ): RangeTransactionResult {
        var lastKnown: RangeReadback? = null
        for (step in plan.steps) {
            if (!write(step)) {
                lastKnown = readSafely(readImmediate)
                return rollback("Range write failed", original, lastKnown, write, readImmediate, readFinal)
            }
        }

        val immediate = readSafely(readImmediate)
            ?: return rollback("Immediate range read-back failed", original, lastKnown, write, readImmediate, readFinal)
        if (!immediate.isValid) {
            return rollback("Immediate range read-back is invalid", original, immediate, write, readImmediate, readFinal)
        }
        lastKnown = immediate

        val final = readSafely(readFinal)
            ?: return rollback("Final range read-back failed", original, lastKnown, write, readImmediate, readFinal)
        if (!final.isValid) {
            return rollback("Final range read-back is invalid", original, final, write, readImmediate, readFinal)
        }
        return RangeTransactionResult.Verified(immediate, final)
    }

    private fun readSafely(reader: () -> RangeReadback): RangeReadback? =
        runCatching { reader() }.getOrNull()

    private fun rollback(
        reason: String,
        original: RangeReadback,
        actual: RangeReadback?,
        write: (RangeWriteStep) -> Boolean,
        readImmediate: () -> RangeReadback,
        readFinal: () -> RangeReadback
    ): RangeTransactionResult {
        val rollbackBase = actual?.takeIf { it.isValid }
        val rollbackPlan = rollbackBase?.let {
            runCatching { FrequencyRangePlanner.plan(it.min, it.max, original.min, original.max) }.getOrNull()
        }

        if (rollbackPlan != null) {
            for (step in rollbackPlan.steps) {
                if (!write(step)) {
                    return rollbackFailure("$reason; rollback write failed", original, actual, readImmediate, readFinal)
                }
            }
        } else {
            // If a read-back failed, the last range is unknown. Try both valid
            // orders using the original bounds so a partial write can still be
            // recovered without relying on an invented frequency.
            val fallbackPlans = listOf(
                listOf(RangeWriteStep(true, original.min), RangeWriteStep(false, original.max)),
                listOf(RangeWriteStep(false, original.max), RangeWriteStep(true, original.min))
            )
            for (fallback in fallbackPlans) {
                if (fallback.all(write)) break
            }
        }

        val restoredImmediate = readSafely(readImmediate)
        val restoredFinal = readSafely(readFinal)
        if (restoredFinal?.isValid == true && restoredFinal == original) {
            return RangeTransactionResult.FailedRolledBack(
                "$reason; original values restored", restoredFinal
            )
        }

        val observed = restoredFinal ?: restoredImmediate ?: actual
        return if (observed?.isValid == true && observed != original) {
            RangeTransactionResult.FailedStateChanged(
                "$reason; rollback did not restore the original range", original, observed
            )
        } else {
            RangeTransactionResult.Failed(
                "$reason; rollback state could not be verified", original, observed
            )
        }
    }

    private fun rollbackFailure(
        reason: String,
        original: RangeReadback,
        actual: RangeReadback?,
        readImmediate: () -> RangeReadback,
        readFinal: () -> RangeReadback
    ): RangeTransactionResult {
        val observed = readSafely(readFinal) ?: readSafely(readImmediate) ?: actual
        return if (observed?.isValid == true && observed != original) {
            RangeTransactionResult.FailedStateChanged(reason, original, observed)
        } else {
            RangeTransactionResult.Failed(reason, original, observed)
        }
    }
}

object ScalarReadbackVerifier {
    fun verify(requested: String, actual: String, adjustedReason: String): ApplyResult = when {
        actual.isBlank() -> ApplyResult.Failed("Read-back unavailable for requested value")
        actual == requested -> ApplyResult.Applied(requested, actual)
        else -> ApplyResult.Adjusted(requested, actual, adjustedReason)
    }
}

object SelectionOrdering {
    /** Sorts numeric frequency values and removes duplicate entries. */
    fun order(items: List<String>): List<String> {
        val numeric = items.map { it.toLongOrNull() }
        return if (numeric.isNotEmpty() && numeric.all { it != null }) {
            items.distinct().sortedBy { it.toLong() }
        } else {
            items
        }
    }
}

data class DiscoveredFrequencySource(
    val source: String,
    val values: List<Long>
)

object FrequencyRangePlanner {
    fun forMax(currentMin: Long, currentMax: Long, requestedMax: Long): RangeWritePlan {
        val finalMin = minOf(currentMin, requestedMax)
        return plan(currentMin, currentMax, finalMin, requestedMax,
            finalMin != currentMin,
            if (finalMin != currentMin) "Minimum was also lowered to keep a valid range." else null)
    }

    fun forMin(currentMin: Long, currentMax: Long, requestedMin: Long): RangeWritePlan {
        val finalMax = maxOf(currentMax, requestedMin)
        return plan(currentMin, currentMax, requestedMin, finalMax,
            finalMax != currentMax,
            if (finalMax != currentMax) "Maximum was also raised to keep a valid range." else null)
    }

    fun plan(
        currentMin: Long,
        currentMax: Long,
        desiredMin: Long,
        desiredMax: Long,
        adjusted: Boolean = false,
        adjustmentReason: String? = null
    ): RangeWritePlan {
        require(currentMin > 0L && currentMax > 0L) { "Current frequency bounds are unavailable" }
        require(desiredMin > 0L && desiredMax > 0L) { "Desired frequency bounds must be positive" }
        require(desiredMin <= desiredMax) { "Minimum frequency cannot exceed maximum frequency" }

        val steps = mutableListOf<RangeWriteStep>()
        fun add(isMin: Boolean, value: Long) {
            val current = if (isMin) currentMin else currentMax
            if (value != current) steps += RangeWriteStep(isMin, value)
        }

        when {
            desiredMax < currentMin -> {
                add(true, desiredMin)
                add(false, desiredMax)
            }
            desiredMin > currentMax -> {
                add(false, desiredMax)
                add(true, desiredMin)
            }
            desiredMax < currentMax -> {
                add(true, desiredMin)
                add(false, desiredMax)
            }
            desiredMin > currentMin -> {
                add(false, desiredMax)
                add(true, desiredMin)
            }
            else -> {
                add(true, desiredMin)
                add(false, desiredMax)
            }
        }
        return RangeWritePlan(desiredMin, desiredMax, steps, adjusted, adjustmentReason)
    }
}

sealed interface ApplyResult {
    data class Applied(val requested: String, val actual: String) : ApplyResult
    data class Adjusted(val requested: String, val actual: String, val reason: String) : ApplyResult
    data class Failed(val reason: String, val stderr: String? = null) : ApplyResult

    fun message(): String = when (this) {
        is Applied -> "Applied $actual"
        is Adjusted -> "$reason Actual: $actual"
        is Failed -> listOfNotNull(reason, stderr?.takeIf { it.isNotBlank() }).joinToString(": ")
    }
}

object TuningPersistencePolicy {
    fun shouldPersist(result: ApplyResult, bootEnabled: Boolean, stateRefreshed: Boolean): Boolean =
        bootEnabled && stateRefreshed && result !is ApplyResult.Failed
}

data class CpuPolicyState(
    val policyId: Int,
    val path: String,
    val affectedCpus: List<Int>,
    val relatedCpus: List<Int>,
    val governor: String,
    val supportedGovernors: List<String>,
    val minFreq: Long,
    val maxFreq: Long,
    val hwMinFreq: Long?,
    val hwMaxFreq: Long?,
    val frequencyCapability: FrequencyCapability
)

data class GpuTuningCapabilities(
    val path: String,
    val governors: List<String>,
    val frequencies: FrequencyCapability,
    val governorWritable: Boolean,
    val minWritable: Boolean,
    val maxWritable: Boolean,
    val targetWritable: Boolean,
    val requiresRoot: Boolean,
    val reason: String? = null,
    val frequencySources: List<String> = emptyList(),
    val frequencyTableComplete: Boolean = false
)
