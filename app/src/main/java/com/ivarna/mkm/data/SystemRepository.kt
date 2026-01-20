package com.ivarna.mkm.data

import com.ivarna.mkm.data.model.*
import com.ivarna.mkm.data.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemRepository {
    suspend fun getHomeData(): HomeData = withContext(Dispatchers.IO) {
        HomeData(
            overview = DeviceInfoProvider.getOverview(),
            memory = MemoryProvider.getMemoryStatus(),
            cpu = CpuProvider.getCpuStatus(),
            gpu = GpuProvider.getGpuStatus(),
            swap = MemoryProvider.getSwapStatus()
        )
    }
}

data class HomeData(
    val overview: SystemOverview,
    val memory: MemoryStatus,
    val cpu: CpuStatus,
    val gpu: GpuStatus,
    val swap: SwapStatus
)
