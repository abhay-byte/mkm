package com.ivarna.mkm.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.service.OverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayScreen(onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    val prefs = remember { context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE) }
    
    var isOverlayEnabled by remember { 
        mutableStateOf(prefs.getBoolean("enabled", false)) 
    }

    fun notifyService() {
        if (isOverlayEnabled) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = "UPDATE_SETTINGS"
            }
            context.startService(intent)
        }
    }
    
    // Sync state with service life
    LaunchedEffect(Unit) {
        // Simple polling to see if enabled (in a real app we'd use a more robust way)
        isOverlayEnabled = prefs.getBoolean("enabled", false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                title = {
                    Column {
                        Text(
                            "Status Overlay",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Performance Monitor",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        val nextState = !isOverlayEnabled
                        isOverlayEnabled = nextState
                        prefs.edit().putBoolean("enabled", nextState).apply()
                        if (nextState) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(Intent(context, OverlayService::class.java))
                            } else {
                                context.startService(Intent(context, OverlayService::class.java))
                            }
                        } else {
                            context.stopService(Intent(context, OverlayService::class.java))
                        }
                    }
                },
                icon = { Icon(if (isOverlayEnabled) Icons.Default.Stop else Icons.Default.PlayArrow, null) },
                text = { Text(if (isOverlayEnabled) "Stop Overlay" else "Start Overlay") },
                containerColor = if (isOverlayEnabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape // Pill shape for expressive FAB
            )
        }
    ) { padding ->
        var showCpuUsage by remember { mutableStateOf(prefs.getBoolean("show_cpu_usage", true)) }
        var showGpuUsage by remember { mutableStateOf(prefs.getBoolean("show_gpu_usage", true)) }
        var showRamUsage by remember { mutableStateOf(prefs.getBoolean("show_ram_usage", true)) }
        var showSwapUsage by remember { mutableStateOf(prefs.getBoolean("show_swap_usage", true)) }
        var showCpuFreq by remember { mutableStateOf(prefs.getBoolean("show_cpu_freq", true)) }
        var isMovable by remember { mutableStateOf(prefs.getBoolean("movable", true)) }
        var attachPosition by remember { 
            mutableStateOf(prefs.getString("attach_position", "top_center") ?: "top_center") 
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                HeroUsageCard(
                    title = "OVERLAY STATUS",
                    usage = if (isOverlayEnabled) 1f else 0f,
                    mainValue = if (isOverlayEnabled) "ACTIVE" else "INACTIVE",
                    subValue = if (isOverlayEnabled) "Real-time monitoring enabled" else "Overlay service is stopped",
                    onClick = { /* No action needed */ },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsSection(title = "Visible Metrics") {
                    OverlayToggleItem(
                        icon = Icons.Default.DeveloperBoard,
                        title = "CPU Utilization",
                        checked = showCpuUsage,
                        onCheckedChange = { 
                            showCpuUsage = it
                            prefs.edit().putBoolean("show_cpu_usage", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.Timeline,
                        title = "CPU Frequency",
                        checked = showCpuFreq,
                        onCheckedChange = { 
                            showCpuFreq = it
                            prefs.edit().putBoolean("show_cpu_freq", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.VideogameAsset,
                        title = "GPU Utilization",
                        checked = showGpuUsage,
                        onCheckedChange = { 
                            showGpuUsage = it
                            prefs.edit().putBoolean("show_gpu_usage", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.Memory,
                        title = "RAM Usage",
                        checked = showRamUsage,
                        onCheckedChange = { 
                            showRamUsage = it
                            prefs.edit().putBoolean("show_ram_usage", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.SwapCalls,
                        title = "Swap Usage",
                        checked = showSwapUsage,
                        onCheckedChange = { 
                            showSwapUsage = it
                            prefs.edit().putBoolean("show_swap_usage", it).apply()
                            notifyService()
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "Overlay Behavior") {
                    OverlayToggleItem(
                        icon = Icons.Default.OpenWith,
                        title = "Movable Overlay",
                        checked = isMovable,
                        onCheckedChange = { 
                            isMovable = it
                            prefs.edit().putBoolean("movable", it).apply()
                            notifyService()
                        }
                    )
                    
                    if (!isMovable) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            "Attach Position",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val posConfig = listOf(
                                Triple("top_left", Alignment.TopStart, Icons.Default.KeyboardArrowUp),
                                Triple("top_center", Alignment.TopCenter, Icons.Default.KeyboardArrowUp),
                                Triple("top_right", Alignment.TopEnd, Icons.Default.KeyboardArrowUp),
                                Triple("left_center", Alignment.CenterStart, Icons.AutoMirrored.Filled.KeyboardArrowLeft),
                                Triple("right_center", Alignment.CenterEnd, Icons.AutoMirrored.Filled.KeyboardArrowRight),
                                Triple("bottom_left", Alignment.BottomStart, Icons.Default.KeyboardArrowDown),
                                Triple("bottom_center", Alignment.BottomCenter, Icons.Default.KeyboardArrowDown),
                                Triple("bottom_right", Alignment.BottomEnd, Icons.Default.KeyboardArrowDown)
                            )
                            
                            posConfig.forEach { (pos, alignment, icon) ->
                                val isSelected = attachPosition == pos
                                Box(
                                    modifier = Modifier
                                        .align(alignment)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        .clickable {
                                            attachPosition = pos
                                            prefs.edit().putString("attach_position", pos).apply()
                                            notifyService()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Text(
                                "SCREEN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            // ... truncated
            
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "The overlay will appear as a small floating window on top of other apps.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItem(
        icon = icon,
        title = title,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}
