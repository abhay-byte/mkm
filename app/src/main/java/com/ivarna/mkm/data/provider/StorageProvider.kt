package com.ivarna.mkm.data.provider

import android.os.Environment
import android.os.StatFs
import com.ivarna.mkm.data.model.StoragePartition
import com.ivarna.mkm.data.model.StorageStatus
import com.ivarna.mkm.utils.FormatUtils

object StorageProvider {

    fun getStorageStatus(): StorageStatus {
        val partitions = mutableListOf<StoragePartition>()
        
        // Data Partition
        val dataPath = Environment.getDataDirectory()
        partitions.add(getPartitionInfo("/data", dataPath.absolutePath))
        
        // Root/System Partition
        val rootPath = Environment.getRootDirectory()
        partitions.add(getPartitionInfo("/system", rootPath.absolutePath))

        // Get UFS Status
        val ufsStatus = UfsProvider.getUfsStatus()
        
        val type = if (ufsStatus.isSupported) "UFS" else "eMMC/Other" // Simplified detection

        return StorageStatus(
            partitions = partitions,
            type = type,
            ufsStatus = ufsStatus
        )
    }

    private fun getPartitionInfo(label: String, path: String): StoragePartition {
        try {
            val stats = StatFs(path)
            val blockSize = stats.blockSizeLong
            val totalBlocks = stats.blockCountLong
            val availableBlocks = stats.availableBlocksLong
            val usedBlocks = totalBlocks - availableBlocks

            val total = totalBlocks * blockSize
            val used = usedBlocks * blockSize
            val free = availableBlocks * blockSize
            
            val totalUi = FormatUtils.formatBytes(total)
            val usedUi = FormatUtils.formatBytes(used)
            val freeUi = FormatUtils.formatBytes(free)
            
            val percent = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f

            return StoragePartition(
                mountPoint = label,
                total = totalUi,
                used = usedUi,
                free = freeUi,
                usagePercent = percent,
                rawTotal = total,
                rawUsed = used,
                blockSize = blockSize
            )
        } catch (e: Exception) {
            return StoragePartition(
                mountPoint = label,
                total = "0 B",
                used = "0 B",
                free = "0 B",
                usagePercent = 0f,
                rawTotal = 0,
                rawUsed = 0,
                blockSize = 0
            )
        }
    }
}
