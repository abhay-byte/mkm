package com.ivarna.mkm.tuning

import com.ivarna.mkm.service.KernelTuningCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class KernelTuningCoordinatorTest {
    @Test
    fun serializesConcurrentKernelMutations() = runBlocking {
        val inMutation = AtomicBoolean(false)
        val overlapped = AtomicBoolean(false)

        listOf(1, 2).map {
            async(Dispatchers.Default) {
                KernelTuningCoordinator.withMutation {
                    if (!inMutation.compareAndSet(false, true)) overlapped.set(true)
                    delay(20)
                    inMutation.set(false)
                }
            }
        }.awaitAll()

        assertFalse(overlapped.get())
    }
}
