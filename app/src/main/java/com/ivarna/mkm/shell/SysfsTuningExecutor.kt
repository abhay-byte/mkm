package com.ivarna.mkm.shell

import com.ivarna.mkm.utils.ShellUtils

/** Validated sysfs read/write facade for CPU and GPU tuning. */
object SysfsTuningExecutor {
    fun read(path: String): String {
        if (!isSafeSysfsPath(path)) return ""
        ShellUtils.readFile(path).takeIf { it.isNotBlank() }?.let { return it.trim() }
        return ShellManager.exec("cat \"$path\"", ShellManager.PrivilegeRequirement.ELEVATED_SYSFS).stdout.trim()
    }

    fun write(path: String, value: String): ShellManager.CommandResult {
        if (!isSafeSysfsPath(path) || !isSafeValue(value)) {
            return ShellManager.CommandResult(-1, "", "Rejected unsafe sysfs path or value")
        }
        return ShellManager.exec(
            "printf '%s' '${quote(value)}' > \"$path\"",
            ShellManager.PrivilegeRequirement.ELEVATED_SYSFS
        )
    }

    fun readLong(path: String): Long? = read(path).toLongOrNull()?.takeIf { it > 0L }

    fun exists(path: String): Boolean {
        if (!isSafeSysfsPath(path)) return false
        if (java.io.File(path).exists()) return true
        return ShellManager.exec("test -e \"$path\"", ShellManager.PrivilegeRequirement.ELEVATED_SYSFS).isSuccess
    }

    /** True when the node exists and either the app or an elevated backend can write it. */
    fun canWrite(path: String): Boolean {
        if (!exists(path)) return false
        val file = java.io.File(path)
        return file.canWrite() || ShellManager.hasRoot() || ShellManager.hasShizuku()
    }

    fun isSafeSysfsPath(path: String): Boolean =
        path.startsWith("/sys/") && path.none {
            it == '\n' || it == '\r' || it == ';' || it == '|' || it == '`' ||
                it == '$' || it == '"' || it == '\\'
        }

    fun isSafeValue(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_.:+-]+"))

    private fun quote(value: String): String = value.replace("'", "'\\''")
}
