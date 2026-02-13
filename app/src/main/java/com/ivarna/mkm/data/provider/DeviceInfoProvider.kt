package com.ivarna.mkm.data.provider

import android.os.Build
import com.ivarna.mkm.data.model.SystemOverview
import com.ivarna.mkm.shell.ShizukuManager
import com.ivarna.mkm.utils.ShellUtils
import com.topjohnwu.superuser.Shell

object DeviceInfoProvider {
    fun getOverview(): SystemOverview {
        val rawKernel = ShellUtils.readFile("/proc/version")
        val kernelVersion = if (rawKernel.isNotEmpty()) {
            // Usually: Linux version 6.1.68-android14-11 ...
            rawKernel.split(" ").getOrNull(2)?.split("-")?.getOrNull(0) ?: "Unknown"
        } else {
            System.getProperty("os.version")?.split("-")?.getOrNull(0) ?: "Unknown"
        }

        val isRoot = Shell.getShell().isRoot
        val isShizukuActive = ShizukuManager.isAvailable() && ShizukuManager.hasPermission()

        return SystemOverview(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            kernelVersion = kernelVersion,
            isShizukuActive = isShizukuActive,
            isRootActive = isRoot
        )
    }
}
