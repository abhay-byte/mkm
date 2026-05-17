package com.ivarna.mkm.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.BatteryStats
import com.ivarna.mkm.service.BatteryMonitorService
import com.ivarna.mkm.service.BatterySessionTracker
import com.ivarna.mkm.ui.components.SectionHeader
import com.ivarna.mkm.ui.viewmodel.BatteryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    viewModel: BatteryViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {}
) {
    val stats by viewModel.batteryStats.collectAsState()
    val showNotification by viewModel.showNotification.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val batteryPrefs = remember { context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE) }

    val updateIntervalOptions = remember {
        listOf(
            "5s" to 5_000L,
            "10s" to 10_000L,
            "30s" to 30_000L,
            "1 min" to 60_000L,
            "5 min" to 300_000L,
            "10 min" to 600_000L
        )
    }
    var selectedIntervalMs by remember {
        mutableStateOf(batteryPrefs.getLong("battery_update_interval_ms", BatterySessionTracker.DEFAULT_UPDATE_INTERVAL_MS))
    }

    val notifHeadingOptions = remember {
        mapOf(
            "Wattage" to (BatteryMonitorService.PREF_NOTIF_SHOW_WATTAGE to true),
            "Temperature" to (BatteryMonitorService.PREF_NOTIF_SHOW_TEMPERATURE to false),
            "Drain Rate" to (BatteryMonitorService.PREF_NOTIF_SHOW_DRAIN to false),
            "Time Left" to (BatteryMonitorService.PREF_NOTIF_SHOW_TIME_LEFT to false),
            "Current (mA)" to (BatteryMonitorService.PREF_NOTIF_SHOW_CURRENT to false),
            "Voltage (mV)" to (BatteryMonitorService.PREF_NOTIF_SHOW_VOLTAGE to false)
        )
    }
    var notifHeadingSelections by remember {
        mutableStateOf(notifHeadingOptions.mapValues { (_, pair) ->
            batteryPrefs.getBoolean(pair.first, pair.second)
        })
    }

    val notifExpandedOptions = remember {
        mapOf(
            "Temperature & Voltage" to (BatteryMonitorService.PREF_NOTIF_EXP_TEMP_VOLTAGE to true),
            "Power (Wattage)" to (BatteryMonitorService.PREF_NOTIF_EXP_POWER to true),
            "Drain Rates" to (BatteryMonitorService.PREF_NOTIF_EXP_DRAIN to true),
            "Time Left" to (BatteryMonitorService.PREF_NOTIF_EXP_TIME_LEFT to true),
            "Screen On" to (BatteryMonitorService.PREF_NOTIF_EXP_SCREEN_ON to true),
            "Screen Off" to (BatteryMonitorService.PREF_NOTIF_EXP_SCREEN_OFF to true),
            "Deep Sleep" to (BatteryMonitorService.PREF_NOTIF_EXP_DEEP_SLEEP to true),
            "Awake" to (BatteryMonitorService.PREF_NOTIF_EXP_AWAKE to true)
        )
    }
    var notifExpandedSelections by remember {
        mutableStateOf(notifExpandedOptions.mapValues { (_, pair) ->
            batteryPrefs.getBoolean(pair.first, pair.second)
        })
    }

    var pendingToggle by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationEnabled(true)
        } else {
            pendingToggle = false
            // Show snackbar explaining why permission is needed
        }
    }

    fun onToggleNotification(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pendingToggle = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setNotificationEnabled(true)
            }
        } else {
            viewModel.setNotificationEnabled(false)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Battery",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleNotification(!showNotification) }) {
                        Icon(
                            imageVector = if (showNotification) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = if (showNotification) "Disable notification" else "Enable notification"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            stats?.let { data ->
                BatteryHeroCard(stats = data)

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("BATTERY LIFE")
                EstimatedTimeCard(stats = data)

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("CAPACITY")
                CapacityCard(stats = data)

                if (data.wattageHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("WATTAGE HISTORY")
                    HistorySparklineCard(
                        history = data.wattageHistory,
                        currentValue = "${String.format("%+.2f", data.calibratedWattageW)} W",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (data.drainHistory.isNotEmpty() && data.isSessionActive) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("DRAIN HISTORY")
                    HistorySparklineCard(
                        history = data.drainHistory,
                        currentValue = "${String.format("%.2f", data.activeDrainPerHr)}%/hr",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("DRAIN RATES")
                DrainRateGrid(stats = data)

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("SESSION BREAKDOWN")
                TimeBreakdownCard(stats = data)

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("NOTIFICATION")
                NotificationToggleCard(
                    enabled = showNotification,
                    onToggle = { onToggleNotification(it) }
                )

                if (showNotification) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("NOTIFICATION CONTENT")
                    NotificationContentCard(
                        headingOptions = notifHeadingOptions,
                        headingSelections = notifHeadingSelections,
                        expandedOptions = notifExpandedOptions,
                        expandedSelections = notifExpandedSelections,
                        onToggle = { prefKey: String, enabled: Boolean ->
                            notifHeadingSelections = notifHeadingSelections.toMutableMap().also { it[prefKey] = enabled }
                            notifExpandedSelections = notifExpandedSelections.toMutableMap().also { it[prefKey] = enabled }
                            batteryPrefs.edit().putBoolean(prefKey, enabled).apply()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("MONITORING")
                IntervalSelectorCard(
                    currentMs = selectedIntervalMs,
                    options = updateIntervalOptions,
                    onSelect = { ms ->
                        selectedIntervalMs = ms
                        batteryPrefs.edit().putLong("battery_update_interval_ms", ms).apply()
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun BatteryHeroCard(stats: BatteryStats) {
    val statusText = when {
        stats.isCharging -> "Charging"
        stats.isSessionActive -> "Discharging ${stats.currentMa} mA"
        else -> "On AC"
    }

    val statusColor = when {
        stats.isCharging -> Color(0xFF4CAF50)
        stats.percent <= 15 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${stats.percent}%",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${String.format("%.1f", stats.temperatureC)}°C",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    imageVector = if (stats.isCharging) Icons.Default.Power else Icons.Default.BatteryFull,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = statusColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusBadge(
                    label = statusText,
                    color = statusColor,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    label = "${stats.voltageMv} mV",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
            }

            if (stats.calibratedWattageW != 0f) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val wattageColor = if (stats.isCharging)
                        Color(0xFF4CAF50)
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    StatusBadge(
                        label = "${String.format("%+.2f", stats.calibratedWattageW)} W",
                        color = wattageColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun EstimatedTimeCard(stats: BatteryStats) {
    val label: String
    val subLabel: String
    val color: Color

    if (stats.estimatedTimeRemainingMin > 0) {
        val hours = stats.estimatedTimeRemainingMin / 60
        val minutes = stats.estimatedTimeRemainingMin % 60
        label = buildString {
            if (hours > 0) append("${hours}h ")
            append("${minutes}m")
        }
        subLabel = if (stats.isCharging) "until full" else "remaining"
        color = if (stats.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    } else {
        label = "—"
        subLabel = if (stats.isCharging) "Calculating…" else "Need more data"
        color = MaterialTheme.colorScheme.onSurfaceVariant
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!stats.isCharging && stats.estimatedTimeRemainingMin > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val blendedDrain = stats.activeDrainPerHr * (stats.screenOnPercent / 100f) +
                        stats.idleDrainPerHr * (stats.screenOffPercent / 100f)
                Text(
                    text = "Based on ${String.format("%.2f", blendedDrain)}%/hr blended drain",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun CapacityCard(stats: BatteryStats) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val showRated = stats.ratedCapacityMah > 0 && stats.ratedCapacityMah != stats.estimatedCapacityMah

            if (showRated) {
                CapacityRow(
                    label = "Rated (design) capacity",
                    value = "${stats.ratedCapacityMah} mAh",
                    icon = Icons.Default.BatteryStd,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            if (stats.estimatedCapacityMah > 0) {
                val isEstimatedOnly = !showRated
                CapacityRow(
                    label = if (isEstimatedOnly) "Estimated capacity" else "Estimated full capacity",
                    value = "${stats.estimatedCapacityMah} mAh",
                    icon = Icons.Default.BatteryFull,
                    color = MaterialTheme.colorScheme.secondary
                )

                if (stats.ratedCapacityMah > 0 && stats.ratedCapacityMah != stats.estimatedCapacityMah) {
                    val healthPct = (stats.estimatedCapacityMah * 100f / stats.ratedCapacityMah).coerceIn(0f, 100f)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { healthPct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = when {
                            healthPct >= 80f -> Color(0xFF4CAF50)
                            healthPct >= 60f -> Color(0xFFFFC107)
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Battery health: ${String.format("%.1f", healthPct)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isEstimatedOnly) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Calculated from charge counter. Root or Shizuku provides more accurate kernel readings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Capacity info unavailable. Root or Shizuku access may be required to read kernel battery data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CapacityRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DrainRateGrid(stats: BatteryStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DrainRateCard(
            title = "Active Drain",
            value = "${String.format("%.2f", stats.activeDrainPerHr)}%/hr",
            icon = Icons.Default.PhoneAndroid,
            isActive = stats.screenOnTimeMs > 0,
            modifier = Modifier.weight(1f)
        )
        DrainRateCard(
            title = "Idle Drain",
            value = "${String.format("%.2f", stats.idleDrainPerHr)}%/hr",
            icon = Icons.Default.Snooze,
            isActive = stats.screenOffTimeMs > 0,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DrainRateCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TimeBreakdownCard(stats: BatteryStats) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (!stats.isSessionActive) {
                Text(
                    text = "Session ended when charger was connected. Disconnect to start a new session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TimeRow(
                label = "Screen on",
                duration = formatDuration(stats.screenOnTimeMs),
                percent = stats.screenOnPercent,
                drainPercent = stats.screenOnDrainPercent,
                icon = Icons.Default.PhoneAndroid,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            TimeRow(
                label = "Screen off",
                duration = formatDuration(stats.screenOffTimeMs),
                percent = stats.screenOffPercent,
                drainPercent = stats.screenOffDrainPercent,
                icon = Icons.Default.PowerOff,
                color = MaterialTheme.colorScheme.secondary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            TimeRow(
                label = "Deep sleep",
                duration = formatDuration(stats.deepSleepTimeMs),
                percent = stats.deepSleepPercent,
                drainPercent = stats.deepSleepDrainPercent,
                icon = Icons.Default.Snooze,
                color = Color(0xFF4CAF50)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            TimeRow(
                label = "Awake",
                duration = formatDuration(stats.awakeTimeMs),
                percent = stats.awakePercent,
                drainPercent = stats.awakeDrainPercent,
                icon = Icons.Default.Schedule,
                color = MaterialTheme.colorScheme.tertiary
            )

            if (stats.isSessionActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Session started ${formatDuration(stats.totalSessionTimeMs)} ago",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TimeRow(
    label: String,
    duration: String,
    percent: Float,
    drainPercent: Float = 0f,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = duration,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            if (drainPercent > 0f) {
                Text(
                    text = "${String.format("%.1f", drainPercent)}% of drain · ${String.format("%.0f", percent)}% of time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${String.format("%.2f", percent)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NotificationToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (enabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Notification Card",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (enabled) "Showing live battery stats in notification" else "Tap to enable battery notification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun StatusBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun HistorySparklineCard(
    history: List<Float>,
    currentValue: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Sparkline(
                history = history,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
        }
    }
}

@Composable
fun Sparkline(
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val pathColor = color
    val fillColor = color.copy(alpha = 0.15f)
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas
        val width = size.width
        val height = size.height
        val stepX = width / (history.size - 1).coerceAtLeast(1)

        val path = androidx.compose.ui.graphics.Path().apply {
            history.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value.coerceIn(0f, 1f) * height)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        // Fill area under line
        val fillPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, height)
            history.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value.coerceIn(0f, 1f) * height)
                lineTo(x, y)
            }
            lineTo(width, height)
            close()
        }
        drawPath(fillPath, fillColor)

        // Draw line
        drawPath(path, pathColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))

        // Draw end dot
        val lastY = height - (history.last().coerceIn(0f, 1f) * height)
        drawCircle(
            color = pathColor,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(width, lastY)
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

@Composable
fun IntervalSelectorCard(
    currentMs: Long,
    options: List<Pair<String, Long>>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.second == currentMs }?.first ?: "30s"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Refresh Interval",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "How often battery stats update",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    AssistChip(
                        onClick = { expanded = true },
                        label = { Text(currentLabel) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { (label, ms) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSelect(ms)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (ms == currentMs) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Longer intervals reduce MKM's own battery usage (was every 1s, now default 30s).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun NotificationContentCard(
    headingOptions: Map<String, Pair<String, Boolean>>,
    headingSelections: Map<String, Boolean>,
    expandedOptions: Map<String, Pair<String, Boolean>>,
    expandedSelections: Map<String, Boolean>,
    onToggle: (prefKey: String, enabled: Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Notification Heading",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            headingOptions.forEach { (label, pair) ->
                val (prefKey, _) = pair
                val checked = headingSelections[prefKey] ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(prefKey, !checked) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = checked, onCheckedChange = { onToggle(prefKey, it) })
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "Expanded Content",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            expandedOptions.forEach { (label, pair) ->
                val (prefKey, _) = pair
                val checked = expandedSelections[prefKey] ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(prefKey, !checked) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = checked, onCheckedChange = { onToggle(prefKey, it) })
                }
            }
        }
    }
}
