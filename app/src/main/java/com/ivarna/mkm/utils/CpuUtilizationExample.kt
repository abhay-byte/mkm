package com.ivarna.mkm.utils

import android.util.Log
import com.ivarna.mkm.data.provider.CpuUtilizationProvider
import kotlinx.coroutines.delay

/**
 * Example usage of the CpuUtilizationProvider component.
 * 
 * This demonstrates how to use the modular CPU utilization component
 * to get CPU usage information using /proc/stat (with frequency-based fallback).
 */
object CpuUtilizationExample {
    private const val TAG = "CpuUtilizationExample"
    
    /**
     * Example: Get overall CPU usage with automatic fallback.
     */
    suspend fun monitorOverallCpuUsage() {
        Log.d(TAG, "Starting overall CPU usage monitoring...")
        
        // First call initializes the baseline
        CpuUtilizationProvider.getOverallCpuUsage()
        delay(500) // Wait for some CPU activity
        
        // Subsequent calls will return actual usage
        repeat(5) {
            val usage = CpuUtilizationProvider.getOverallCpuUsage()
            Log.d(TAG, "Overall CPU Usage: ${(usage * 100).toInt()}%")
            delay(1000)
        }
    }
    
    /**
     * Example: Get per-core CPU usage with automatic fallback.
     */
    suspend fun monitorPerCoreCpuUsage() {
        Log.d(TAG, "Starting per-core CPU usage monitoring...")
        
        // First call initializes the baseline
        CpuUtilizationProvider.getPerCoreCpuUsage()
        delay(500)
        
        // Subsequent calls will return actual usage
        repeat(5) {
            val perCoreUsage = CpuUtilizationProvider.getPerCoreCpuUsage()
            perCoreUsage.forEach { (coreId, usage) ->
                Log.d(TAG, "Core $coreId: ${(usage * 100).toInt()}%")
            }
            delay(1000)
        }
    }
    
    /**
     * Example: Force using /proc/stat method only (no frequency fallback).
     */
    suspend fun monitorUsingProcStatOnly() {
        Log.d(TAG, "Monitoring CPU using /proc/stat only...")
        
        CpuUtilizationProvider.reset() // Reset cache
        CpuUtilizationProvider.getOverallCpuUsage(useFrequencyFallback = false)
        delay(500)
        
        repeat(5) {
            val usage = CpuUtilizationProvider.getOverallCpuUsage(useFrequencyFallback = false)
            if (usage == 0f) {
                Log.d(TAG, "/proc/stat not available (no root?) - usage: 0%")
            } else {
                Log.d(TAG, "CPU Usage (from /proc/stat): ${(usage * 100).toInt()}%")
            }
            delay(1000)
        }
    }
    
    /**
     * Example: Reset the cache to get fresh baseline.
     */
    fun resetCache() {
        Log.d(TAG, "Resetting CPU utilization cache...")
        CpuUtilizationProvider.reset()
    }
}
