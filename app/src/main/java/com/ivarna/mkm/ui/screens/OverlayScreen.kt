package com.ivarna.mkm.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.zIndex
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.service.OverlayService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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
        var showPower by remember { mutableStateOf(prefs.getBoolean("show_power", true)) }
        var showCpuFreq by remember { mutableStateOf(prefs.getBoolean("show_cpu_freq", true)) }
        var showCpuTemp by remember { mutableStateOf(prefs.getBoolean("show_cpu_temp", false)) }
        var showBatteryTemp by remember { mutableStateOf(prefs.getBoolean("show_battery_temp", false)) }
        var showBatteryPercent by remember { mutableStateOf(prefs.getBoolean("show_battery_percent", false)) }
        var showProgressBars by remember { mutableStateOf(prefs.getBoolean("show_progress_bars", true)) }
        var showIconsOnly by remember { mutableStateOf(prefs.getBoolean("show_icons_only", false)) }
        var isGridView by remember { mutableStateOf(prefs.getBoolean("is_grid_view", false)) }
        var isHorizontal by remember { mutableStateOf(prefs.getBoolean("is_horizontal", false)) }
        var showSparklines by remember { mutableStateOf(prefs.getBoolean("show_sparklines", false)) }
        val defaultOrder = "cpu_usage,cpu_freq,gpu_usage,ram_usage,swap_usage,power_usage,cpu_temp,battery_temp,battery_percent"
        var componentOrder by remember { 
            mutableStateOf((prefs.getString("component_order", defaultOrder) ?: defaultOrder).split(",")) 
        }
        var gridColumns by remember { mutableStateOf(prefs.getInt("grid_columns", 2)) }
        var updateInterval by remember { mutableStateOf(prefs.getLong("update_interval", 2000L)) }
        var isMovable by remember { mutableStateOf(prefs.getBoolean("movable", true)) }
        var overlayOpacity by remember { mutableStateOf(prefs.getFloat("overlay_opacity", 0.9f)) }
        var accentColorIndex by remember { mutableStateOf(prefs.getInt("accent_color_index", 0)) }
        var attachPosition by remember { 
            mutableStateOf(prefs.getString("attach_position", "top_center") ?: "top_center") 
        }

        val accentColors = listOf(
            MaterialTheme.colorScheme.primary,
            Color(0xFF4CAF50), // Green
            Color(0xFF2196F3), // Blue
            Color(0xFFF44336), // Red
            Color(0xFFFFEB3B), // Yellow
            Color(0xFFE91E63), // Pink
            Color(0xFF9C27B0), // Purple
            Color(0xFF00BCD4)  // Cyan
        )

        val haptic = LocalHapticFeedback.current
        val lazyListState = rememberLazyListState()
        
        var draggedItemKey by remember { mutableStateOf<String?>(null) }
        var draggingOffset by remember { mutableStateOf(0f) }
        var lastSwappedIndex by remember { mutableStateOf<Int?>(null) }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    OverlayToggleItem(
                        icon = Icons.Default.FlashOn,
                        title = "Power Usage",
                        checked = showPower,
                        onCheckedChange = { 
                            showPower = it
                            prefs.edit().putBoolean("show_power", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.Thermostat,
                        title = "CPU Temperature",
                        checked = showCpuTemp,
                        onCheckedChange = { 
                            showCpuTemp = it
                            prefs.edit().putBoolean("show_cpu_temp", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Battery Temperature",
                        checked = showBatteryTemp,
                        onCheckedChange = { 
                            showBatteryTemp = it
                            prefs.edit().putBoolean("show_battery_temp", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.BatteryStd,
                        title = "Battery Percentage",
                        checked = showBatteryPercent,
                        onCheckedChange = { 
                            showBatteryPercent = it
                            prefs.edit().putBoolean("show_battery_percent", it).apply()
                            notifyService()
                        }
                    )
                }
            }

            item {
                Text(
                    "Component Order",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            val metricLabels = mapOf(
                "cpu_usage" to ("CPU Utilization" to Icons.Default.DeveloperBoard),
                "cpu_freq" to ("CPU Frequency" to Icons.Default.Timeline),
                "gpu_usage" to ("GPU Utilization" to Icons.Default.VideogameAsset),
                "ram_usage" to ("RAM Usage" to Icons.Default.Memory),
                "swap_usage" to ("Swap Usage" to Icons.Default.SwapCalls),
                "power_usage" to ("Power Usage" to Icons.Default.FlashOn),
                "cpu_temp" to ("CPU Temperature" to Icons.Default.Thermostat),
                "battery_temp" to ("Battery Temperature" to Icons.Default.BatteryChargingFull),
                "battery_percent" to ("Battery Percentage" to Icons.Default.BatteryStd)
            )

            itemsIndexed(componentOrder, key = { _, key -> key }) { index, key ->
                val labelInfo = metricLabels[key] ?: ("Unknown" to Icons.Default.Help)
                val isDragging = draggedItemKey == key
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .animateItem() 
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = draggingOffset
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = 16.dp.toPx()
                            }
                        }
                        .zIndex(if (isDragging) 1f else 0f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDragging) MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    tonalElevation = if (isDragging) 8.dp else 0.dp
                ) {
                    ListItem(
                        headlineContent = { Text(labelInfo.first) },
                        leadingContent = { Icon(labelInfo.second, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Icon(
                                Icons.Default.Reorder,
                                null,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .pointerInput(key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedItemKey = key
                                                lastSwappedIndex = componentOrder.indexOf(key)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                draggingOffset += dragAmount.y
                                                
                                                val currentIndex = componentOrder.indexOf(key)
                                                if (currentIndex != -1) {
                                                    val itemView = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == key }
                                                    val itemHeight = itemView?.size ?: 0
                                                    
                                                    if (itemHeight > 0) {
                                                        val threshold = itemHeight * 0.3f
                                                        val targetIndex = if (draggingOffset > threshold) {
                                                            if (currentIndex < componentOrder.size - 1) currentIndex + 1 else currentIndex
                                                        } else if (draggingOffset < -threshold) {
                                                            if (currentIndex > 0) currentIndex - 1 else currentIndex
                                                        } else {
                                                            currentIndex
                                                        }

                                                        if (targetIndex != currentIndex) {
                                                            val newList = componentOrder.toMutableList()
                                                            val item = newList.removeAt(currentIndex)
                                                            newList.add(targetIndex, item)
                                                            componentOrder = newList
                                                            
                                                            if (targetIndex > currentIndex) {
                                                                draggingOffset -= itemHeight
                                                            } else {
                                                                draggingOffset += itemHeight
                                                            }

                                                            // Only vibrate if the actual index changed from the last vibration
                                                            if (targetIndex != lastSwappedIndex) {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                lastSwappedIndex = targetIndex
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggedItemKey = null
                                                draggingOffset = 0f
                                                lastSwappedIndex = null
                                                prefs.edit().putString("component_order", componentOrder.joinToString(",")).apply()
                                                notifyService()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragCancel = {
                                                draggedItemKey = null
                                                draggingOffset = 0f
                                                lastSwappedIndex = null
                                            }
                                        )
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsSection(title = "Appearance") {
                    OverlayToggleItem(
                        icon = Icons.Default.LinearScale,
                        title = "Show Progress Bars",
                        checked = showProgressBars,
                        onCheckedChange = { 
                            showProgressBars = it
                            prefs.edit().putBoolean("show_progress_bars", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.EmojiSymbols,
                        title = "Use Icons Only",
                        checked = showIconsOnly,
                        onCheckedChange = { 
                            showIconsOnly = it
                            prefs.edit().putBoolean("show_icons_only", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.GridView,
                        title = "Grid Layout",
                        checked = isGridView,
                        onCheckedChange = { 
                            isGridView = it
                            if (it) isHorizontal = false
                            prefs.edit().putBoolean("is_grid_view", it)
                                .putBoolean("is_horizontal", isHorizontal).apply()
                            notifyService()
                        }
                    )
                    
                    OverlayToggleItem(
                        icon = Icons.Default.ViewArray,
                        title = "Horizontal Layout",
                        checked = isHorizontal,
                        onCheckedChange = { 
                            isHorizontal = it
                            if (it) isGridView = false
                            prefs.edit().putBoolean("is_horizontal", it)
                                .putBoolean("is_grid_view", isGridView).apply()
                            notifyService()
                        }
                    )
                    
                    OverlayToggleItem(
                        icon = Icons.Default.ShowChart,
                        title = "Show Sparklines",
                        checked = showSparklines,
                        onCheckedChange = { 
                            showSparklines = it
                            prefs.edit().putBoolean("show_sparklines", it).apply()
                            notifyService()
                        }
                    )
                    
                    if (isGridView) {
                        SettingsItem(
                            icon = Icons.Default.ViewColumn,
                            title = "Grid Columns",
                            subtitle = "Columns: $gridColumns",
                            onClick = { }
                        ) {
                            Slider(
                                value = gridColumns.toFloat(),
                                onValueChange = { 
                                    gridColumns = it.toInt().coerceIn(1, 4)
                                },
                                onValueChangeFinished = {
                                    prefs.edit().putInt("grid_columns", gridColumns).apply()
                                    notifyService()
                                },
                                valueRange = 1f..4f,
                                steps = 2,
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "Styling") {
                    SettingsItem(
                        icon = Icons.Default.Opacity,
                        title = "Background Opacity",
                        subtitle = "${(overlayOpacity * 100).toInt()}%",
                        onClick = { }
                    ) {
                        Slider(
                            value = overlayOpacity,
                            onValueChange = { 
                                overlayOpacity = it
                            },
                            onValueChangeFinished = {
                                prefs.edit().putFloat("overlay_opacity", overlayOpacity).apply()
                                notifyService()
                            },
                            valueRange = 0.2f..1.0f,
                            modifier = Modifier.width(120.dp)
                        )
                    }

                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Accent Color",
                        subtitle = "Select overlay theme",
                        onClick = { }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            accentColors.forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(color, CircleShape)
                                        .clickable {
                                            accentColorIndex = index
                                            prefs.edit().putInt("accent_color_index", index).apply()
                                            notifyService()
                                        }
                                        .then(
                                            if (accentColorIndex == index) Modifier.background(Color.White.copy(alpha = 0.5f), CircleShape).padding(4.dp)
                                            else Modifier
                                        )
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "Overlay Behavior") {
                    SettingsItem(
                        icon = Icons.Default.Speed,
                        title = "Update Frequency",
                        subtitle = "Interval: ${updateInterval}ms",
                        onClick = { }
                    ) {
                        Slider(
                            value = updateInterval.toFloat(),
                            onValueChange = { 
                                updateInterval = it.toLong().coerceIn(100L, 5000L)
                            },
                            onValueChangeFinished = {
                                prefs.edit().putLong("update_interval", updateInterval).apply()
                                notifyService()
                            },
                            valueRange = 100f..5000f,
                            modifier = Modifier.width(120.dp)
                        )
                    }

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
