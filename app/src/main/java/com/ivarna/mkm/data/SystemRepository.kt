package com.ivarna.mkm.data

import android.content.Context
import com.ivarna.mkm.data.model.*
import com.ivarna.mkm.data.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemRepository(context: Context? = null) {
    private val powerProvider = PowerProvider(context)

    suspend fun getHomeData(powerMultiplier: Float = 1.0f): HomeData = withContext(Dispatchers.IO) {
        val thermalStatus = ThermalProvider.getThermalStatus()
        HomeData(
            overview = DeviceInfoProvider.getOverview(),
            memory = MemoryProvider.getMemoryStatus(),
            cpu = CpuProvider.getCpuStatus(),
            gpu = GpuProvider.getGpuStatus(),
            swap = MemoryProvider.getSwapStatus(),
            power = powerProvider.getPowerStatus(powerMultiplier),
            cpuTemp = thermalStatus.cpuTemp,
            batteryTemp = thermalStatus.batteryTemp
        )
    }

    suspend fun getRamData(): RamData = withContext(Dispatchers.IO) {
        RamData(
            memory = MemoryProvider.getMemoryStatus(),
            swap = MemoryProvider.getSwapStatus(),
            ufs = UfsProvider.getUfsStatus(),
            devfreq = DevfreqProvider.getDevfreqStatus()
        )
    }

    suspend fun getStorageStatus(): StorageStatus = withContext(Dispatchers.IO) {
        StorageProvider.getStorageStatus()
    }
}

data class HomeData(
    val overview: SystemOverview,
    val memory: MemoryStatus,
    val cpu: CpuStatus,
    val gpu: GpuStatus,
    val swap: SwapStatus,
    val power: PowerStatus = PowerStatus(),
    val cpuTemp: Float = 0f,
    val batteryTemp: Float = 0f
)

data class RamData(
    val memory: MemoryStatus,
    val swap: SwapStatus,
    val ufs: UfsStatus = UfsStatus(),
    val devfreq: DevfreqStatus = DevfreqStatus()
)
