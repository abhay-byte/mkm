package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.service.OverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayScreen(
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val prefs = remember { context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE) }

    val snackbarHostState = remember { SnackbarHostState() }

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
    
    // Sync state with actual service life
    LaunchedEffect(Unit) {
        val prefEnabled = prefs.getBoolean("enabled", false)
        if (prefEnabled && !OverlayService.isRunning) {
            // Stale state: user had it on, but service was killed (e.g. force-close).
            // Auto-restart it instead of lying about its state.
            if (Settings.canDrawOverlays(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(Intent(context, OverlayService::class.java))
                } else {
                    context.startService(Intent(context, OverlayService::class.java))
                }
                isOverlayEnabled = true
            } else {
                // Permission lost / not granted - reset to off so UI matches reality
                prefs.edit().putBoolean("enabled", false).apply()
                isOverlayEnabled = false
            }
        } else {
            isOverlayEnabled = prefEnabled && OverlayService.isRunning
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(com.ivarna.mkm.R.string.menu))
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
                text = { Text(if (isOverlayEnabled) stringResource(com.ivarna.mkm.R.string.stop_overlay) else stringResource(com.ivarna.mkm.R.string.start_overlay)) },
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
        var showAbsoluteValues by remember { mutableStateOf(prefs.getBoolean("show_absolute_values", false)) }
        var accentTintBackground by remember { mutableStateOf(prefs.getBoolean("accent_tint_background", false)) }
        var accentBgColorIndex by remember { mutableStateOf(prefs.getInt("accent_bg_color_index", -1)) }
        var cpuFreqDisplay by remember { mutableStateOf(prefs.getString("cpu_freq_display", "all_cores") ?: "all_cores") }
        var cpuFreqDropdownExpanded by remember { mutableStateOf(false) }
        var absCpu by remember { mutableStateOf(prefs.getBoolean("abs_cpu", true)) }
        var absGpu by remember { mutableStateOf(prefs.getBoolean("abs_gpu", true)) }
        var absRam by remember { mutableStateOf(prefs.getBoolean("abs_ram", true)) }
        var absSwap by remember { mutableStateOf(prefs.getBoolean("abs_swap", true)) }

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
                    title = stringResource(com.ivarna.mkm.R.string.overlay_status),
                    usage = if (isOverlayEnabled) 1f else 0f,
                    mainValue = if (isOverlayEnabled) "ACTIVE" else "INACTIVE",
                    subValue = if (isOverlayEnabled) "Real-time monitoring enabled" else "Overlay service is stopped",
                    onClick = { /* No action needed */ },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsSection(title = stringResource(com.ivarna.mkm.R.string.visible_metrics)) {
                    OverlayToggleItem(
                        icon = Icons.Default.DeveloperBoard,
                        title = stringResource(com.ivarna.mkm.R.string.cpu_utilization),
                        checked = showCpuUsage,
                        onCheckedChange = {
                            showCpuUsage = it
                            prefs.edit().putBoolean("show_cpu_usage", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.Timeline,
                        title = stringResource(com.ivarna.mkm.R.string.cpu_frequency),
                        checked = showCpuFreq,
                        onCheckedChange = {
                            showCpuFreq = it
                            prefs.edit().putBoolean("show_cpu_freq", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.VideogameAsset,
                        title = stringResource(com.ivarna.mkm.R.string.gpu_utilization),
                        checked = showGpuUsage,
                        onCheckedChange = {
                            showGpuUsage = it
                            prefs.edit().putBoolean("show_gpu_usage", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.Dns,
                        title = stringResource(com.ivarna.mkm.R.string.ram_usage),
                        checked = showRamUsage,
                        onCheckedChange = {
                            showRamUsage = it
                            prefs.edit().putBoolean("show_ram_usage", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.SwapCalls,
                        title = stringResource(com.ivarna.mkm.R.string.swap_usage),
                        checked = showSwapUsage,
                        onCheckedChange = { 
                            showSwapUsage = it
                            prefs.edit().putBoolean("show_swap_usage", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.FlashOn,
                        title = stringResource(com.ivarna.mkm.R.string.power_usage),
                        checked = showPower,
                        onCheckedChange = { 
                            showPower = it
                            prefs.edit().putBoolean("show_power", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.Thermostat,
                        title = stringResource(com.ivarna.mkm.R.string.cpu_temperature),
                        checked = showCpuTemp,
                        onCheckedChange = { 
                            showCpuTemp = it
                            prefs.edit().putBoolean("show_cpu_temp", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.BatteryChargingFull,
                        title = stringResource(com.ivarna.mkm.R.string.battery_temperature),
                        checked = showBatteryTemp,
                        onCheckedChange = { 
                            showBatteryTemp = it
                            prefs.edit().putBoolean("show_battery_temp", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.BatteryStd,
                        title = stringResource(com.ivarna.mkm.R.string.battery_percentage),
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
                var showReorderDialog by remember { mutableStateOf(false) }
                
                if (showReorderDialog) {
                    ComponentOrderDialog(
                        componentOrder = componentOrder,
                        onDismiss = { showReorderDialog = false },
                        onReorder = { newOrder ->
                            componentOrder = newOrder
                            prefs.edit().putString("component_order", newOrder.joinToString(",")).apply()
                            notifyService()
                        }
                    )
                }
                
                SettingsSection(title = stringResource(com.ivarna.mkm.R.string.component_order)) {
                    SettingsItem(
                        icon = Icons.Default.Reorder,
                        title = stringResource(com.ivarna.mkm.R.string.reorder_components),
                        subtitle = stringResource(com.ivarna.mkm.R.string.customize_overlay_layout),
                        onClick = { showReorderDialog = true },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(com.ivarna.mkm.R.string.appearance)) {
                    OverlayToggleItem(
                        icon = Icons.Default.LinearScale,
                        title = stringResource(com.ivarna.mkm.R.string.show_progress_bars),
                        checked = showProgressBars,
                        onCheckedChange = { 
                            showProgressBars = it
                            prefs.edit().putBoolean("show_progress_bars", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.EmojiSymbols,
                        title = stringResource(com.ivarna.mkm.R.string.use_icons_only),
                        checked = showIconsOnly,
                        onCheckedChange = { 
                            showIconsOnly = it
                            prefs.edit().putBoolean("show_icons_only", it).apply()
                            notifyService()
                        }
                    )
                    OverlayToggleItem(
                        icon = Icons.Default.GridView,
                        title = stringResource(com.ivarna.mkm.R.string.grid_layout),
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
                        title = stringResource(com.ivarna.mkm.R.string.horizontal_layout),
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
                        title = stringResource(com.ivarna.mkm.R.string.show_sparklines),
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
                            title = stringResource(com.ivarna.mkm.R.string.grid_columns),
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    OverlayToggleItem(
                        icon = Icons.Default.Numbers,
                        title = stringResource(com.ivarna.mkm.R.string.show_absolute_values),
                        checked = showAbsoluteValues,
                        onCheckedChange = {
                            showAbsoluteValues = it
                            prefs.edit().putBoolean("show_absolute_values", it).apply()
                            notifyService()
                        }
                    )

                    if (showAbsoluteValues) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            OverlayToggleItem(
                                icon = Icons.Default.DeveloperBoard,
                                title = stringResource(com.ivarna.mkm.R.string.cpu_as_frequency),
                                checked = absCpu,
                                onCheckedChange = {
                                    absCpu = it
                                    prefs.edit().putBoolean("abs_cpu", it).apply()
                                    notifyService()
                                }
                            )
                            OverlayToggleItem(
                                icon = Icons.Default.VideogameAsset,
                                title = stringResource(com.ivarna.mkm.R.string.gpu_as_frequency),
                                checked = absGpu,
                                onCheckedChange = {
                                    absGpu = it
                                    prefs.edit().putBoolean("abs_gpu", it).apply()
                                    notifyService()
                                }
                            )
                            OverlayToggleItem(
                                icon = Icons.Default.Dns,
                                title = stringResource(com.ivarna.mkm.R.string.ram_as_size),
                                checked = absRam,
                                onCheckedChange = {
                                    absRam = it
                                    prefs.edit().putBoolean("abs_ram", it).apply()
                                    notifyService()
                                }
                            )
                            OverlayToggleItem(
                                icon = Icons.Default.SwapCalls,
                                title = stringResource(com.ivarna.mkm.R.string.swap_as_size),
                                checked = absSwap,
                                onCheckedChange = {
                                    absSwap = it
                                    prefs.edit().putBoolean("abs_swap", it).apply()
                                    notifyService()
                                }
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(com.ivarna.mkm.R.string.cpu_frequency_display),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box {
                            val currentLabel = when (cpuFreqDisplay) {
                                "avg" -> "Average"
                                "max" -> "Max Frequency"
                                else -> "All Cores"
                            }
                            ExposedDropdownMenuBox(
                                expanded = cpuFreqDropdownExpanded,
                                onExpandedChange = { cpuFreqDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = currentLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(com.ivarna.mkm.R.string.mode)) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = cpuFreqDropdownExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = cpuFreqDropdownExpanded,
                                    onDismissRequest = { cpuFreqDropdownExpanded = false }
                                ) {
                                    listOf(
                                        "all_cores" to "All Cores",
                                        "avg" to "Average",
                                        "max" to "Max Frequency"
                                    ).forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                cpuFreqDisplay = value
                                                prefs.edit().putString("cpu_freq_display", value).apply()
                                                cpuFreqDropdownExpanded = false
                                                notifyService()
                                            },
                                            leadingIcon = {
                                                if (cpuFreqDisplay == value) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(com.ivarna.mkm.R.string.cpu_freq_summary_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(com.ivarna.mkm.R.string.styling)) {
                    SettingsItem(
                        icon = Icons.Default.Opacity,
                        title = stringResource(com.ivarna.mkm.R.string.background_opacity),
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


                    // Accent Color Section - Custom layout to avoid ListItem constraints
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Accent Color",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Select overlay theme",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 56.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            accentColors.forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (accentColorIndex == index)
                                                MaterialTheme.colorScheme.surfaceVariant
                                            else
                                                Color.Transparent,
                                            CircleShape
                                        )
                                        .padding(4.dp)
                                        .background(color, CircleShape)
                                        .clickable {
                                            accentColorIndex = index
                                            prefs.edit().putInt("accent_color_index", index).apply()
                                            notifyService()
                                        }
                                )
                            }
                        }

                        OverlayToggleItem(
                            icon = Icons.Default.FormatColorFill,
                            title = stringResource(com.ivarna.mkm.R.string.apply_accent_to_background),
                            checked = accentTintBackground,
                            onCheckedChange = {
                                accentTintBackground = it
                                prefs.edit().putBoolean("accent_tint_background", it).apply()
                                notifyService()
                            }
                        )

                        if (accentTintBackground) {
                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                                Text(
                                    text = stringResource(com.ivarna.mkm.R.string.background_color),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    accentColors.forEachIndexed { index, color ->
                                        val effectiveIndex = if (accentBgColorIndex == -1) accentColorIndex else accentBgColorIndex
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (effectiveIndex == index)
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    else
                                                        Color.Transparent,
                                                    CircleShape
                                                )
                                                .padding(4.dp)
                                                .background(color, CircleShape)
                                                .clickable {
                                                    accentBgColorIndex = index
                                                    prefs.edit().putInt("accent_bg_color_index", index).apply()
                                                    notifyService()
                                                }
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(com.ivarna.mkm.R.string.bg_color_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(com.ivarna.mkm.R.string.overlay_behavior)) {
                    SettingsItem(
                        icon = Icons.Default.Speed,
                        title = stringResource(com.ivarna.mkm.R.string.update_frequency),
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
                        title = stringResource(com.ivarna.mkm.R.string.movable_overlay),
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

@Composable
fun ComponentOrderDialog(
    componentOrder: List<String>,
    onDismiss: () -> Unit,
    onReorder: (List<String>) -> Unit
) {
    var localOrder by remember { mutableStateOf(componentOrder) }
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var draggingOffset by remember { mutableStateOf(0f) }
    var lastSwappedIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    
    val metricLabels = mapOf(
        "cpu_usage" to ("CPU Utilization" to Icons.Default.DeveloperBoard),
        "cpu_freq" to ("CPU Frequency" to Icons.Default.Timeline),
        "gpu_usage" to ("GPU Utilization" to Icons.Default.VideogameAsset),
        "ram_usage" to ("RAM Usage" to Icons.Default.Dns),
        "swap_usage" to ("Swap Usage" to Icons.Default.SwapCalls),
        "power_usage" to ("Power Usage" to Icons.Default.FlashOn),
        "cpu_temp" to ("CPU Temperature" to Icons.Default.Thermostat),
        "battery_temp" to ("Battery Temperature" to Icons.Default.BatteryChargingFull),
        "battery_percent" to ("Battery Percentage" to Icons.Default.BatteryStd)
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reorder Components",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(com.ivarna.mkm.R.string.close))
                    }
                }
                
                HorizontalDivider()
                
                // Drag instructions
                Text(
                    "Long press and drag to reorder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                
                // Reorderable list
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(localOrder, key = { _, key -> key }) { index, key ->
                        val labelInfo = metricLabels[key] ?: ("Unknown" to Icons.Default.Help)
                        val isDragging = draggedItemKey == key
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = draggingOffset
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                        shadowElevation = 8.dp.toPx()
                                    }
                                }
                                .zIndex(if (isDragging) 1f else 0f),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDragging) 
                                MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp) 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            tonalElevation = if (isDragging) 4.dp else 0.dp
                        ) {
                            ListItem(
                                headlineContent = { Text(labelInfo.first) },
                                leadingContent = { 
                                    Icon(labelInfo.second, null, tint = MaterialTheme.colorScheme.primary) 
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        null,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .pointerInput(key) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggedItemKey = key
                                                        lastSwappedIndex = localOrder.indexOf(key)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        draggingOffset += dragAmount.y
                                                        
                                                        val currentIndex = localOrder.indexOf(key)
                                                        if (currentIndex != -1) {
                                                            val itemView = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == key }
                                                            val itemHeight = itemView?.size ?: 0
                                                            
                                                            if (itemHeight > 0) {
                                                                // Increased threshold from 0.3f to 0.6f for less sensitivity
                                                                val threshold = itemHeight * 0.6f
                                                                val targetIndex = if (draggingOffset > threshold) {
                                                                    if (currentIndex < localOrder.size - 1) currentIndex + 1 else currentIndex
                                                                } else if (draggingOffset < -threshold) {
                                                                    if (currentIndex > 0) currentIndex - 1 else currentIndex
                                                                } else {
                                                                    currentIndex
                                                                }

                                                                if (targetIndex != currentIndex && targetIndex != lastSwappedIndex) {
                                                                    val newList = localOrder.toMutableList()
                                                                    val item = newList.removeAt(currentIndex)
                                                                    newList.add(targetIndex, item)
                                                                    localOrder = newList
                                                                    
                                                                    if (targetIndex > currentIndex) {
                                                                        draggingOffset -= itemHeight
                                                                    } else {
                                                                        draggingOffset += itemHeight
                                                                    }

                                                                    // Reduced haptic feedback - only vibrate once per swap
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    lastSwappedIndex = targetIndex
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggedItemKey = null
                                                        draggingOffset = 0f
                                                        lastSwappedIndex = null
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
                }
                
                HorizontalDivider()
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(com.ivarna.mkm.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onReorder(localOrder)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(com.ivarna.mkm.R.string.save))
                    }
                }
            }
        }
    }
}