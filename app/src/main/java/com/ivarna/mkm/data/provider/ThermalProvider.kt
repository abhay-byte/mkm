package com.ivarna.mkm.data.provider

import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.shell.ThermalScripts
import com.ivarna.mkm.utils.AppLogger
import com.ivarna.mkm.utils.ShellUtils

data class ThermalZone(
    val id: String,
    val type: String,
    val temp: Float
)

data class ThermalStatus(
    val zones: List<ThermalZone>,
    val maxTemp: Float,
    val currentLimit: Int = 0
)

object ThermalProvider {
    
    fun getThermalStatus(fetchLimit: Boolean = false): ThermalStatus {
        val result = ShellManager.exec(ThermalScripts.getThermalInfo())
        val zones = mutableListOf<ThermalZone>()
        val zoneMap = mutableMapOf<String, Pair<String, Float>>() // Id -> (Type, Temp)

        if (result.isSuccess) {
            result.stdout.lines().forEach { line ->
                // Grep format: /sys/.../thermal_zoneX/file:value
                // e.g. /sys/class/thermal/thermal_zone0/temp:54000
                // e.g. /sys/class/thermal/thermal_zone0/type:soc_max
                try {
                    val parts = line.split(":")
                    if (parts.size >= 2) {
                        val path = parts[0]
                        val value = parts[1].trim()
                        val filename = path.substringAfterLast("/")
                        val zoneId = path.substringBeforeLast("/").substringAfterLast("/")
                        
                        var current = zoneMap[zoneId] ?: ("" to 0f)
                        
                        if (filename == "type") {
                            current = current.copy(first = value)
                        } else if (filename == "temp") {
                            val temp = (value.toFloatOrNull() ?: 0f) / 1000f
                            current = current.copy(second = temp)
                        }
                        zoneMap[zoneId] = current
                    }
                } catch (e: Exception) { }
            }
            
            zoneMap.forEach { (id, data) ->
                val (type, temp) = data
                if (type.isNotEmpty() && temp > 0 && !type.contains("battery", ignoreCase = true)) {
                    zones.add(ThermalZone(id, type, temp))
                }
            }
        }
        
        return ThermalStatus(
            zones = zones.sortedByDescending { it.temp },
            maxTemp = zones.maxOfOrNull { it.temp } ?: 0f,
            currentLimit = if (fetchLimit) parseThermalLimit() else 0
        )
    }

    fun getThermalLimit(): Int {
        return parseThermalLimit()
    }

    private fun parseThermalLimit(): Int {
        // 1. Try legacy/Qualcomm config first
        val configContent = ShellManager.exec(ThermalScripts.getThermalConfigContent()).stdout
        val regex = Regex("set_point\\s+(\\d+)")
        val matches = regex.findAll(configContent)
        val configPoints = matches.map { it.groupValues[1].toInt() / 1000 }.filter { it > 60 }.toList()
        
        if (configPoints.isNotEmpty()) {
            return configPoints.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
        }

        // 2. Fallback to kernel trip points
        val tripResult = ShellManager.exec(ThermalScripts.getTripPoints())
        if (tripResult.isSuccess) {
            val tripMap = mutableMapOf<String, Pair<String, Int>>() // Key (zone+tripId) -> (Type, Temp)
            
            tripResult.stdout.lines().forEach { line ->
                // format: /sys/.../trip_point_0_type:passive Or /sys/.../trip_point_0_temp:85000
                // NOTE: getTripPoints() uses grep -H, so lines are: filename:content
                try {
                    val parts = line.split(":")
                    if (parts.size >= 2) {
                        val path = parts[0]
                        val value = parts[1].trim()
                        
                        // Extract base path (zone + trip id). 
                        // Path: /sys/class/thermal/thermal_zone0/trip_point_0_type
                        val basePath = if (path.endsWith("_type")) path.removeSuffix("_type") else path.removeSuffix("_temp")
                        val isType = path.endsWith("_type")
                        
                        var current = tripMap[basePath] ?: ("" to 0)
                        
                        if (isType) {
                            current = current.copy(first = value)
                        } else {
                            current = current.copy(second = value.toIntOrNull() ?: 0)
                        }
                        tripMap[basePath] = current
                    }
                } catch (e: Exception) { }
            }
            
            // Filter for valid limits
            val limits = tripMap.values.filter { (type, temp) ->
                // temp in millidegrees (e.g., 85000). Range 40C to 100C.
                // We loosen the type check to include generic ones if needed, but passive/active is standard.
                // Log what we found
                val valid = (type == "passive" || type == "active" || type == "hot" || type == "critical") && temp > 40000 && temp < 105000
                valid
            }.map { it.second / 1000 }
            
            if (limits.isNotEmpty()) {
                val minLimit = limits.minOrNull() ?: 0
                return minLimit
            } else {
                 // Log a sample if we found things but nothing valid
                if (tripMap.isNotEmpty()) {
                    val sample = tripMap.entries.take(5).joinToString { "${it.key}=${it.value.first}:${it.value.second}" }
                     AppLogger.log("Parsed 0 valid limits. Sample trips: $sample")
                } else {
                     // AppLogger.log("No trip points parsed at all. Grep output lines: ${tripResult.stdout.lines().size}")
                }
            }
        }
        
        return 0
    }

    fun setThermalLimit(limit: Int): Boolean {
        var success = false
        AppLogger.log("Setting limit to $limit°C")
        
        // 1. Try Config
        val configCmd = ThermalScripts.setThermalLimit(limit)
        val configResult = ShellManager.exec(configCmd)
        if (configResult.isSuccess) {
            AppLogger.log("Config update success. Restarting service...")
            ShellManager.exec(ThermalScripts.restartThermalEngine())
            success = true
        } else {
            AppLogger.log("Config update failed: ${configResult.stderr}")
        }
        
        // 2. Try Trip Points (Kernel)
        val tripResult = ShellManager.exec(ThermalScripts.getTripPoints())
        if (tripResult.isSuccess) {
            val pathsToUpdate = mutableListOf<String>()
             
             tripResult.stdout.lines().forEach { line ->
                 // format: /sys/.../trip_point_0_type:passive:85000 (from grep -H)
                 // or just /sys/... : ... if we are using grep output
                 
                 // Wait, getTripPoints uses grep -H now. Output is filename:content
                 // /sys/class/thermal/thermal_zone0/trip_point_0_type:passive
                 
                 try {
                     if (line.endsWith("passive") || line.endsWith("active")) {
                         val parts = line.split(":")
                         if (parts.size >= 2) {
                             val typePath = parts[0]
                             val tempPath = typePath.replace("_type", "_temp")
                             pathsToUpdate.add(tempPath)
                         }
                     }
                 } catch (e: Exception) { }
             }
             
             if (pathsToUpdate.isNotEmpty()) {
                 AppLogger.log("Found ${pathsToUpdate.size} trip points to update.")
                 pathsToUpdate.forEach { path ->
                    val cmd = "echo ${limit * 1000} > $path"
                    val res = ShellManager.exec(cmd)
                    if (res.isSuccess) {
                        success = true
                        AppLogger.log("Updated trip point: $path")
                    } else {
                        AppLogger.log("Failed to update $path: ${res.stderr}")
                    }
                 }
             } else {
                 AppLogger.log("No passive/active trip points found.")
             }
        } else {
            AppLogger.log("Failed to scan trip points: ${tripResult.stderr}")
        }
        
        if (success) AppLogger.log("Limit set successfully (at least partially).")
        else AppLogger.log("Failed to set limit.")
        
        return success
    }



    fun disableThrottling(): Boolean {
        AppLogger.log("Disabling throttling services...")
        val res = ShellManager.exec(ThermalScripts.stopThermalEngine())
        if (res.isSuccess) AppLogger.log("Services stopped successfully.")
        else AppLogger.log("Failed to stop services: ${res.stderr}")
        return res.isSuccess
    }
}
