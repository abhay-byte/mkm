package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivarna.mkm.service.BatteryMonitorService
import com.ivarna.mkm.service.BatterySessionTracker

// ---------------------------------------------------------------------------
// Data model for a single toggle option
// ---------------------------------------------------------------------------

data class NotifOption(
    val prefKey: String,
    val label: String,
    val defaultValue: Boolean,
    val subtitle: String = ""
)

// ---------------------------------------------------------------------------
// Heading options (all sessions — shown in the notification title bar)
// ---------------------------------------------------------------------------

private val headingOptions = listOf(
    NotifOption(BatteryMonitorService.PREF_NOTIF_SHOW_WATTAGE, "Wattage", true, "Show W reading in title"),
    NotifOption(BatteryMonitorService.PREF_NOTIF_SHOW_TEMPERATURE, "Temperature", false, "Show °C in title"),
    NotifOption(BatteryMonitorService.PREF_NOTIF_SHOW_DRAIN, "Drain Rate", false, "Show %/hr in title"),
    NotifOption(BatteryMonitorService.PREF_NOTIF_SHOW_TIME_LEFT, "Time Left", false, "Remaining / full estimate in title"),
    NotifOption(BatteryMonitorService.PREF_NOTIF_SHOW_CURRENT, "Current (mA)", false, "Show current in title"),
    NotifOption(BatteryMonitorService.PREF_NOTIF_SHOW_VOLTAGE, "Voltage (mV)", false, "Show voltage in title"),
)

// ---------------------------------------------------------------------------
// Expanded options — discharging sessions
// ---------------------------------------------------------------------------

private val dischargingExpandedOptions = listOf(
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_TEMP_VOLTAGE, "Temperature & Voltage", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_POWER, "Power (Wattage)", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_DRAIN, "Drain Rates", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_TIME_LEFT, "Time Remaining", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_SCREEN_ON, "Screen On", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_SCREEN_OFF, "Screen Off", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_DEEP_SLEEP, "Deep Sleep", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_AWAKE, "Awake", true),
    NotifOption(BatteryMonitorService.PREF_NOTIF_EXP_SHOW_MAH, "Show mAh (capacity estimate)", false, "Append estimated mAh to each stat"),
)

// Charging expanded options — these always show unconditionally from the manager,
// but we expose toggles here for future fine-grained control.
// Currently the charging notification shows everything always; the pref is read for discharging.

// ---------------------------------------------------------------------------
// Top-level Notification Settings screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onOpenCharging: () -> Unit,
    onOpenDischarging: () -> Unit,
    onOpenMonitoring: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Notification Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.ivarna.mkm.R.string.back))
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            NotifSettingsNavCard(
                icon = Icons.Default.Power,
                iconTint = Color(0xFF4CAF50),
                title = stringResource(com.ivarna.mkm.R.string.charging),
                subtitle = stringResource(com.ivarna.mkm.R.string.customise_while_charging),
                onClick = onOpenCharging
            )
            NotifSettingsNavCard(
                icon = Icons.Default.BatteryStd,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(com.ivarna.mkm.R.string.discharging),
                subtitle = stringResource(com.ivarna.mkm.R.string.customise_while_on_battery),
                onClick = onOpenDischarging
            )
            NotifSettingsNavCard(
                icon = Icons.Default.Timer,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = stringResource(com.ivarna.mkm.R.string.monitoring),
                subtitle = stringResource(com.ivarna.mkm.R.string.refresh_and_heading),
                onClick = onOpenMonitoring
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Charging notification settings sub-page
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingNotificationSettingsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE) }

    // Charging notification always shows temp, power, current, gained %, duration, est. full
    // We expose a single toggle for "Show in expanded" per metric — these map to existing prefs
    // but for charging the content is currently unconditional (see BatteryNotificationManager).
    // We add charging-specific prefs here for forward-compat.

    val chargingOptions = listOf(
        NotifOption("notif_chg_show_power", "Charging Power (W)", true, "Show watts in expanded view"),
        NotifOption("notif_chg_show_current", "Current (mA)", true, "Show instant + avg current"),
        NotifOption("notif_chg_show_temp", "Temperature & Voltage", true, "Show °C and mV"),
        NotifOption("notif_chg_show_gained", "Gained %", true, "Show % gained since plug-in"),
        NotifOption("notif_chg_show_duration", "Charging Duration", true, "Time since charger connected"),
        NotifOption("notif_chg_show_time_full", "Est. Time to Full", true, "Estimated minutes until 100%"),
    )

    var selections by remember {
        mutableStateOf(
            chargingOptions.associate { opt ->
                opt.prefKey to prefs.getBoolean(opt.prefKey, opt.defaultValue)
            }
        )
    }

    fun toggle(prefKey: String, value: Boolean) {
        selections = selections + (prefKey to value)
        prefs.edit().putBoolean(prefKey, value).apply()
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Charging Notification",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.ivarna.mkm.R.string.back))
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            NotifSectionCard(
                title = stringResource(com.ivarna.mkm.R.string.expanded_content),
                subtitle = "Shown when the notification is expanded",
                iconTint = Color(0xFF4CAF50),
                options = chargingOptions,
                selections = selections,
                onToggle = { key, v -> toggle(key, v) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Discharging notification settings sub-page
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DischargingNotificationSettingsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE) }

    var selections by remember {
        mutableStateOf(
            dischargingExpandedOptions.associate { opt ->
                opt.prefKey to prefs.getBoolean(opt.prefKey, opt.defaultValue)
            }
        )
    }

    fun toggle(prefKey: String, value: Boolean) {
        selections = selections + (prefKey to value)
        prefs.edit().putBoolean(prefKey, value).apply()
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Discharging Notification",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.ivarna.mkm.R.string.back))
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            NotifSectionCard(
                title = "Expanded Content",
                subtitle = stringResource(com.ivarna.mkm.R.string.shown_when_expanded_battery),
                iconTint = MaterialTheme.colorScheme.primary,
                options = dischargingExpandedOptions,
                selections = selections,
                onToggle = { key, v -> toggle(key, v) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Monitoring / Heading settings sub-page
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringNotificationSettingsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE) }

    // Heading toggles
    var headingSelections by remember {
        mutableStateOf(
            headingOptions.associate { opt ->
                opt.prefKey to prefs.getBoolean(opt.prefKey, opt.defaultValue)
            }
        )
    }

    // Refresh interval
    val intervalOptions = listOf(
        "5s" to 5_000L,
        "10s" to 10_000L,
        "30s" to 30_000L,
        "1 min" to 60_000L,
        "5 min" to 300_000L,
        "10 min" to 600_000L
    )
    var selectedIntervalMs by remember {
        mutableStateOf(prefs.getLong("battery_update_interval_ms", BatterySessionTracker.DEFAULT_UPDATE_INTERVAL_MS))
    }
    var intervalMenuExpanded by remember { mutableStateOf(false) }

    fun toggleHeading(prefKey: String, value: Boolean) {
        headingSelections = headingSelections + (prefKey to value)
        prefs.edit().putBoolean(prefKey, value).apply()
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Monitoring",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.ivarna.mkm.R.string.back))
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Refresh interval card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(com.ivarna.mkm.R.string.refresh_interval), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(com.ivarna.mkm.R.string.how_often_battery_update), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box {
                            val currentLabel = intervalOptions.find { it.second == selectedIntervalMs }?.first ?: "30s"
                            AssistChip(
                                onClick = { intervalMenuExpanded = true },
                                label = { Text(currentLabel) },
                                trailingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenu(expanded = intervalMenuExpanded, onDismissRequest = { intervalMenuExpanded = false }) {
                                intervalOptions.forEach { (label, ms) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedIntervalMs = ms
                                            prefs.edit().putLong("battery_update_interval_ms", ms).apply()
                                            intervalMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (ms == selectedIntervalMs) {
                                                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Longer intervals reduce MKM's own battery usage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Notification heading toggles
            NotifSectionCard(
                title = stringResource(com.ivarna.mkm.R.string.notification_heading),
                subtitle = stringResource(com.ivarna.mkm.R.string.items_in_title_bar),
                iconTint = MaterialTheme.colorScheme.secondary,
                options = headingOptions,
                selections = headingSelections,
                onToggle = { key, v -> toggleHeading(key, v) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable composables
// ---------------------------------------------------------------------------

@Composable
fun NotifSettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun NotifSettingsNavCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NotifSectionCard(
    title: String,
    subtitle: String,
    iconTint: Color,
    options: List<NotifOption>,
    selections: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = iconTint)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            options.forEachIndexed { index, opt ->
                val checked = selections[opt.prefKey] ?: opt.defaultValue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(opt.prefKey, !checked) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(opt.label, style = MaterialTheme.typography.bodyLarge)
                        if (opt.subtitle.isNotEmpty()) {
                            Text(opt.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = checked, onCheckedChange = { onToggle(opt.prefKey, it) })
                }
                if (index < options.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
