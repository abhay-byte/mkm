package com.ivarna.mkm.data.model

enum class FpsSource {
    ADRENO_INFLIGHT,
    MALI_DMA_FENCE,
    FPS_MONITOR
}

data class FpsSample(
    val tMs: Long,
    val fps: Float,
    val frameMs: Float,
    val pkg: String,
    val pid: Int,
    val source: FpsSource,
    val events: Int = 0,
    val idle: Boolean = false
)

data class FpsSession(
    val startedAtMs: Long,
    val samples: List<FpsSample>,
    val platform: String,
    val endedAtMs: Long? = null
)
