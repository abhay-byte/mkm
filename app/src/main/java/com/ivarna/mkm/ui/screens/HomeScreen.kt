package com.ivarna.mkm.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.ui.viewmodel.HomeViewModel
import com.ivarna.mkm.data.HomeData
import com.ivarna.mkm.ui.components.QuickActionItem
import com.ivarna.mkm.ui.components.SectionHeader
import com.ivarna.mkm.ui.components.StatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToPower: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { 
                    Text(
                        "Minimal Kernel Manager",
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
                    IconButton(onClick = { /* TODO: Overflow menu */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        uiState?.let { data ->
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
                
                SystemOverviewCard(data.overview, onCheckAgain = { viewModel.refresh() })
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("QUICK STATUS MONITOR")
                
                QuickStatsGrid(data)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("FREQUENT OPERATIONS")
                
                QuickActionsList(onNavigateToPower)
                
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 32.dp))
            }
        }
    }
}
}

@Composable
fun SystemOverviewCard(
    overview: com.ivarna.mkm.data.model.SystemOverview,
    onCheckAgain: () -> Unit
) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = overview.deviceName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Kernel: ${overview.kernelVersion}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                // Check again button removed
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusBadge(
                    label = "Shizuku",
                    isActive = overview.isShizukuActive,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    label = "Root",
                    isActive = overview.isRootActive,
                    modifier = Modifier.weight(1f)
                )
            }
            
            if (!overview.isShizukuActive && !overview.isRootActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Access denied. Please enable Shizuku or grant Root access to use all features.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, isActive: Boolean, modifier: Modifier = Modifier) {
    val color = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    val icon = if (isActive) Icons.Default.Settings else Icons.Default.Settings // Could use different icons
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: ${if (isActive) "Active" else "Inactive"}",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun QuickStatsGrid(data: HomeData) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "RAM",
                value = "${data.memory.usedUi} / ${data.memory.totalUi}",
                subValue = "${(data.memory.usagePercent * 100).toInt()}% Used",
                progress = data.memory.usagePercent,
                icon = Icons.Default.Memory,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "CPU",
                value = "${(data.cpu.overallUsage * 100).toInt()}%",
                subValue = "${data.cpu.totalCores} Cores Active",
                progress = data.cpu.overallUsage,
                icon = Icons.Default.DeveloperBoard,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "GPU",
                value = data.gpu.currentFreq,
                subValue = "${(data.gpu.loadPercent * 100).toInt()}% Load",
                progress = data.gpu.loadPercent,
                icon = Icons.Default.VideogameAsset,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Swap",
                value = data.swap.totalUi,
                subValue = if (data.swap.isActive) "Active (${(data.swap.usagePercent * 100).toInt()}%)" else "Inactive",
                progress = data.swap.usagePercent,
                icon = Icons.Default.SwapHoriz,
                modifier = Modifier.weight(1f)
            )
        }
    }
}



@Composable
fun QuickActionsList(onNavigateToPower: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Refresh stats action removed
            QuickActionItem(
                icon = Icons.Default.Bolt,
                title = "Power Efficiency Manager",
                onClick = onNavigateToPower
            )
            QuickActionItem(
                icon = Icons.Default.Add,
                title = "Create Swap",
                onClick = {}
            )
            QuickActionItem(
                icon = Icons.Default.Settings,
                title = "Advanced Settings",
                onClick = {}
            )
        }
    }
}
