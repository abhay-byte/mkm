package com.ivarna.mkm.utils

import java.io.File
import java.io.IOException
import java.util.Locale

object ShellUtils {
    fun readFile(path: String): String {
        return try {
            File(path).readText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun formatSize(kilobytes: Long): String {
        val bytes = kilobytes * 1024
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    fun formatFreq(khz: Long): String {
        return if (khz >= 1000000) {
            String.format(Locale.US, "%.2f GHz", khz / 1000000.0)
        } else {
            "${khz / 1000} MHz"
        }
    }
}
