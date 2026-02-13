package com.ivarna.mkm.shell

// Shizuku support disabled for v1.0 - This is a root-only release
// Shizuku will be properly integrated in v1.1 with the correct API
// import android.content.pm.PackageManager
// import rikka.shizuku.Shizuku
// import rikka.shizuku.ShizukuProvider

object ShizukuHelper {
    const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean {
        // Shizuku support disabled for v1.0
        return false
    }

    fun hasPermission(): Boolean {
        // Shizuku support disabled for v1.0
        return false
    }

    fun requestPermission() {
        // Shizuku support disabled for v1.0
    }

    fun addBinderReceivedListener(listener: Any) {
        // Shizuku support disabled for v1.0
    }

    fun removeBinderReceivedListener(listener: Any) {
        // Shizuku support disabled for v1.0
    }

    fun addBinderDeadListener(listener: Any) {
        // Shizuku support disabled for v1.0
    }

    fun removeBinderDeadListener(listener: Any) {
        // Shizuku support disabled for v1.0
    }
}
