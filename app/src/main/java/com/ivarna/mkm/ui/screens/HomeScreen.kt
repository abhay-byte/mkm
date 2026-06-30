package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SdStorage
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.HomeData
import com.ivarna.mkm.ui.components.SectionHeader
import com.ivarna.mkm.ui.components.StatCard
import com.ivarna.mkm.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
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
                        stringResource(com.ivarna.mkm.R.string.minimal_kernel_manager),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                     IconButton(onClick = onOpenDrawer) {
                         Icon(Icons.Filled.Menu, contentDescription = stringResource(com.ivarna.mkm.R.string.menu))
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

                SectionHeader(stringResource(com.ivarna.mkm.R.string.quick_status_monitor))

                QuickStatsGrid(data)

                Spacer(modifier = Modifier.height(20.dp))

                val isAccessGranted = data.overview.isShizukuActive || data.overview.isRootActive
                QuickAccessCard(
                    isAccessGranted = isAccessGranted,
                    onNavigate = onNavigate
                )

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
                        text = stringResource(com.ivarna.mkm.R.string.kernel_format, overview.kernelVersion),
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
                    label = stringResource(com.ivarna.mkm.R.string.shizuku),
                    isActive = overview.isShizukuActive,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    label = stringResource(com.ivarna.mkm.R.string.root),
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
                        text = stringResource(com.ivarna.mkm.R.string.access_denied),
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
                text = "$label: ${if (isActive) stringResource(com.ivarna.mkm.R.string.active) else stringResource(com.ivarna.mkm.R.string.inactive)}",
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
                title = stringResource(com.ivarna.mkm.R.string.ram),
                value = "${data.memory.usedUi} / ${data.memory.totalUi}",
                subValue = "${(data.memory.usagePercent * 100).toInt()}% Used",
                progress = data.memory.usagePercent,
                icon = Icons.Default.Dns,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(com.ivarna.mkm.R.string.cpu),
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
                title = stringResource(com.ivarna.mkm.R.string.gpu),
                value = data.gpu.currentFreq,
                subValue = if (!data.gpu.frequencyAvailable && data.gpu.freqRequiresRoot)
                    "${(data.gpu.loadPercent * 100).toInt()}% Load • Root needed for freq"
                else
                    "${(data.gpu.loadPercent * 100).toInt()}% Load",
                progress = data.gpu.loadPercent,
                icon = Icons.Default.VideogameAsset,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(com.ivarna.mkm.R.string.swap),
                value = data.swap.totalUi,
                subValue = if (data.swap.isActive) "Active (${(data.swap.usagePercent * 100).toInt()}%)" else stringResource(com.ivarna.mkm.R.string.inactive),
                progress = data.swap.usagePercent,
                icon = Icons.Default.SwapHoriz,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAccessCard(
    isAccessGranted: Boolean,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val alwaysEnabled = setOf("battery", "overlay", "settings")
    val items = listOf(
        QuickAccessItem(stringResource(com.ivarna.mkm.R.string.ram), "ram", Icons.Default.Dns),
        QuickAccessItem(stringResource(com.ivarna.mkm.R.string.cpu), "cpu", Icons.Default.DeveloperBoard),
        QuickAccessItem(stringResource(com.ivarna.mkm.R.string.gpu), "gpu", Icons.Default.VideogameAsset),
        QuickAccessItem("Storage", "storage", Icons.Default.SdStorage),
        QuickAccessItem(stringResource(com.ivarna.mkm.R.string.power), "power", Icons.Default.Bolt),
        QuickAccessItem("Battery", "battery", Icons.Default.BatteryFull),
        QuickAccessItem("Overlay", "overlay", Icons.Default.SwapHoriz),
        QuickAccessItem("Settings", "settings", Icons.Default.Settings)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(com.ivarna.mkm.R.string.quick_access),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!isAccessGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(com.ivarna.mkm.R.string.limited),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.forEach { item ->
                    val enabled = isAccessGranted || item.route in alwaysEnabled
                    QuickAccessTile(
                        label = item.label,
                        icon = item.icon,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (enabled) {
                                onNavigate(item.route)
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Locked: Root or Shizuku access required",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (enabled) 1.dp else 0.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!enabled) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(com.ivarna.mkm.R.string.locked),
                        modifier = Modifier
                            .padding(2.dp)
                            .size(10.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private data class QuickAccessItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)
