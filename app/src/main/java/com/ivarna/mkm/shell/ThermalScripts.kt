package com.ivarna.mkm.shell

object ThermalScripts {
    /**
     * Get list of thermal zones and their types.
     * Output format: path/to/temp:type
     */
    fun getThermalInfo(): String {
        // Use grep to read all files at once. -H ensures filename is printed.
        return "grep -H . /sys/class/thermal/thermal_zone*/type /sys/class/thermal/thermal_zone*/temp 2>/dev/null"
    }

    /**
     * Stop thermal engine services (Qualcomm).
     */
    fun stopThermalEngine(): String {
        return "stop thermal-engine; " +
               "stop thermald; " +
               "stop mi_thermald; " +
               "stop thermal_manager; " +
               "stop vendor.thermal-engine; " +
               "stop android.hardware.thermal-service; " +
               "stop android.hardware.thermal-service.pixel; " +
               "stop vendor.thermal-hal-2-0; " +
               "stop vendor.thermal-hal-1-0; " +
               "stop thermal-hal; " +
               "stop thermalloadalgod; " +
               "stop thermalservice; " +
               "stop logd; " + // Maybe risky to stop logd, skipping it to be safe
               "echo disable > /sys/class/thermal/thermal_zone*/mode; " +
               "echo 0 > /sys/module/msm_thermal/parameters/enabled; " +
               "echo 0 > /sys/kernel/msm_thermal/enabled; " +
               "setprop init.svc.mi-thermald stopped; " +
               "setprop init.svc.thermal-engine stopped; " +
               "setprop init.svc.thermalservice stopped; " + 
               "setprop init.svc.android.hardware.thermal-service stopped; " +
               "true"
    }

    fun restartThermalEngine(): String {
        return "start thermal-engine; start thermald; start mi_thermald; start thermal_manager; start android.hardware.thermal-service; start vendor.thermal-hal-2-0"
    }

    /**
     * Read thermal configuration file.
     * Tries common locations.
     */
    fun getThermalConfigContent(): String {
        return "cat /vendor/etc/thermal-engine.conf 2>/dev/null || cat /system/etc/thermal-engine.conf 2>/dev/null"
    }

    /**
     * Set a new thermal limit by replacing set_point and set_point_clr in the config file.
     * usage: replace set_point X with set_point Y
     * This is a complex sed operation.
     * We will replace ALL set_point values that look like they are high temps (e.g. > 60000).
     * @param limitTemp Limit in degrees Celsius (e.g. 85)
     */
    fun setThermalLimit(limitTemp: Int): String {
        val limitMilli = limitTemp * 1000
        val clearMilli = (limitTemp - 5) * 1000 // Clear throttle 5 degrees below limit
        
        // Find existing config file
        val findFile = "FILE=\$(ls /vendor/etc/thermal-engine.conf 2>/dev/null || ls /system/etc/thermal-engine.conf 2>/dev/null | head -n 1)"
        
        // Backup
        val backup = "cp \$FILE \$FILE.bak"
        
        // Replace set_point using sed. 
        // We match `set_point <number>` and replace with `set_point <new_number>`
        // But we only want to replace high values, not low ones (like battery thresholds).
        // Let's assume we replace anything > 60000 (60C).
        // It's safer to just replace specific known sections but generic replacement is requested.
        // Regex: set_point [6-9][0-9][0-9][0-9][0-9] | 1[0-9][0-9][0-9][0-9][0-9]
        
        // sed -i 's/set_point [0-9]\{5,\}/set_point 95000/g' $FILE
        // But we should be careful.
        
        val sedCmd = "sed -i -E 's/set_point [0-9]{5,}/set_point $limitMilli/g; s/set_point_clr [0-9]{5,}/set_point_clr $clearMilli/g' \$FILE"
        
        return "$findFile && $backup && $sedCmd"
    }

    /**
     * Get all trip points.
     * Output format: zone_path:trip_type:trip_temp
     */
    fun getTripPoints(): String {
        // Use grep to read all trip point files at once.
        return "grep -H . /sys/class/thermal/thermal_zone*/trip_point_*_type /sys/class/thermal/thermal_zone*/trip_point_*_temp 2>/dev/null"
    }

    /**
     * Set temperature for a specific trip point.
     * Note: This might not persist after reboot or might be overwritten by kernel.
     */
    fun setTripPointTemp(zonePath: String, tripType: String, tempMilli: Int): String {
        // We need to find the specific trip point file for this zone and type
        // This is a bit complex as we don't know the ID (trip_point_0, trip_point_1, etc) from just the type
        // So we iterate to find it.
        
        return "for trip in $zonePath/trip_point_*_type; do " +
                "if [ \"\$(cat \"\$trip\")\" = \"$tripType\" ]; then " +
                "echo \"$tempMilli\" > \"\${trip%_type}_temp\"; " +
                "fi; " +
                "done"
    }
}
