package com.ivarna.mkm.shell

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/**
 * Manages Shizuku integration for elevated shell access without root.
 * Provides permission checking, requesting, and lifecycle management.
 */
object ShizukuManager {
    private val binderReceivedListeners = mutableListOf<Shizuku.OnBinderReceivedListener>()
    private val binderDeadListeners = mutableListOf<Shizuku.OnBinderDeadListener>()
    
    @Volatile
    private var initialized = false
    
    @Volatile
    private var appContext: Context? = null
    
    /**
     * Initialize Shizuku. Call this in Application.onCreate()
     * @return true if Shizuku binder is available
     */
    fun init(context: Context): Boolean {
        if (initialized) return isAvailable()
        
        appContext = context.applicationContext
        
        // Enable multi-process support if needed (disabled for single-process app)
        ShizukuProvider.enableMultiProcessSupport(false)
        
        initialized = true
        return isAvailable()
    }
    
    /**
     * Check if Shizuku app is installed on the device
     */
    fun isInstalled(): Boolean {
        val context = appContext ?: return false
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if Shizuku service is running (binder is alive)
     */
    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if Shizuku is available (app installed and running)
     * @deprecated Use isInstalled() and isRunning() for more granular checks
     */
    fun isAvailable(): Boolean {
        return isInstalled() && isRunning()
    }
    
    /**
     * Check if we have permission to use Shizuku
     */
    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false // Don't support old versions
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Request permission from user
     * Result will be delivered to OnRequestPermissionResultListener
     */
    fun requestPermission() {
        if (!isAvailable()) return
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }
    
    /**
     * Add listener for binder received events
     */
    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        binderReceivedListeners.add(listener)
        Shizuku.addBinderReceivedListener(listener)
    }
    
    /**
     * Remove binder received listener
     */
    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        binderReceivedListeners.remove(listener)
        Shizuku.removeBinderReceivedListener(listener)
    }
    
    /**
     * Add listener for binder dead events
     */
    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        binderDeadListeners.add(listener)
        Shizuku.addBinderDeadListener(listener)
    }
    
    /**
     * Remove binder dead listener
     */
    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        binderDeadListeners.remove(listener)
        Shizuku.removeBinderDeadListener(listener)
    }
    
    /**
     * Get Shizuku version
     */
    fun getVersion(): Int {
        return try {
            Shizuku.getVersion()
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Get Shizuku UID (0 for root, 2000 for adb)
     */
    fun getUid(): Int {
        return try {
            Shizuku.getUid()
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Check if Shizuku is running with root privileges
     */
    fun isRootMode(): Boolean {
        return getUid() == 0
    }
    
    /**
     * Check if Shizuku is running with ADB privileges
     */
    fun isAdbMode(): Boolean {
        val uid = getUid()
        return uid == 2000
    }
    
    /**
     * Get a human-readable description of the current Shizuku mode
     */
    fun getModeDescription(): String {
        return when (getUid()) {
            0 -> "Root mode"
            2000 -> "ADB mode"
            else -> "Unknown mode"
        }
    }
}

/**
 * Request code for Shizuku permission
 */
const val SHIZUKU_REQUEST_CODE = 1001
