package com.ivarna.mkm.shell

// Commented out - Shizuku dependencies not available
// import android.content.pm.PackageManager
// import rikka.shizuku.Shizuku
// import rikka.shizuku.ShizukuProvider

object ShizukuHelper {
    const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean {
        // Shizuku support disabled - uncomment when dependencies are available
        return false
        /* return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        } */
    }

    fun hasPermission(): Boolean {
        // Shizuku support disabled
        return false
        /* if (Shizuku.isPreV11()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED */
    }

    fun requestPermission() {
        // Shizuku support disabled
        // Shizuku.requestPermission(REQUEST_CODE)
    }

    fun addBinderReceivedListener(listener: Any) {
        // Shizuku support disabled
        // Shizuku.addBinderReceivedListener(listener)
    }

    fun removeBinderReceivedListener(listener: Any) {
        // Shizuku support disabled
        // Shizuku.removeBinderReceivedListener(listener)
    }

    fun addBinderDeadListener(listener: Any) {
        // Shizuku support disabled
        // Shizuku.addBinderDeadListener(listener)
    }

    fun removeBinderDeadListener(listener: Any) {
        // Shizuku support disabled
        // Shizuku.removeBinderDeadListener(listener)
    }
}
