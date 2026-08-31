package com.ivarna.mkm.data.provider

import android.os.BatteryManager
import org.junit.Assert.*
import org.junit.Test

class BatteryChargingTest {

    @Test
    fun testNullIntentReturnsFalse() {
        assertFalse(BatteryCharging.readAndroidCharging(null))
    }

    @Test
    fun testChargingStatusEvaluatesTrue() {
        // BATTERY_STATUS_CHARGING = 2
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_CHARGING, 0))
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_WIRELESS))
    }

    @Test
    fun testPluggedNonZeroEvaluatesTrueEvenIfNotExplicitChargingStatus() {
        // If status is DISCHARGING (3) or NOT_CHARGING (4) or FULL (5) or UNKNOWN (1), but plugged != 0 -> true (H1 contract)
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_AC))
        assertTrue(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_UNKNOWN, BatteryManager.BATTERY_PLUGGED_WIRELESS))
    }

    @Test
    fun testUnpluggedAndNotChargingEvaluatesFalse() {
        assertFalse(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_DISCHARGING, 0))
        assertFalse(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 0))
        assertFalse(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_FULL, 0))
        assertFalse(BatteryCharging.isCharging(BatteryManager.BATTERY_STATUS_UNKNOWN, 0))
    }
}
