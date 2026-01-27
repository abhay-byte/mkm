package com.ivarna.mkm.utils

import java.util.Locale

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"
        if (exp > pre.length) return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, pre.length.toDouble()), pre.last())
        val suffix = pre[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), suffix)
    }
}
