package com.ivarna.mkm.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.MemoryStatus
import com.ivarna.mkm.data.model.SwapStatus
import com.ivarna.mkm.ui.components.InfoRow
import com.ivarna.mkm.ui.components.SectionHeader
import com.ivarna.mkm.ui.components.SwapConfigDialog
import com.ivarna.mkm.ui.viewmodel.RamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamScreen(viewModel: RamViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    var showSwapDialog by remember { mutableStateOf(false) }

    if (showSwapDialog) {
        val currentSwap = uiState?.swap
        SwapConfigDialog(
            initialSize = if (currentSwap?.isActive == true) 1024 else 2048,
            initialPath = currentSwap?.path?.takeIf { it != "None" } ?: "/data/local/tmp/swapfile",
            onDismiss = { showSwapDialog = false },
            onConfirm = { path, size ->
                viewModel.applySwap(path, size)
            }
        )
    }

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
                text = { Text("Create New Swap") },
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
            
            if (isProcessing) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }

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
                            Text("Dismiss", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            uiState?.let { data ->
                MemoryOverviewCard(data.memory)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Swap Configuration")
                SwapConfigurationCard(
                    swap = data.swap,
                    onConfigureClick = { showSwapDialog = true },
                    onDisableClick = { viewModel.disableSwap(data.swap.path) },
                    onRemoveClick = { viewModel.removeSwap(data.swap.path) }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Memory Details")
                MemoryDetailsCard(data.memory)
                
                Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
            }
        }
    }
}
}

@Composable
fun MemoryOverviewCard(memory: MemoryStatus) {
    val animatedProgress by animateFloatAsState(
        targetValue = memory.usagePercent,
        animationSpec = spring(stiffness = 50f),
        label = "progress"
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
    onRemoveClick: () -> Unit
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
    onRemoveClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Current Swap",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${swap.totalUi} · Active",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Swap options")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Disable Swap") },
                    onClick = {
                        onDisableClick()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Swap File") },
                    onClick = {
                        onRemoveClick()
                        showMenu = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
    }
    
    Text(
        text = swap.path,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    val animatedProgress by animateFloatAsState(
        targetValue = swap.usagePercent,
        animationSpec = spring(stiffness = 50f),
        label = "swap_progress"
    )
    
    Text(
        text = "Usage: ${swap.usedUi} / ${swap.totalUi}",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    Spacer(modifier = Modifier.height(8.dp))
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
    )
    
    Spacer(modifier = Modifier.height(20.dp))
    
    Button(
        onClick = onConfigureClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Reconfigure / Resize")
    }
}

@Composable
fun NoSwapContent(onConfigureClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Active Swap",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a swap file to increase available memory and improve system stability.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onConfigureClick) {
            Text("Configure Swap")
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
            InfoRow(label = "Available", value = memory.availableUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = "Cached", value = memory.cachedUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = "Active", value = memory.activeUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = "Inactive", value = memory.inactiveUi)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoRow(label = "Buffers", value = memory.buffersUi)
        }
    }
}
