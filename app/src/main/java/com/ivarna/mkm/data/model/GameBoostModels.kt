package com.ivarna.mkm.data.model

/** The global tuning primitives owned by Game Boost. */
enum class GameBoostComponent {
    CPU_GOVERNOR,
    CPU_MAX_LOCK,
    GPU_GOVERNOR,
    GPU_MAX_LOCK,
    STORAGE_GOVERNOR,
    STORAGE_MAX_LOCK
}

sealed interface GameBoostState {
    data object Off : GameBoostState
    data class Enabling(val step: String) : GameBoostState
    data class Active(
        val applied: Set<GameBoostComponent>,
        val unsupported: Set<GameBoostComponent> = emptySet(),
        val warnings: List<String> = emptyList()
    ) : GameBoostState
    data class ThermalLimited(
        val stillApplied: Set<GameBoostComponent>,
        val released: Set<GameBoostComponent>,
        val warnings: List<String> = emptyList()
    ) : GameBoostState
    data object Disabling : GameBoostState
    data class RecoveryRequired(val reason: String, val remaining: List<String>) : GameBoostState
}

data class GameBoostComponentCapability(
    val component: GameBoostComponent,
    val supported: Boolean,
    val reason: String? = null,
    val target: String? = null,
    val source: String? = null
)

data class GameBoostCapabilities(
    val components: Map<GameBoostComponent, GameBoostComponentCapability>,
    val boostPossible: Boolean,
    val maxLocksThermallySupported: Boolean
)

data class CpuBoostSnapshot(
    val policyId: Int,
    val path: String,
    val governor: String? = null,
    val minFreq: Long? = null,
    val maxFreq: Long? = null,
    val targetFreq: Long? = null,
    val targetGovernor: String? = null
)

data class GpuBoostSnapshot(
    val path: String,
    val governor: String? = null,
    val minFreq: Long? = null,
    val maxFreq: Long? = null,
    val targetFreq: Long? = null,
    val targetGovernor: String? = null
)

data class StorageBoostSnapshot(
    val path: String,
    val governor: String? = null,
    val minFreq: Long? = null,
    val maxFreq: Long? = null,
    val targetFreq: Long? = null,
    val targetGovernor: String? = null
)

data class GameBoostSnapshot(
    val version: Int = 1,
    val bootCount: Int? = null,
    val bootId: String? = null,
    val phase: String = "ENABLING",
    val cpu: List<CpuBoostSnapshot> = emptyList(),
    val gpu: GpuBoostSnapshot? = null,
    val storage: StorageBoostSnapshot? = null,
    val attempted: Set<GameBoostComponent> = emptySet(),
    val applied: Set<GameBoostComponent> = emptySet(),
    val thermallyReleased: Set<GameBoostComponent> = emptySet()
)

/** Components that may have been touched and therefore must be restored. */
fun GameBoostSnapshot.componentsNeedingRestore(): Set<GameBoostComponent> =
    (attempted + applied) - thermallyReleased
