package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuCore
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.components.ThermalCard
import com.ivarna.mkm.ui.viewmodel.CpuViewModel
import com.ivarna.mkm.utils.ShellUtils
import com.ivarna.mkm.ui.components.BootToggleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuScreen(viewModel: CpuViewModel = viewModel(), onOpenDrawer: () -> Unit = {}) {
    val cpuStatus by viewModel.cpuStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val bootEnabled by viewModel.bootEnabled.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedPolicyForGovernor by remember { mutableStateOf<Int?>(null) }
    var selectedPolicyForMaxFreq by remember { mutableStateOf<Int?>(null) }
    var selectedPolicyForMinFreq by remember { mutableStateOf<Int?>(null) }
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
                            cpuStatus.cpuName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            stringResource(com.ivarna.mkm.R.string.cpu_management),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        PullToRefreshWrapper(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(
                    top = 8.dp, // padding consumed by wrapper, just need top spacing
                    bottom = 32.dp,
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
            item {
                HeroUsageCard(
                    title = stringResource(com.ivarna.mkm.R.string.overall_utilization),
                    usage = cpuStatus.overallUsage,
                    mainValue = "${(cpuStatus.overallUsage * 100).toInt()}%",
                    subValue = stringResource(com.ivarna.mkm.R.string.processors_active_format, cpuStatus.totalCores)
                )
            }

            // Thermal Status
            item {
                val thermalStatus by viewModel.thermalStatus.collectAsState()
                // Show card if we have data OR if we are refreshing (to show loading state)
                if (thermalStatus.zones.isNotEmpty() || isRefreshing) {
                    ThermalCard(
                        status = thermalStatus,
                        isLoading = isRefreshing && thermalStatus.zones.isEmpty(), // Only show specific loading if empty
                        onSetLimit = { limit -> viewModel.setThermalLimit(limit) },
                        onDisableThrottling = { viewModel.disableThrottling() }
                    )
                }
            }


            item {
                Spacer(modifier = Modifier.height(16.dp))
                BootToggleCard(
                    enabled = bootEnabled,
                    onToggle = { viewModel.toggleBootEnabled(it) }
                )
            }

            item {
                SectionHeader(stringResource(com.ivarna.mkm.R.string.cpu_clusters))
            }

            items(cpuStatus.clusters) { cluster ->
                CpuClusterCard(
                    cluster = cluster,
                    onGovernorClick = { selectedPolicyForGovernor = cluster.id },
                    onMaxFreqClick = { selectedPolicyForMaxFreq = cluster.id },
                    onMinFreqClick = { selectedPolicyForMinFreq = cluster.id }
                )
            }

            item {
                SectionHeader(stringResource(com.ivarna.mkm.R.string.core_status_monitoring))
            }

            item {
                CoreStatusGrid(
                    cores = cpuStatus.clusters.flatMap { it.cores }
                )
            }
            }
        }

        // Selection sheets
        selectedPolicyForGovernor?.let { policyId ->
            cpuStatus.clusters.firstOrNull { it.id == policyId }?.let { cluster ->
            SelectionBottomSheet(
                title = stringResource(com.ivarna.mkm.R.string.select_governor),
                items = cluster.availableGovernors,
                selectedItem = cluster.governor,
                onDismiss = { sheetError = null; selectedPolicyForGovernor = null },
                onItemSelected = {
                    sheetError = null
                    applyingControl = "cpu-${cluster.id}-governor"
                    viewModel.setGovernor(cluster.id, it) { result ->
                        applyingControl = null
                        if (result is com.ivarna.mkm.data.model.ApplyResult.Failed) sheetError = result.message()
                        else {
                            selectedPolicyForGovernor = null
                            if (result is com.ivarna.mkm.data.model.ApplyResult.Adjusted) android.widget.Toast.makeText(context, result.message(), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                isApplying = applyingControl == "cpu-${cluster.id}-governor",
                errorMessage = sheetError
            )
            }
        }

        selectedPolicyForMaxFreq?.let { policyId ->
            cpuStatus.clusters.firstOrNull { it.id == policyId }?.let { cluster ->
            SelectionBottomSheet(
                title = stringResource(com.ivarna.mkm.R.string.select_max_frequency),
                items = cluster.availableFrequencies,
                selectedItem = cluster.rawMaxFreq,
                onDismiss = { sheetError = null; selectedPolicyForMaxFreq = null },
                onItemSelected = {
                    sheetError = null
                    applyingControl = "cpu-${cluster.id}-max"
                    viewModel.setFrequency(cluster.id, it, true) { result ->
                        applyingControl = null
                        if (result is com.ivarna.mkm.data.model.ApplyResult.Failed) sheetError = result.message()
                        else {
                            selectedPolicyForMaxFreq = null
                            if (result is com.ivarna.mkm.data.model.ApplyResult.Adjusted) android.widget.Toast.makeText(context, result.message(), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) },
                isApplying = applyingControl == "cpu-${cluster.id}-max",
                errorMessage = sheetError
            )
            }
        }

        selectedPolicyForMinFreq?.let { policyId ->
            cpuStatus.clusters.firstOrNull { it.id == policyId }?.let { cluster ->
            SelectionBottomSheet(
                title = stringResource(com.ivarna.mkm.R.string.select_min_frequency),
                items = cluster.availableFrequencies,
                selectedItem = cluster.rawMinFreq,
                onDismiss = { sheetError = null; selectedPolicyForMinFreq = null },
                onItemSelected = {
                    sheetError = null
                    applyingControl = "cpu-${cluster.id}-min"
                    viewModel.setFrequency(cluster.id, it, false) { result ->
                        applyingControl = null
                        if (result is com.ivarna.mkm.data.model.ApplyResult.Failed) sheetError = result.message()
                        else {
                            selectedPolicyForMinFreq = null
                            if (result is com.ivarna.mkm.data.model.ApplyResult.Adjusted) android.widget.Toast.makeText(context, result.message(), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) },
                isApplying = applyingControl == "cpu-${cluster.id}-min",
                errorMessage = sheetError
            )
            }
        }
    }
}

@Composable
fun CpuClusterCard(
    cluster: CpuCluster,
    onGovernorClick: () -> Unit,
    onMaxFreqClick: () -> Unit,
    onMinFreqClick: () -> Unit
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(com.ivarna.mkm.R.string.cluster_id_format, cluster.id),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    val policyCpus = cluster.affectedCpus.ifEmpty { cluster.relatedCpus }
                    Text(
                        text = if (policyCpus.isNotEmpty()) {
                            stringResource(com.ivarna.mkm.R.string.policy_cpus_format, policyCpus.joinToString(","))
                        } else {
                            stringResource(com.ivarna.mkm.R.string.policy_id_format, cluster.id)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingRow(
                    label = stringResource(com.ivarna.mkm.R.string.governor),
                    value = cluster.governor,
                    onClick = onGovernorClick,
                    enabled = cluster.governorWritable && cluster.availableGovernors.isNotEmpty(),
                    disabledReason = cluster.governorReason
                )
                SettingRow(
                    label = stringResource(com.ivarna.mkm.R.string.max_frequency),
                    value = cluster.maxFreq,
                    onClick = onMaxFreqClick,
                    enabled = cluster.maxWritable && cluster.availableFrequencies.isNotEmpty(),
                    disabledReason = cluster.maxReason
                )
                SettingRow(
                    label = stringResource(com.ivarna.mkm.R.string.min_frequency),
                    value = cluster.minFreq,
                    onClick = onMinFreqClick,
                    enabled = cluster.minWritable && cluster.availableFrequencies.isNotEmpty(),
                    disabledReason = cluster.minReason
                )
                InfoRow(
                    label = stringResource(com.ivarna.mkm.R.string.current_clock_speed),
                    value = cluster.currentFreq
                )
                if (cluster.availableGovernors.isEmpty() || cluster.availableFrequencies.isEmpty()) {
                    Text(
                        text = stringResource(com.ivarna.mkm.R.string.tuning_unavailable_kernel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CoreStatusGrid(cores: List<CpuCore>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cores.chunked(2).forEach { rowCores ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCores.forEach { core ->
                    CoreMiniCard(
                        core = core,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowCores.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
