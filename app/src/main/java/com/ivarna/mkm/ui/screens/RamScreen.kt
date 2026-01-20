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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.MemoryStatus
import com.ivarna.mkm.data.model.SwapStatus
import com.ivarna.mkm.ui.components.InfoRow
import com.ivarna.mkm.ui.components.SectionHeader
import com.ivarna.mkm.ui.components.SquigglyLinearProgressIndicator
import com.ivarna.mkm.ui.viewmodel.RamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamScreen(viewModel: RamViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RAM") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { /* TODO: Overflow menu */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* TODO: Navigate to swap creation */ },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create New Swap") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        uiState?.let { data ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                MemoryOverviewCard(data.memory)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Swap Configuration")
                SwapConfigurationCard(data.swap)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Memory Details")
                MemoryDetailsCard(data.memory)
                
                Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "${memory.usedUi} / ${memory.totalUi}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            SquigglyLinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
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
fun SwapConfigurationCard(swap: SwapStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (swap.isActive) {
                ActiveSwapContent(swap)
            } else {
                NoSwapContent()
            }
        }
    }
}

@Composable
fun ActiveSwapContent(swap: SwapStatus) {
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
    SquigglyLinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
    )
    
    Spacer(modifier = Modifier.height(20.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { /* TODO */ },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text("Disable Swap")
        }
        OutlinedButton(
            onClick = { /* TODO */ },
            modifier = Modifier.weight(1f)
        ) {
            Text("Reconfigure")
        }
    }
}

@Composable
fun NoSwapContent() {
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
        Button(onClick = { /* TODO */ }) {
            Text("Configure Swap")
        }
    }
}

@Composable
fun MemoryDetailsCard(memory: MemoryStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
