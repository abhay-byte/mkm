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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
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
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
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
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { /* TODO: Overflow menu */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        uiState?.let { data ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
                
                SystemOverviewCard(data.overview)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("QUICK STATUS MONITOR")
                
                QuickStatsGrid(data)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("FREQUENT OPERATIONS")
                
                QuickActionsList()
                
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 32.dp))
            }
        }
    }
}

@Composable
fun SystemOverviewCard(overview: com.ivarna.mkm.data.model.SystemOverview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
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
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (overview.isShizukuActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                val text = if (overview.isShizukuActive) "Shizuku: Active" else "Shizuku: Inactive"
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
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
fun QuickActionsList() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            QuickActionItem(
                icon = Icons.Default.Refresh,
                title = "Refresh Stats",
                onClick = {}
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
