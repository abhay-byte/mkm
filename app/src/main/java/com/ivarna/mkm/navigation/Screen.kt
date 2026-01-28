package com.ivarna.mkm.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object RAM : Screen("ram", "RAM", Icons.Filled.Memory, Icons.Outlined.Memory)
    object CPU : Screen("cpu", "CPU", Icons.Filled.DeveloperBoard, Icons.Outlined.DeveloperBoard)
    object GPU : Screen("gpu", "GPU", Icons.Filled.VideogameAsset, Icons.Outlined.VideogameAsset)
    object Storage : Screen("storage", "Storage", Icons.Filled.SdStorage, Icons.Outlined.SdStorage)
    object Power : Screen("power", "Power", Icons.Filled.Bolt, Icons.Outlined.Bolt)
    object Overlay : Screen("overlay", "Overlay", Icons.Filled.Layers, Icons.Outlined.Layers)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val navItems = listOf(
    Screen.Home,
    Screen.RAM,
    Screen.CPU,
    Screen.GPU,
    Screen.Storage,
    Screen.Power,
    Screen.Overlay,
    Screen.Settings
)
