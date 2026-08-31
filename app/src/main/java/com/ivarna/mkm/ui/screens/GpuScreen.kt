package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.viewmodel.GpuViewModel
import com.ivarna.mkm.utils.ShellUtils
import com.ivarna.mkm.ui.components.BootToggleCard
import com.ivarna.mkm.service.GameBoostRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuScreen(viewModel: GpuViewModel = viewModel(), onOpenDrawer: () -> Unit = {}) {
    val gpuStatus by viewModel.gpuStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val bootEnabled by viewModel.bootEnabled.collectAsState()
    val gameBoostState by GameBoostRegistry.state.collectAsState()
    val tuningOwned = gameBoostState !is com.ivarna.mkm.data.model.GameBoostState.Off
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showGovernorSheet by remember { mutableStateOf(false) }
    var showMaxFreqSheet by remember { mutableStateOf(false) }
    var showMinFreqSheet by remember { mutableStateOf(false) }
    var showTargetFreqSheet by remember { mutableStateOf(false) }
    var applyingControl by remember { mutableStateOf<String?>(null) }
    var sheetError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                            gpuStatus.model,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            gpuStatus.sysfsPath,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Refresh button removed
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        PullToRefreshWrapper(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
            Spacer(modifier = Modifier.height(8.dp))

            HeroUsageCard(
                title = stringResource(com.ivarna.mkm.R.string.gpu_utilization),
                usage = gpuStatus.loadPercent,
                mainValue = "${(gpuStatus.loadPercent * 100).toInt()}%",
                subValue = gpuStatus.currentFreq
            )

            Spacer(modifier = Modifier.height(16.dp))
            BootToggleCard(
                enabled = bootEnabled,
                onToggle = { viewModel.toggleBootEnabled(it) },
                interactive = !tuningOwned,
                subtitle = if (tuningOwned) stringResource(com.ivarna.mkm.R.string.game_boost_managed_reason) else stringResource(com.ivarna.mkm.R.string.apply_on_boot_desc)
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(stringResource(com.ivarna.mkm.R.string.graphics_information))
            ElevatedCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(label = stringResource(com.ivarna.mkm.R.string.renderer), value = gpuStatus.renderer)
                    InfoRow(label = stringResource(com.ivarna.mkm.R.string.system_path), value = gpuStatus.sysfsPath)
                    InfoRow(label = stringResource(com.ivarna.mkm.R.string.target_frequency), value = gpuStatus.targetFreq)
                    InfoRow(label = stringResource(com.ivarna.mkm.R.string.governor), value = gpuStatus.governor)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(stringResource(com.ivarna.mkm.R.string.performance_controls))
            
            // Warning Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(com.ivarna.mkm.R.string.warning),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(com.ivarna.mkm.R.string.instability_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column {
                    SettingRow(
                        label = stringResource(com.ivarna.mkm.R.string.gpu_governor),
                        value = gpuStatus.governor,
                        onClick = { showGovernorSheet = true },
                        enabled = !tuningOwned && gpuStatus.governorWritable && gpuStatus.availableGovernors.isNotEmpty(),
                        disabledReason = if (tuningOwned) "Managed by Game Boost. Disable Game Boost to edit." else gpuStatus.governorReason
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        label = stringResource(com.ivarna.mkm.R.string.maximum_frequency),
                        value = gpuStatus.maxFreq,
                        onClick = { showMaxFreqSheet = true },
                        enabled = !tuningOwned && gpuStatus.maxWritable && gpuStatus.availableFrequencies.isNotEmpty(),
                        disabledReason = if (tuningOwned) "Managed by Game Boost. Disable Game Boost to edit." else gpuStatus.maxReason
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        label = stringResource(com.ivarna.mkm.R.string.minimum_frequency),
                        value = gpuStatus.minFreq,
                        onClick = { showMinFreqSheet = true },
                        enabled = !tuningOwned && gpuStatus.minWritable && gpuStatus.availableFrequencies.isNotEmpty(),
                        disabledReason = if (tuningOwned) "Managed by Game Boost. Disable Game Boost to edit." else gpuStatus.minReason
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        label = stringResource(com.ivarna.mkm.R.string.target_frequency),
                        value = gpuStatus.targetFreq,
                        onClick = { showTargetFreqSheet = true },
                        enabled = !tuningOwned && gpuStatus.targetWritable && gpuStatus.availableFrequencies.isNotEmpty(),
                        disabledReason = if (tuningOwned) "Managed by Game Boost. Disable Game Boost to edit." else gpuStatus.targetReason
                    )
                }
            }

            gpuStatus.capabilityReason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))



            Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Selection Sheets
        if (showGovernorSheet) {
            SelectionBottomSheet(
                title = stringResource(com.ivarna.mkm.R.string.select_gpu_governor),
                items = gpuStatus.availableGovernors,
                selectedItem = gpuStatus.governor,
                onDismiss = { sheetError = null; showGovernorSheet = false },
                onItemSelected = {
                    sheetError = null
                    applyingControl = "gpu-governor"
                    viewModel.setGovernor(it) { result ->
                        applyingControl = null
                        if (result is com.ivarna.mkm.data.model.ApplyResult.Failed) sheetError = result.message()
                        else {
                            showGovernorSheet = false
                            if (result is com.ivarna.mkm.data.model.ApplyResult.Adjusted) android.widget.Toast.makeText(context, result.message(), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                isApplying = applyingControl == "gpu-governor",
                errorMessage = sheetError
            )
        }

        if (showMaxFreqSheet) {
            SelectionBottomSheet(
                title = stringResource(com.ivarna.mkm.R.string.maximum_frequency),
                items = gpuStatus.availableFrequencies,
                selectedItem = gpuStatus.rawMaxFreq,
                onDismiss = { sheetError = null; showMaxFreqSheet = false },
                onItemSelected = {
                    sheetError = null
                    applyingControl = "gpu-max"
                    viewModel.setFrequency(it, 1) { result ->
                        applyingControl = null
                        if (result is com.ivarna.mkm.data.model.ApplyResult.Failed) sheetError = result.message()
                        else {
                            showMaxFreqSheet = false
                            if (result is com.ivarna.mkm.data.model.ApplyResult.Adjusted) android.widget.Toast.makeText(context, result.message(), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                itemLabel = { formatFreq(it) },
                isApplying = applyingControl == "gpu-max",
                errorMessage = sheetError
            )
        }

        if (showMinFreqSheet) {
            SelectionBottomSheet(
                title = stringResource(com.ivarna.mkm.R.string.minimum_frequency),
                items = gpuStatus.availableFrequencies,
                selectedItem = gpuStatus.rawMinFreq,
                onDismiss = { sheetError = null; showMinFreqSheet = false },
                onItemSelected = {
                    sheetError = null
                    applyingControl = "gpu-min"
                    viewModel.setFrequency(it, 0) { result ->
                        applyingControl = null
                        if (result is com.ivarna.mkm.data.model.ApplyResult.Failed) sheetError = result.message()
                        else {
                            showMinFreqSheet = false
                            if (result is com.ivarna.mkm.data.model.ApplyResult.Adjusted) android.widget.Toast.makeText(context, result.message(), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                itemLabel = { formatFreq(it) },
                isApplying = applyingControl == "gpu-min",
                errorMessage = sheetError
            )
        }

        if (showTargetFreqSheet) {
            SelectionBottomSheet(
                title = stringResource(com.ivarna.mkm.R.string.target_frequency),
                items = gpuStatus.availableFrequencies,
                selectedItem = gpuStatus.rawTargetFreq,
                onDismiss = { sheetError = null; showTargetFreqSheet = false },
                onItemSelected = {
                    sheetError = null
                    applyingControl = "gpu-target"
                    viewModel.setFrequency(it, 2) { result ->
                        applyingControl = null
                        if (result is com.ivarna.mkm.data.model.ApplyResult.Failed) sheetError = result.message()
                        else {
                            showTargetFreqSheet = false
                            if (result is com.ivarna.mkm.data.model.ApplyResult.Adjusted) android.widget.Toast.makeText(context, result.message(), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                itemLabel = { formatFreq(it) },
                isApplying = applyingControl == "gpu-target",
                errorMessage = sheetError
            )
        }
    }
}

private fun formatFreq(freq: String): String {
    val f = freq.toLongOrNull() ?: return freq
    // Mali freqs are usually in Hz if they are that large (> 10MHz)
    val khz = if (f > 10000000) f / 1000 else f
    return ShellUtils.formatFreq(khz)
}
