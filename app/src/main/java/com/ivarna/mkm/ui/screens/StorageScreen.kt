package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Storage // Might not exist
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.ui.components.InfoRow
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import com.ivarna.mkm.ui.components.StatCard
import com.ivarna.mkm.ui.components.HeroUsageCard
import com.ivarna.mkm.ui.components.UfsTuningCard
import com.ivarna.mkm.ui.viewmodel.StorageViewModel
import com.ivarna.mkm.R
import com.ivarna.mkm.ui.components.BootToggleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(viewModel: StorageViewModel = viewModel(), onOpenDrawer: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val bootEnabled by viewModel.bootEnabled.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                    Text(
                        stringResource(com.ivarna.mkm.R.string.storage_info),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                scrollBehavior = scrollBehavior
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState?.let { state ->
                    BootToggleCard(
                        enabled = bootEnabled,
                        onToggle = { viewModel.toggleBootEnabled(it) }
                    )

                    val internalStorage = state.partitions.find { it.mountPoint == "/data" }
                    val systemStorage = state.partitions.find { it.mountPoint == "/system" }

                    if (internalStorage != null) {
                        HeroUsageCard(
                            title = stringResource(com.ivarna.mkm.R.string.internal_storage),
                            usage = internalStorage.usagePercent / 100f,
                            mainValue = "${internalStorage.usagePercent.toInt()}%",
                            subValue = "${internalStorage.used} used of ${internalStorage.total}"
                        )
                    }

                    if (systemStorage != null) {
                         StatCard(
                            title = stringResource(com.ivarna.mkm.R.string.system_partition),
                            value = systemStorage.used,
                            subValue = "of ${systemStorage.total} used",
                            progress = systemStorage.usagePercent / 100f,
                            icon = Icons.Default.SdStorage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    UfsTuningCard(
                        ufs = state.ufsStatus,
                        onGovernorSelected = { path, gov -> viewModel.setUfsGovernor(path, gov) },
                        onMinFreqSelected = { path, freq -> viewModel.setUfsMinFreq(path, freq) },
                        onMaxFreqSelected = { path, freq -> viewModel.setUfsMaxFreq(path, freq) }
                    )

                    Card(
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "Storage Info",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            InfoRow(label = stringResource(com.ivarna.mkm.R.string.type), value = state.type)
                            InfoRow(label = "Partition Count", value = state.partitions.size.toString())
                            
                            internalStorage?.let {
                                InfoRow(label = stringResource(com.ivarna.mkm.R.string.internal_total), value = it.total)
                                InfoRow(label = stringResource(com.ivarna.mkm.R.string.internal_free), value = it.free)
                                InfoRow(label = stringResource(com.ivarna.mkm.R.string.block_size), value = "${it.blockSize} bytes")
                            }
                        }
                    }
                }
            }
        }
    }
}
