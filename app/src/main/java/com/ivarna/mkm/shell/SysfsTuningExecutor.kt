package com.ivarna.mkm.shell

import com.ivarna.mkm.utils.ShellUtils

/** Validated sysfs read/write facade for CPU and GPU tuning. */
object SysfsTuningExecutor {
    enum class SysfsAccess {
        READ_WRITE,
        READ_ONLY,
        UNAVAILABLE,
        UNKNOWN
    }

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

    /** Probes the exact node through the backend that would perform tuning. */
    fun access(path: String): SysfsAccess {
        if (!isSafeSysfsPath(path)) return SysfsAccess.UNAVAILABLE
        val hasElevated = ShellManager.hasRoot() || ShellManager.hasShizuku()
        if (hasElevated) {
            val result = ShellManager.exec(
                "if ! test -e \"$path\"; then printf '%s' UNAVAILABLE; " +
                    "elif ! test -r \"$path\"; then printf '%s' UNAVAILABLE; " +
                    "elif test -w \"$path\"; then printf '%s' READ_WRITE; " +
                    "else printf '%s' READ_ONLY; fi",
                ShellManager.PrivilegeRequirement.ELEVATED_SYSFS
            )
            return classifyAccessProbe(result.exitCode, result.stdout)
        }

        val file = java.io.File(path)
        return when {
            !file.exists() || !file.canRead() -> SysfsAccess.UNAVAILABLE
            file.canWrite() -> SysfsAccess.READ_WRITE
            else -> SysfsAccess.READ_ONLY
        }
    }

    fun canWrite(path: String): Boolean = access(path) == SysfsAccess.READ_WRITE

    fun accessReason(access: SysfsAccess): String? = when (access) {
        SysfsAccess.READ_WRITE -> null
        SysfsAccess.READ_ONLY -> "Kernel exposes this value as read-only"
        SysfsAccess.UNAVAILABLE -> if (ShellManager.hasRoot() || ShellManager.hasShizuku()) {
            "Kernel node is unavailable or unreadable"
        } else {
            "Requires root or Shizuku access"
        }
        SysfsAccess.UNKNOWN -> "Unable to determine node writability"
    }

    fun classifyAccessProbe(exitCode: Int, output: String): SysfsAccess {
        if (exitCode == -1 && output.isBlank()) return SysfsAccess.UNKNOWN
        return when (output.trim()) {
            "READ_WRITE" -> SysfsAccess.READ_WRITE
            "READ_ONLY" -> SysfsAccess.READ_ONLY
            "UNAVAILABLE" -> SysfsAccess.UNAVAILABLE
            else -> SysfsAccess.UNKNOWN
        }
    }

    fun isSafeSysfsPath(path: String): Boolean =
        path.startsWith("/sys/") && path.none {
            it == '\n' || it == '\r' || it == ';' || it == '|' || it == '`' ||
                it == '$' || it == '"' || it == '\\'
        }

    fun isSafeValue(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_.:+-]+"))

    private fun quote(value: String): String = value.replace("'", "'\\''")
}
