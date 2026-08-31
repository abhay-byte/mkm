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
    data class Failed(
        val reason: String,
        val failedStep: RangeWriteStep? = null,
        val readback: RangeReadback? = null
    ) : RangeTransactionResult
}

/** Executes an ordered range plan and requires both read-back checkpoints. */
object RangeWriteTransaction {
    fun execute(
        plan: RangeWritePlan,
        write: (RangeWriteStep) -> Boolean,
        readImmediate: () -> RangeReadback,
        readFinal: () -> RangeReadback
    ): RangeTransactionResult {
        for (step in plan.steps) {
            if (!write(step)) {
                return RangeTransactionResult.Failed("Range write failed", failedStep = step)
            }
        }

        val immediate = runCatching { readImmediate() }.getOrElse {
            return RangeTransactionResult.Failed("Immediate range read-back failed")
        }
        if (!immediate.isValid) {
            return RangeTransactionResult.Failed("Immediate range read-back is invalid", readback = immediate)
        }

        val final = runCatching { readFinal() }.getOrElse {
            return RangeTransactionResult.Failed("Final range read-back failed", readback = immediate)
        }
        if (!final.isValid) {
            return RangeTransactionResult.Failed("Final range read-back is invalid", readback = final)
        }
        return RangeTransactionResult.Verified(immediate, final)
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
    val reason: String? = null
)
