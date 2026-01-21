package com.ivarna.mkm.ui.screens

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuCore
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.viewmodel.CpuViewModel
import com.ivarna.mkm.utils.ShellUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuScreen(viewModel: CpuViewModel = viewModel()) {
    val cpuStatus by viewModel.cpuStatus.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedClusterForGovernor by remember { mutableStateOf<CpuCluster?>(null) }
    var selectedClusterForMaxFreq by remember { mutableStateOf<CpuCluster?>(null) }
    var selectedClusterForMinFreq by remember { mutableStateOf<CpuCluster?>(null) }
    
    var selectedCoreForSettings by remember { mutableStateOf<com.ivarna.mkm.data.model.CpuCore?>(null) }
    var showCoreGovernorSheet by remember { mutableStateOf(false) }
    var showCoreMaxFreqSheet by remember { mutableStateOf(false) }
    var showCoreMinFreqSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            cpuStatus.cpuName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "CPU Management",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item {
                HeroUsageCard(
                    title = "OVERALL UTILIZATION",
                    usage = cpuStatus.overallUsage,
                    mainValue = "${(cpuStatus.overallUsage * 100).toInt()}%",
                    subValue = "${cpuStatus.totalCores} Processors Active"
                )
            }

            item {
                SectionHeader("CPU Clusters")
            }

            items(cpuStatus.clusters) { cluster ->
                CpuClusterCard(
                    cluster = cluster,
                    onGovernorClick = { selectedClusterForGovernor = cluster },
                    onMaxFreqClick = { selectedClusterForMaxFreq = cluster },
                    onMinFreqClick = { selectedClusterForMinFreq = cluster }
                )
            }

            item {
                SectionHeader("Core Status Monitoring")
            }

            item {
                CoreStatusGrid(
                    cores = cpuStatus.clusters.flatMap { it.cores },
                    onCoreClick = { selectedCoreForSettings = it }
                )
            }
        }

        // Selection sheets
        selectedClusterForGovernor?.let { cluster ->
            SelectionBottomSheet(
                title = "Select Governor",
                items = cluster.availableGovernors,
                selectedItem = cluster.governor,
                onDismiss = { selectedClusterForGovernor = null },
                onItemSelected = {
                    viewModel.setGovernor(cluster.id, it)
                    selectedClusterForGovernor = null
                }
            )
        }

        selectedClusterForMaxFreq?.let { cluster ->
            SelectionBottomSheet(
                title = "Select Max Frequency",
                items = cluster.availableFrequencies,
                selectedItem = cluster.rawMaxFreq,
                onDismiss = { selectedClusterForMaxFreq = null },
                onItemSelected = {
                    viewModel.setFrequency(cluster.id, it, true)
                    selectedClusterForMaxFreq = null
                },
                itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
            )
        }

        selectedClusterForMinFreq?.let { cluster ->
            SelectionBottomSheet(
                title = "Select Min Frequency",
                items = cluster.availableFrequencies,
                selectedItem = cluster.rawMinFreq,
                onDismiss = { selectedClusterForMinFreq = null },
                onItemSelected = {
                    viewModel.setFrequency(cluster.id, it, false)
                    selectedClusterForMinFreq = null
                },
                itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
            )
        }

        selectedCoreForSettings?.let { core ->
            ModalBottomSheet(
                onDismissRequest = { selectedCoreForSettings = null },
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 48.dp)
                ) {
                    Text(
                        text = "Core ${core.id} Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingRow(
                            label = "GOVERNOR",
                            value = core.governor,
                            onClick = { showCoreGovernorSheet = true }
                        )
                        SettingRow(
                            label = "MAX FREQUENCY",
                            value = core.maxFreq,
                            onClick = { showCoreMaxFreqSheet = true }
                        )
                        SettingRow(
                            label = "MIN FREQUENCY",
                            value = core.minFreq,
                            onClick = { showCoreMinFreqSheet = true }
                        )
                    }
                }
            }

            if (showCoreGovernorSheet) {
                SelectionBottomSheet(
                    title = "Core ${core.id} Governor",
                    items = core.availableGovernors,
                    selectedItem = core.governor,
                    onDismiss = { showCoreGovernorSheet = false },
                    onItemSelected = {
                        viewModel.setGovernorForCore(core.id, it)
                        showCoreGovernorSheet = false
                    }
                )
            }

            if (showCoreMaxFreqSheet) {
                SelectionBottomSheet(
                    title = "Core ${core.id} Max Frequency",
                    items = core.availableFrequencies,
                    selectedItem = core.rawMaxFreq,
                    onDismiss = { showCoreMaxFreqSheet = false },
                    onItemSelected = {
                        viewModel.setFrequencyForCore(core.id, it, true)
                        showCoreMaxFreqSheet = false
                    },
                    itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
                )
            }

            if (showCoreMinFreqSheet) {
                SelectionBottomSheet(
                    title = "Core ${core.id} Min Frequency",
                    items = core.availableFrequencies,
                    selectedItem = core.rawMinFreq,
                    onDismiss = { showCoreMinFreqSheet = false },
                    onItemSelected = {
                        viewModel.setFrequencyForCore(core.id, it, false)
                        showCoreMinFreqSheet = false
                    },
                    itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cluster ${cluster.id}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Cores ${cluster.coreRange.first}-${cluster.coreRange.last}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingRow(
                    label = "GOVERNOR",
                    value = cluster.governor,
                    onClick = onGovernorClick
                )
                SettingRow(
                    label = "MAX FREQUENCY",
                    value = cluster.maxFreq,
                    onClick = onMaxFreqClick
                )
                SettingRow(
                    label = "MIN FREQUENCY",
                    value = cluster.minFreq,
                    onClick = onMinFreqClick
                )
                InfoRow(
                    label = "Current Clock Speed",
                    value = cluster.currentFreq
                )
            }
        }
    }
}

@Composable
fun CoreStatusGrid(cores: List<CpuCore>, onCoreClick: (CpuCore) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cores.chunked(2).forEach { rowCores ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCores.forEach { core ->
                    CoreMiniCard(
                        core = core,
                        onClick = { onCoreClick(core) },
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

