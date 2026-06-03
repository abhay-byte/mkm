package com.ivarna.mkm.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.service.BatteryMonitorService
import com.ivarna.mkm.service.BatterySessionTracker
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.viewmodel.AppTheme
import com.ivarna.mkm.ui.viewmodel.PowerViewModel
import com.ivarna.mkm.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    powerViewModel: PowerViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {},
    onRequestShizukuPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {}
) {
    val theme by viewModel.theme.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }

    // --- Power Calibration state ---
    val savedMultiplier by powerViewModel.calibrationMultiplier.collectAsState()
    val powerStatus by powerViewModel.powerStatus.collectAsState()
    var calibrationMultiplierText by remember { mutableStateOf(savedMultiplier.toString()) }
    var userHasEdited by remember { mutableStateOf(false) }
    LaunchedEffect(savedMultiplier) {
        if (!userHasEdited) calibrationMultiplierText = savedMultiplier.toString()
    }
    var calibrationSaveError by remember { mutableStateOf(false) }

    // --- Battery Notification state ---
    val batteryPrefs = remember { context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE) }
    var batteryNotificationEnabled by remember {
        mutableStateOf(batteryPrefs.getBoolean(BatteryMonitorService.PREF_NOTIFICATION_ENABLED, false))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setBatteryNotification(context, true)
            batteryNotificationEnabled = true
        }
    }

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
    var showIntervalMenu by remember { mutableStateOf(false) }

    fun toggleBatteryNotification(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            setBatteryNotification(context, enabled)
            batteryNotificationEnabled = enabled
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                     IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item {
                AppInfoCard(
                    appName = "Minimal Kernel Manager",
                    version = "v1.3",
                    buildDate = "Feb 28, 2026"
                )
            }

            // Access Method Card - NEW for v1.1
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AccessMethodCard(
                    onRequestShizukuPermission = onRequestShizukuPermission
                )
            }

            item {
                SettingsSection(title = "Appearance") {
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        subtitle = when(theme) {
                            AppTheme.SYSTEM -> "System Default"
                            AppTheme.DYNAMIC -> "Dynamic (Material You)"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                            AppTheme.AMOLED -> "Black (AMOLED)"
                            AppTheme.NORD -> "Nord Theme"
                            AppTheme.NORD_LIGHT -> "Nord Light"
                            AppTheme.DRACULA -> "Dracula"
                            AppTheme.MONOKAI -> "Monokai"
                            AppTheme.GRUVBOX -> "Gruvbox"
                            AppTheme.GRUVBOX_LIGHT -> "Gruvbox Light"
                            AppTheme.SOLARIZED -> "Solarized Dark"
                            AppTheme.SOLARIZED_LIGHT -> "Solarized Light"
                            AppTheme.SYNTHWAVE -> "Synthwave (Neon)"
                            AppTheme.ONE_LIGHT -> "One Light"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // Power Calibration - moved from Overlay page
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "Power Calibration") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val liveCalibrated = powerStatus.powerW * savedMultiplier
                        val polaritySign = if (powerStatus.isCharging) "+" else "-"
                        val polarityColor = if (powerStatus.isCharging)
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.error
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Raw Power",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${polaritySign}%.3f W".format(powerStatus.powerW),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = polarityColor
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Calibrated (${savedMultiplier}×)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${polaritySign}%.3f W".format(liveCalibrated),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Calibration Multiplier",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = calibrationMultiplierText,
                                onValueChange = {
                                    calibrationMultiplierText = it
                                    userHasEdited = true
                                    calibrationSaveError = false
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("Multiplier") },
                                placeholder = { Text("e.g. 1.1") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                isError = calibrationSaveError,
                                supportingText = if (calibrationSaveError) {
                                    { Text("Invalid number", color = MaterialTheme.colorScheme.error) }
                                } else null
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    val normalised = calibrationMultiplierText.trim().replace(',', '.')
                                    val v = normalised.toFloatOrNull()
                                    if (v != null && v > 0f) {
                                        powerViewModel.saveCalibrationMultiplier(v)
                                        userHasEdited = false
                                        calibrationSaveError = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Multiplier saved: ${v}×")
                                        }
                                    } else {
                                        calibrationSaveError = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save multiplier")
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    powerViewModel.saveCalibrationMultiplier(1.0f)
                                    userHasEdited = false
                                    calibrationSaveError = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Reset to 1.0×")
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Reset to 1×")
                            }
                        }
                        Text(
                            text = "Saved: ${savedMultiplier}×  •  Match your external power meter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Battery Notification Toggle
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "Notifications") {
                    SettingsItem(
                        icon = if (batteryNotificationEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        title = "Battery Monitor",
                        subtitle = if (batteryNotificationEnabled) "Persistent notification active" else "Tap to enable battery notification",
                        onClick = { toggleBatteryNotification(!batteryNotificationEnabled) },
                        trailing = {
                            Switch(
                                checked = batteryNotificationEnabled,
                                onCheckedChange = { toggleBatteryNotification(it) }
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Navigate to full notification customise screen
                    SettingsItem(
                        icon = Icons.Default.Tune,
                        title = "Customise Notification",
                        subtitle = "Configure what's shown when charging or discharging",
                        onClick = onOpenNotificationSettings
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Refresh Interval",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box {
                            val currentLabel = updateIntervalOptions.find { it.second == selectedIntervalMs }?.first ?: "30s"
                            AssistChip(
                                onClick = { showIntervalMenu = true },
                                label = { Text(currentLabel) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = showIntervalMenu,
                                onDismissRequest = { showIntervalMenu = false }
                            ) {
                                updateIntervalOptions.forEach { (label, ms) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedIntervalMs = ms
                                            batteryPrefs.edit().putLong("battery_update_interval_ms", ms).apply()
                                            showIntervalMenu = false
                                        },
                                        leadingIcon = {
                                            if (ms == selectedIntervalMs) {
                                                Icon(
                                                    Icons.Default.Timer,
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Longer intervals reduce battery usage. Default is 30s.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                AboutMeCard(
                    name = "Abhay Raj",
                    handle = "@abhay-byte",
                    bio = "Passionate about building software, exploring hardware, and all things Linux."
                )
            }

            item {
                SettingsSection(title = "Connect With Me") {
                    SocialItem(
                        icon = Icons.Default.Code,
                        label = "GitHub",
                        description = "View my repositories and projects",
                        onClick = { uriHandler.openUri("https://github.com/abhay-byte") }
                    )
                    SocialItem(
                        icon = Icons.Default.Group,
                        label = "LinkedIn",
                        description = "Let's connect professionally",
                        onClick = { uriHandler.openUri("https://www.linkedin.com/in/abhay-byte") }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    onClick = { uriHandler.openUri("https://github.com/abhay-byte/mkm") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    ListItem(
                        headlineContent = { Text("Star on GitHub", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("MKM - If you like this project, please give it a star!") },
                        leadingContent = {
                            Icon(Icons.Default.Star, contentDescription = null)
                        },
                        modifier = Modifier.padding(8.dp),
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        SelectionBottomSheet(
            title = "App Theme",
            items = AppTheme.values().map { it.name },
            selectedItem = theme.name,
            onDismiss = { showThemeDialog = false },
            onItemSelected = {
                viewModel.setTheme(AppTheme.valueOf(it))
                showThemeDialog = false
            },
            itemLabel = {
                when(AppTheme.valueOf(it)) {
                    AppTheme.SYSTEM -> "System Default"
                    AppTheme.DYNAMIC -> "Dynamic (Material You)"
                    AppTheme.LIGHT -> "Light"
                    AppTheme.DARK -> "Dark"
                    AppTheme.AMOLED -> "Black (AMOLED)"
                    AppTheme.NORD -> "Nord Theme"
                    AppTheme.NORD_LIGHT -> "Nord Light"
                    AppTheme.DRACULA -> "Dracula"
                    AppTheme.MONOKAI -> "Monokai"
                    AppTheme.GRUVBOX -> "Gruvbox"
                    AppTheme.GRUVBOX_LIGHT -> "Gruvbox Light"
                    AppTheme.SOLARIZED -> "Solarized Dark"
                    AppTheme.SOLARIZED_LIGHT -> "Solarized Light"
                    AppTheme.SYNTHWAVE -> "Synthwave (Neon)"
                    AppTheme.ONE_LIGHT -> "One Light"
                }
            }
        )
    }
}

private fun setBatteryNotification(context: Context, enabled: Boolean) {
    context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(BatteryMonitorService.PREF_NOTIFICATION_ENABLED, enabled)
        .apply()

    val intent = Intent(context, BatteryMonitorService::class.java).apply {
        action = if (enabled) BatteryMonitorService.ACTION_START else BatteryMonitorService.ACTION_STOP
    }
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
