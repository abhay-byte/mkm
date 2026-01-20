package com.ivarna.mkm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.GpuStatus
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.viewmodel.GpuViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuScreen(viewModel: GpuViewModel = viewModel()) {
    val gpuStatus by viewModel.gpuStatus.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showGovernorSheet by remember { mutableStateOf(false) }
    var showMaxFreqSheet by remember { mutableStateOf(false) }
    var showMinFreqSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "GPU Management",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                actions = {
                    IconButton(onClick = { /* ViewModel refresh if applicable */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 8.dp))

            HeroUsageCard(
                title = "GPU UTILIZATION",
                usage = gpuStatus.loadPercent,
                mainValue = "${(gpuStatus.loadPercent * 100).toInt()}%",
                subValue = gpuStatus.currentFreq
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Graphics Information")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(label = "Model", value = gpuStatus.model)
                    InfoRow(label = "Current Frequency", value = gpuStatus.currentFreq)
                    InfoRow(label = "Governor", value = gpuStatus.governor)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Performance Controls")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    SettingRow(
                        label = "GPU Governor",
                        value = gpuStatus.governor,
                        onClick = { showGovernorSheet = true }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        label = "Maximum Frequency",
                        value = gpuStatus.maxFreq,
                        onClick = { showMaxFreqSheet = true }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        label = "Minimum Frequency",
                        value = gpuStatus.minFreq,
                        onClick = { showMinFreqSheet = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Selection Sheets
        if (showGovernorSheet) {
            SelectionBottomSheet(
                title = "Select GPU Governor",
                items = gpuStatus.availableGovernors,
                selectedItem = gpuStatus.governor,
                onDismiss = { showGovernorSheet = false },
                onItemSelected = {
                    viewModel.setGovernor(it)
                    showGovernorSheet = false
                }
            )
        }

        if (showMaxFreqSheet) {
            SelectionBottomSheet(
                title = "Maximum Frequency",
                items = gpuStatus.availableFrequencies,
                selectedItem = gpuStatus.rawMaxFreq,
                onDismiss = { showMaxFreqSheet = false },
                onItemSelected = {
                    viewModel.setFrequency(it, true)
                    showMaxFreqSheet = false
                },
                itemLabel = { formatFreq(it) }
            )
        }

        if (showMinFreqSheet) {
            SelectionBottomSheet(
                title = "Minimum Frequency",
                items = gpuStatus.availableFrequencies,
                selectedItem = gpuStatus.rawMinFreq,
                onDismiss = { showMinFreqSheet = false },
                onItemSelected = {
                    viewModel.setFrequency(it, false)
                    showMinFreqSheet = false
                },
                itemLabel = { formatFreq(it) }
            )
        }
    }
}

private fun formatFreq(freq: String): String {
    val f = freq.toLongOrNull() ?: return freq
    return if (f >= 1000000) "${f / 1000000} GHz"
    else if (f >= 1000) "${f / 1000} MHz"
    else "$f KHz"
}
