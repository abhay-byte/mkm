package com.ivarna.mkm.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide lock for every CPU/GPU/thermal mutation touching shared kernel state. */
object KernelTuningCoordinator {
    private val mutationMutex = Mutex()

    suspend fun <T> withMutation(block: suspend () -> T): T = mutationMutex.withLock { block() }
}
