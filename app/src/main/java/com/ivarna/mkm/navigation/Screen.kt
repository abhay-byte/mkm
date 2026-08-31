package com.ivarna.mkm.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val labelResId: Int, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Home : Screen("home", com.ivarna.mkm.R.string.home, Icons.Filled.Home, Icons.Outlined.Home)
    object RAM : Screen("ram", com.ivarna.mkm.R.string.ram, Icons.Filled.Memory, Icons.Outlined.Memory)
    object CPU : Screen("cpu", com.ivarna.mkm.R.string.cpu, Icons.Filled.DeveloperBoard, Icons.Outlined.DeveloperBoard)
    object GPU : Screen("gpu", com.ivarna.mkm.R.string.gpu, Icons.Filled.VideogameAsset, Icons.Outlined.VideogameAsset)
    object GameBoost : Screen("game_boost", com.ivarna.mkm.R.string.game_boost, Icons.Filled.VideogameAsset, Icons.Outlined.VideogameAsset)
    object Storage : Screen("storage", com.ivarna.mkm.R.string.storage, Icons.Filled.SdStorage, Icons.Outlined.SdStorage)
    object Power : Screen("power", com.ivarna.mkm.R.string.power, Icons.Filled.Bolt, Icons.Outlined.Bolt)
    object Battery : Screen("battery", com.ivarna.mkm.R.string.battery, Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull)
    object BatteryHistory : Screen("battery_history", com.ivarna.mkm.R.string.session_history, Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull)
    object NotificationSettings : Screen("notif_settings", com.ivarna.mkm.R.string.notification_settings, Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull)
    object ChargingNotification : Screen("notif_charging", com.ivarna.mkm.R.string.charging_notification, Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull)
    object DischargingNotification : Screen("notif_discharging", com.ivarna.mkm.R.string.discharging_notification, Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull)
    object MonitoringNotification : Screen("notif_monitoring", com.ivarna.mkm.R.string.monitoring, Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull)
    object Overlay : Screen("overlay", com.ivarna.mkm.R.string.overlay, Icons.Filled.Layers, Icons.Outlined.Layers)
    object Settings : Screen("settings", com.ivarna.mkm.R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

val navItems = listOf(
    Screen.Home,
    Screen.RAM,
    Screen.CPU,
    Screen.GPU,
    Screen.GameBoost,
    Screen.Storage,
    Screen.Power,
    Screen.Battery,
    Screen.Overlay,
    Screen.Settings
)
