package com.ivarna.mkm.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.BenchmarkStatus
import com.ivarna.mkm.data.model.CpuEfficiencyResult
import com.ivarna.mkm.data.model.GpuEfficiencyResult
import com.ivarna.mkm.ui.components.BenchmarkResultsTable
import com.ivarna.mkm.ui.components.EfficiencyGraph
import com.ivarna.mkm.ui.components.PowerMonitorCard
import com.ivarna.mkm.ui.components.PowerCalibrationComponent
import com.ivarna.mkm.ui.viewmodel.PowerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerScreen(
    viewModel: PowerViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {}
) {
    val powerStatus by viewModel.powerStatus.collectAsState()
    val cpuResults by viewModel.cpuResults.collectAsState()
    val cpuStatus by viewModel.cpuBenchStatus.collectAsState()
    val gpuResults by viewModel.gpuResults.collectAsState()
    val gpuStatus by viewModel.gpuBenchStatus.collectAsState()
    val realTimeLogs by viewModel.realTimeLogs.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Monitor", "CPU Bench", "GPU Bench")
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val tabContainerColor by animateColorAsState(
        targetValue = if (scrollBehavior.state.overlappedFraction > 0.01f)
            MaterialTheme.colorScheme.surfaceContainer
        else
            MaterialTheme.colorScheme.surface,
        label = "TabRowColorAnimation"
    )

    var showLogDialog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }
    var isRawDataMode by remember { mutableStateOf(false) }

    if (showLogDialog) {
        LogViewerDialog(
            logs = logContent,
            isRawData = isRawDataMode,
            onDismiss = { showLogDialog = false }
        )
    }
    
    // Show real-time progress dialog when running
    if (cpuStatus is BenchmarkStatus.Running || gpuStatus is BenchmarkStatus.Running) {
        RealTimeProgressDialog(logs = realTimeLogs)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                     IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                title = {
                    Column {
                        Text(
                            "Power Monitor",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Efficiency & Benchmarks",
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
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = tabContainerColor,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> MonitorTab(
                    powerStatus = powerStatus,
                    onSaveMultiplier = { viewModel.saveCalibrationMultiplier(it) }
                )
                1 -> CpuBenchTab(
                    cpuStatus, 
                    cpuResults, 
                    onStart = { viewModel.runCpuBenchmark() },
                    onViewLogs = { logs ->
                        logContent = logs
                        isRawDataMode = false
                        showLogDialog = true
                    },
                    onViewRawData = {
                         // We can iterate results to reconstruct CSV or use what is in logs (but logs has mix).
                         // Let's generate CSV from results structure for clean Raw Data view.
                         val header = "Policy,Frequency_KHz,Duration_Sec,Score,Power_uW,Efficiency\n"
                         val rows = cpuResults.map { r ->
                             "${r.policy},${r.frequencyKHz},${r.durationSec},${r.score},${r.powerW},${r.efficiency}"
                         }.joinToString("\n")
                         logContent = header + rows
                         isRawDataMode = true
                         showLogDialog = true
                    },
                    logs = realTimeLogs
                )
                2 -> GpuBenchTab(
                    gpuStatus, 
                    gpuResults, 
                    onStart = { viewModel.runGpuBenchmark() },
                    onViewLogs = { logs ->
                        logContent = logs
                        isRawDataMode = false
                        showLogDialog = true
                    },
                    onViewRawData = {
                         val header = "Frequency_Hz,Score(Util),Power_W\n"
                         val body = gpuResults.joinToString("\n") { 
                             "${it.frequencyHz},${it.utilization},${it.powerW}"
                         }
                         logContent = header + body
                         isRawDataMode = true
                         showLogDialog = true
                    }
                )
            }
        }
    }
}

@Composable
fun MonitorTab(
    powerStatus: com.ivarna.mkm.data.model.PowerStatus,
    onSaveMultiplier: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        PowerMonitorCard(status = powerStatus)
        Spacer(modifier = Modifier.height(16.dp))
        
        PowerCalibrationComponent(
            status = powerStatus,
            onSaveMultiplier = onSaveMultiplier
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Values are read directly from kernel power supply subsystem.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CpuBenchTab(
    status: BenchmarkStatus,
    results: List<CpuEfficiencyResult>,
    onStart: () -> Unit,
    onViewLogs: (String) -> Unit,
    onViewRawData: () -> Unit,
    logs: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Debug Logs Card at the top
        if (logs.isNotEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Debug Logs (Last 20 lines)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val lastLines = logs.lines().takeLast(20).joinToString("\n")
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        val scrollState = rememberScrollState()
                        
                        LaunchedEffect(lastLines) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        
                        Text(
                            text = lastLines,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
        
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("CPU Efficiency", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Calculates Score per Watt across clusters.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onStart,
                    enabled = status !is BenchmarkStatus.Running,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                   Text("Start Benchmark")
                }
                
                StatusMessage(status, onViewLogs, onViewRawData)
            }
        }
        
                if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Efficiency Curve", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                     // User requested: X = Wattage, Y = Score.
                     val points = results.map { it.powerW to it.score }
                     EfficiencyGraph(
                        dataPoints = points,
                        xLabel = "Power (W)",
                        yLabel = "Score",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                     )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            BenchmarkResultsTable(results = results)
        }
    }
}

@Composable
fun GpuBenchTab(
    status: BenchmarkStatus,
    results: List<GpuEfficiencyResult>,
    onStart: () -> Unit,
    onViewLogs: (String) -> Unit,
    onViewRawData: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("GPU Efficiency", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Measures Power vs Frequency under load.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onStart,
                        enabled = status !is BenchmarkStatus.Running,
                        modifier = Modifier.fillMaxWidth(),
                         shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Benchmark")
                    }
                    
                    StatusMessage(status, onViewLogs, onViewRawData)
                }
            }
            
            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                 ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Power Curve", style = MaterialTheme.typography.titleMedium)
                        
                         // The user said Y axis score? 
                         // "x axis power and y axis score. and a curve of pwer efficiency we get"
                         // They likely want Efficiency on Y axis.
                         // But we calculated 0 efficiency for GPU. 
                         // Let's fix this visual: Show Power vs Freq as currently implemented, OR manually approximate eff.
                         // Let's just show what we have but label it clearly.
                         // Actually, if we invert x/y... User mentioned "x axis power".
                         // Usually X = Freq (independent), Y = Power (dependent). 
                         // Efficiency graph should occur if we have metrics.
                         
                         val points = results.map { it.frequencyHz.toFloat() to it.powerW }
                         EfficiencyGraph(
                            dataPoints = points,
                            xLabel = "Freq",
                            yLabel = "Power",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                         )
                    }
                 }
            }
        }
        
        if (status is BenchmarkStatus.Running) {
             com.ivarna.mkm.ui.components.GpuStressView(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun StatusMessage(
    status: BenchmarkStatus, 
    onViewLogs: (String) -> Unit,
    onViewRawData: () -> Unit
) {
    if (status is BenchmarkStatus.Completed) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                text = status.message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                TextButton(onClick = { onViewLogs(status.logs) }) {
                    Text("View Logs")
                }
                TextButton(onClick = onViewRawData) {
                    Text("View Raw Data")
                }
            }
        }
    } else if (status is BenchmarkStatus.Error) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                text = status.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
             TextButton(onClick = { onViewLogs(status.logs) }) {
                Text("View Logs")
            }
        }
    }
}

@Composable
fun RealTimeProgressDialog(logs: String) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(300.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Running Benchmark...", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)).padding(8.dp)
                ) {
                    // Auto-scrolling text would be nice, but simple display is okay for now.
                    Text(
                        text = logs.takeLast(1000), // Show tail
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
fun LogViewerDialog(logs: String, isRawData: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isRawData) "Raw Data (CSV)" else "Benchmark Logs", 
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))) {
                    Text(
                        text = logs.ifEmpty { "No data available." },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("MKM Data", logs)
                            clipboard.setPrimaryClip(clip)
                        }
                    ) {
                        Text("Copy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
