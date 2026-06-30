package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ivarna.mkm.data.model.SwapDeviceInfo
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.MemoryStatus
import com.ivarna.mkm.data.model.SwapStatus
import com.ivarna.mkm.data.model.UfsStatus
import com.ivarna.mkm.ui.components.InfoRow
import com.ivarna.mkm.ui.components.SectionHeader
import com.ivarna.mkm.ui.components.SwapConfigDialog
import com.ivarna.mkm.ui.viewmodel.RamViewModel
import com.ivarna.mkm.ui.components.BootToggleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamScreen(viewModel: RamViewModel = viewModel(), onOpenDrawer: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val bootEnabled by viewModel.bootEnabled.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    var showSwapDialog by remember { mutableStateOf(false) }

    if (showSwapDialog) {
        val currentSwap = uiState?.swap
        val defaultPath = "/data/local/tmp/swapfile"
        // Don't suggest /dev/block paths (zram) as they are not safe/valid for file creation
        val suggestion = currentSwap?.path?.takeIf { it != "None" && !it.startsWith("/dev/") } ?: defaultPath
        
        SwapConfigDialog(
            initialSize = if (currentSwap?.isActive == true && currentSwap.path == suggestion) 1024 else 2048,
            initialPath = suggestion,
            onDismiss = { showSwapDialog = false },
            onConfirm = { path, size ->
                viewModel.applySwap(path, size)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                MediumTopAppBar(
                    title = { 
                        Text(
                            "RAM Management",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                    },
                    navigationIcon = {
                         IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(com.ivarna.mkm.R.string.menu))
                        }
                    },
                    actions = {
                        // Refresh button removed in favor of pull-to-refresh
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showSwapDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(com.ivarna.mkm.R.string.create_new_swap)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        ) { innerPadding ->
            PullToRefreshWrapper(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    BootToggleCard(
                        enabled = bootEnabled,
                        onToggle = { viewModel.toggleBootEnabled(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Removed top progress indicator in favor of LoadingOverlay

                    errorMessage?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearError() }) {
                                    Text(stringResource(com.ivarna.mkm.R.string.dismiss), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    uiState?.let { data ->
                        MemoryOverviewCard(data.memory)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        SectionHeader(stringResource(com.ivarna.mkm.R.string.swap_configuration))
                        SwapConfigurationCard(
                            swap = data.swap,
                            onConfigureClick = { showSwapDialog = true },
                            onDisableClick = { viewModel.disableSwap(data.swap.path) },
                            onRemoveClick = { path -> viewModel.removeSwap(path) }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(stringResource(com.ivarna.mkm.R.string.ddr_frequency_tuning))
                        DevfreqTuningCard(
                            devfreq = data.devfreq,
                            onGovernorSelected = { path, gov -> viewModel.setDevfreqGovernor(path, gov) },
                            onFreqSelected = { path, freq -> viewModel.setDevfreqFreq(path, freq) }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        SectionHeader(stringResource(com.ivarna.mkm.R.string.memory_details))
                        MemoryDetailsCard(data.memory)
                        
                        Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                    }
                }
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = "Processing...")
        }
    }
}

@Composable
fun MemoryOverviewCard(memory: MemoryStatus) {
    val animatedProgress by animateFloatAsState(
        targetValue = memory.usagePercent,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = stringResource(com.ivarna.mkm.R.string.progress)
    )

    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${memory.usedUi} / ${memory.totalUi}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Spacer(modifier = Modifier.height(12.dp))
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${(memory.usagePercent * 100).toInt()}% Used · ${memory.freeUi} Free",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun SwapConfigurationCard(
    swap: SwapStatus,
    onConfigureClick: () -> Unit,
    onDisableClick: () -> Unit,
    onRemoveClick: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (swap.isActive) {
                ActiveSwapContent(swap, onConfigureClick, onDisableClick, onRemoveClick)
            } else {
                NoSwapContent(onConfigureClick)
            }
        }
    }
}

@Composable
fun ActiveSwapContent(
    swap: SwapStatus,
    onConfigureClick: () -> Unit,
    onDisableClick: () -> Unit,
    onRemoveClick: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(com.ivarna.mkm.R.string.swap_usage),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${swap.usedUi} / ${swap.totalUi}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(com.ivarna.mkm.R.string.swap_options))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // If there is specifically a file-based swap that we manage, show Disable/Delete
                // Otherwise only show Create New (which is handled by FAB)
                if (swap.path != "None") {
                    DropdownMenuItem(
                        text = { Text(stringResource(com.ivarna.mkm.R.string.disable_main_swap)) },
                        onClick = {
                            onDisableClick()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(com.ivarna.mkm.R.string.delete_main_swap_file)) },
                        onClick = {
                            onRemoveClick(swap.path)
                            showMenu = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))

    // List all active devices
    if (swap.devices.isNotEmpty()) {
        swap.devices.forEach { device ->
            SwapDeviceRow(device = device, onRemove = { onRemoveClick(device.path) })
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    } else {
         // Fallback for when detailed info is missing but swap is active (shouldn't happen often)
        Text(
            text = "Active: ${swap.path}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Button(
        onClick = onConfigureClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(com.ivarna.mkm.R.string.create_new_swap_resize))
    }
}

@Composable
fun SwapDeviceRow(device: SwapDeviceInfo, onRemove: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                 Text(
                    text = device.path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                 Text(
                    text = device.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            if (device.type == "file") {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(com.ivarna.mkm.R.string.delete_swap_file),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
             Text(
                text = "${device.usedUi} used of ${device.sizeUi}",
                style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Prio: ${device.priority}",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NoSwapContent(onConfigureClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(com.ivarna.mkm.R.string.no_active_swap),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(com.ivarna.mkm.R.string.create_swap_file),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onConfigureClick) {
            Text(stringResource(com.ivarna.mkm.R.string.configure_swap))
        }
    }
}

@Composable
fun MemoryDetailsCard(memory: MemoryStatus) {
    ElevatedCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(label = stringResource(com.ivarna.mkm.R.string.available), value = memory.availableUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = stringResource(com.ivarna.mkm.R.string.cached), value = memory.cachedUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = stringResource(com.ivarna.mkm.R.string.active), value = memory.activeUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = stringResource(com.ivarna.mkm.R.string.inactive), value = memory.inactiveUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = stringResource(com.ivarna.mkm.R.string.buffers), value = memory.buffersUi)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(enabled = false) {}, // Block clicks
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(16.dp)
                )
                .padding(24.dp)
        ) {
            LoadingIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = message
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevfreqTuningCard(
    devfreq: com.ivarna.mkm.data.model.DevfreqStatus,
    onGovernorSelected: (String, String) -> Unit,
    onFreqSelected: (String, String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Column(modifier = Modifier.weight(1f)) {
                    val title = if (devfreq.isSupported) stringResource(com.ivarna.mkm.R.string.ddr_controller) + ": " + devfreq.controllerPath.substringAfterLast("/") else stringResource(com.ivarna.mkm.R.string.ddr_controller)
                    val subtitle = if (devfreq.isSupported) devfreq.controllerPath else stringResource(com.ivarna.mkm.R.string.not_detected_or_supported)
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                     Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (devfreq.isSupported) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(com.ivarna.mkm.R.string.bandwidth_governor),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        value = devfreq.currentGovernor,
                        onValueChange = {},
                        label = { Text(stringResource(com.ivarna.mkm.R.string.select_mode)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        devfreq.availableGovernors.forEach { gov ->
                            DropdownMenuItem(
                                text = { Text(gov) },
                                onClick = {
                                    onGovernorSelected(devfreq.controllerPath, gov)
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                if (devfreq.availableFrequencies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(com.ivarna.mkm.R.string.frequencies),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Current: ${devfreq.currentFreq}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(com.ivarna.mkm.R.string.ddr_reboot_warning),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Freq Dropdown (only relevant if we want to force specific freq)
                     var freqExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = freqExpanded,
                        onExpandedChange = { freqExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = devfreq.currentFreq.takeIf { it != "0" && it.isNotBlank() } ?: "Set Specific Frequency",
                            onValueChange = {},
                            label = { Text(stringResource(com.ivarna.mkm.R.string.force_frequency_userspace)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = freqExpanded,
                            onDismissRequest = { freqExpanded = false },
                        ) {
                            devfreq.availableFrequencies.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq) },
                                    onClick = {
                                        onFreqSelected(devfreq.controllerPath, freq)
                                        freqExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                }

                if (devfreq.availableGovernors.isEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(com.ivarna.mkm.R.string.no_governors_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    if (devfreq.debugInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = stringResource(com.ivarna.mkm.R.string.debug_info),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = devfreq.debugInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(com.ivarna.mkm.R.string.no_ddr_controller),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                
                if (devfreq.debugInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                             Text(
                                text = stringResource(com.ivarna.mkm.R.string.debug_info),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                             androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = devfreq.debugInfo,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}