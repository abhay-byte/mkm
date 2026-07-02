package com.ivarna.mkm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.content.pm.ServiceInfo
import com.ivarna.mkm.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ivarna.mkm.data.SystemRepository
import com.ivarna.mkm.data.HomeData
import com.ivarna.mkm.data.provider.PowerCalibrationManager
import com.ivarna.mkm.utils.FpsMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var savedStateRegistryOwner: SavedStateRegistryOwner
    private val repository = SystemRepository(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val uiState = MutableStateFlow<HomeData?>(null)
    private var isMovableState by mutableStateOf(true)
    private var attachPositionState by mutableStateOf("top_center")
    private var showCpuUsageState by mutableStateOf(true)
    private var showCpuFreqState by mutableStateOf(true)
    private var showGpuUsageState by mutableStateOf(true)
    private var showRamUsageState by mutableStateOf(true)
    private var showSwapUsageState by mutableStateOf(true)
    private var showPowerUsageState by mutableStateOf(true)
    private var showCpuTempState by mutableStateOf(false)
    private var showBatteryTempState by mutableStateOf(false)
    private var showProgressBarsState by mutableStateOf(true)
    private var showBatteryPercentState by mutableStateOf(false)
    private var updateIntervalState by mutableStateOf(2000L)
    private var showIconsOnlyState by mutableStateOf(false)
    private var isGridViewState by mutableStateOf(false)
    private var gridColumnsState by mutableStateOf(2)
    private var isHorizontalState by mutableStateOf(false)
    private var componentOrderState by mutableStateOf(listOf<String>())
    private var overlayOpacityState by mutableStateOf(0.9f)
    private var accentColorIndexState by mutableStateOf(0)
    private var showSparklinesState by mutableStateOf(false)
    private var showAbsoluteValuesState by mutableStateOf(false)
    private var cpuFreqDisplayState by mutableStateOf("all_cores")
    private var accentTintBackgroundState by mutableStateOf(false)
    private var accentBgColorIndexState by mutableStateOf(-1)
    private var absCpuState by mutableStateOf(true)
    private var absGpuState by mutableStateOf(true)
    private var absRamState by mutableStateOf(true)
    private var absSwapState by mutableStateOf(true)
    private var showFpsState by mutableStateOf(false)
    private val _fpsState = MutableStateFlow(FpsMonitor.FpsResult(0f, 0))
    private val fpsState = _fpsState.asStateFlow()

    // History for Sparklines: Map of metric key to list of values
    private val metricHistory = mutableStateMapOf<String, List<Float>>()
    private val MAX_HISTORY = 15

    private val CHANNEL_ID = "overlay_service"
    private val NOTIFICATION_ID = 1001

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val owner = object : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val savedStateController = SavedStateRegistryController.create(this)
            
            override val lifecycle: Lifecycle = lifecycleRegistry
            override val viewModelStore: ViewModelStore = ViewModelStore()
            override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry
            
            init {
                savedStateController.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
        }
        
        this.lifecycleOwner = owner
        this.savedStateRegistryOwner = owner
        this.viewModelStore = owner.viewModelStore

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        isRunning = true
        showOverlay()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_SETTINGS") {
            updateSettings()
        }
        return START_STICKY
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        showCpuUsageState = prefs.getBoolean("show_cpu_usage", true)
        showCpuFreqState = prefs.getBoolean("show_cpu_freq", true)
        showGpuUsageState = prefs.getBoolean("show_gpu_usage", true)
        showRamUsageState = prefs.getBoolean("show_ram_usage", true)
        showSwapUsageState = prefs.getBoolean("show_swap_usage", true)
        showPowerUsageState = prefs.getBoolean("show_power", true)
        showCpuTempState = prefs.getBoolean("show_cpu_temp", false)
        showBatteryTempState = prefs.getBoolean("show_battery_temp", false)
        showProgressBarsState = prefs.getBoolean("show_progress_bars", true)
        showBatteryPercentState = prefs.getBoolean("show_battery_percent", false)
        // Use PowerCalibrationManager for consistent update interval across app
        val calibrationManager = PowerCalibrationManager(this)
        updateIntervalState = calibrationManager.getUpdateInterval()
        showIconsOnlyState = prefs.getBoolean("show_icons_only", false)
        isGridViewState = prefs.getBoolean("is_grid_view", false)
        gridColumnsState = prefs.getInt("grid_columns", 2)
        isHorizontalState = prefs.getBoolean("is_horizontal", false)
        val defaultOrder = "cpu_usage,cpu_freq,gpu_usage,ram_usage,swap_usage,power_usage,cpu_temp,battery_temp,battery_percent,fps"
        val savedOrder = (prefs.getString("component_order", defaultOrder) ?: defaultOrder).split(",")
        // Ensure any newly added default items (e.g. "fps") are included
        val defaultItems = defaultOrder.split(",")
        componentOrderState = (savedOrder + defaultItems.filter { it !in savedOrder }).distinct()
        Log.d("OverlayService", "Loaded component order: ${componentOrderState.joinToString(",")}")
        isMovableState = prefs.getBoolean("movable", true)
        attachPositionState = prefs.getString("attach_position", "top_center") ?: "top_center"
        overlayOpacityState = prefs.getFloat("overlay_opacity", 0.9f)
        accentColorIndexState = prefs.getInt("accent_color_index", 0)
        showSparklinesState = prefs.getBoolean("show_sparklines", false)
        showAbsoluteValuesState = prefs.getBoolean("show_absolute_values", false)
        accentTintBackgroundState = prefs.getBoolean("accent_tint_background", false)
        accentBgColorIndexState = prefs.getInt("accent_bg_color_index", -1)
        cpuFreqDisplayState = prefs.getString("cpu_freq_display", "all_cores") ?: "all_cores"
        absCpuState = prefs.getBoolean("abs_cpu", true)
        absGpuState = prefs.getBoolean("abs_gpu", true)
        absRamState = prefs.getBoolean("abs_ram", true)
        absSwapState = prefs.getBoolean("abs_swap", true)
        showFpsState = prefs.getBoolean("show_fps", false)
    }

    private fun updateSettings() {
        if (!::composeView.isInitialized || !composeView.isAttachedToWindow) return
        
        loadSettings()
        
        val flags = if (isMovableState) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // Avoid status bar overlap by removing FLAG_LAYOUT_IN_SCREEN
        }

        val params = (composeView.layoutParams as WindowManager.LayoutParams).apply {
            this.flags = flags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                preferredRefreshRate = if (showFpsState) getPreferredRefreshRate() else 0f
            }
            if (isMovableState) {
                // If switching back to movable from a fixed position, reset to a default point
                if (x == 0 && (gravity == (Gravity.TOP or Gravity.CENTER_HORIZONTAL) || 
                              gravity == (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) ||
                              gravity == (Gravity.START or Gravity.CENTER_VERTICAL) ||
                              gravity == (Gravity.END or Gravity.CENTER_VERTICAL))) {
                    gravity = Gravity.TOP or Gravity.START
                    x = 100
                    y = 100
                }
            } else {
                gravity = when (attachPositionState) {
                    "top_left" -> Gravity.TOP or Gravity.START
                    "top_right" -> Gravity.TOP or Gravity.END
                    "bottom_left" -> Gravity.BOTTOM or Gravity.START
                    "bottom_right" -> Gravity.BOTTOM or Gravity.END
                    "bottom_center" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    "left_center" -> Gravity.START or Gravity.CENTER_VERTICAL
                    "right_center" -> Gravity.END or Gravity.CENTER_VERTICAL
                    else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                x = 0
                y = 0
            }
        }
        
        windowManager.updateViewLayout(composeView, params)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("MKM Overlay Active")
            .setContentText("Status monitor is running")
            .setSmallIcon(R.drawable.ic_stat_battery)
            .build()
    }

    private fun startMonitoring() {
        val calibrationManager = PowerCalibrationManager(this)
        serviceScope.launch {
            while (isActive) {
                val data = repository.getHomeData(calibrationManager.getMultiplier())
                uiState.value = data
                
                data?.let { h ->
                    updateHistory("cpu_usage", h.cpu.overallUsage)
                    updateHistory("cpu_freq", h.cpu.overallUsage) 
                    updateHistory("gpu_usage", h.gpu.loadPercent)
                    updateHistory("ram_usage", h.memory.usagePercent)
                    updateHistory("swap_usage", h.swap.usagePercent)
                    updateHistory("power_usage", (h.power.calibratedPowerW / 5f).coerceIn(0f, 1f))
                    updateHistory("cpu_temp", (h.cpuTemp / 100f).coerceIn(0f, 1f))
                    updateHistory("battery_temp", (h.batteryTemp / 100f).coerceIn(0f, 1f))
                    updateHistory("battery_percent", h.power.batteryPercent / 100f)
                }
                
                // Read FPS (gfxinfo for per-app, Choreographer fallback)
                if (showFpsState) {
                    FpsMonitor.initChoreographer()
                    val fpsResult = withContext(Dispatchers.IO) { FpsMonitor.readFps() }
                    _fpsState.value = fpsResult
                    val maxRate = getActiveRefreshRate().coerceAtLeast(60f)
                    updateHistory("fps", (fpsResult.fps / maxRate).coerceIn(0f, 1f))
                } else {
                    FpsMonitor.stopChoreographer()
                }
                
                val interval = if (showFpsState) {
                    updateIntervalState.coerceAtLeast(500L)
                } else {
                    updateIntervalState.coerceAtLeast(1000L)
                }
                delay(interval)
            }
        }
    }

    private fun updateHistory(key: String, value: Float) {
        val current = metricHistory[key] ?: emptyList()
        val next = (current + value).takeLast(MAX_HISTORY)
        metricHistory[key] = next
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun showOverlay() {
        if (::composeView.isInitialized && composeView.isAttachedToWindow) return
        
        loadSettings()
        
        val flags = if (isMovableState) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // Avoid status bar overlap by removing FLAG_LAYOUT_IN_SCREEN
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                preferredRefreshRate = if (showFpsState) getPreferredRefreshRate() else 0f
            }
            if (isMovableState) {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            } else {
                gravity = when (attachPositionState) {
                    "top_left" -> Gravity.TOP or Gravity.START
                    "top_right" -> Gravity.TOP or Gravity.END
                    "bottom_left" -> Gravity.BOTTOM or Gravity.START
                    "bottom_right" -> Gravity.BOTTOM or Gravity.END
                    "bottom_center" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    "left_center" -> Gravity.START or Gravity.CENTER_VERTICAL
                    "right_center" -> Gravity.END or Gravity.CENTER_VERTICAL
                    else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                x = 0
                y = 0
            }
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService.lifecycleOwner)
            setViewTreeViewModelStoreOwner(this@OverlayService.lifecycleOwner as ViewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(this@OverlayService.savedStateRegistryOwner)
            
            setContent {
                val accentColors = listOf(
                    Color(0xFFD0BCFF),
                    Color(0xFF4CAF50),
                    Color(0xFF2196F3),
                    Color(0xFFF44336),
                    Color(0xFFFFEB3B),
                    Color(0xFFE91E63),
                    Color(0xFF9C27B0),
                    Color(0xFF00BCD4)
                )
                val selectedColor = accentColors.getOrElse(accentColorIndexState) { accentColors[0] }
                val baseSurface = Color(0xFF49454F)
                val backgroundColor = if (accentTintBackgroundState) {
                    val bgIndex = if (accentBgColorIndexState == -1) accentColorIndexState else accentBgColorIndexState
                    val bgColor = accentColors.getOrElse(bgIndex) { selectedColor }
                    bgColor.copy(alpha = 0.25f)
                } else {
                    baseSurface
                }

                MaterialTheme(colorScheme = darkColorScheme(primary = selectedColor, surfaceVariant = backgroundColor)) {
                    val data by uiState.collectAsState()

                    Card(
                        modifier = Modifier
                            .let {
                                if (isMovableState) {
                                    it.pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            params.x += dragAmount.x.toInt()
                                            params.y += dragAmount.y.toInt()
                                            windowManager.updateViewLayout(this@apply, params)
                                        }
                                    }
                                } else it
                            }
                            .padding(2.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = backgroundColor.copy(alpha = overlayOpacityState)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        data?.let { homeData ->
                            val metricsMap = mutableMapOf<String, @Composable () -> Unit>()

                            if (showCpuUsageState) metricsMap["cpu_usage"] = {
                                if (showAbsoluteValuesState && absCpuState) {
                                    val freqStr = formatFreqCompact(parseFreqMHz(homeData.cpu.clusters.firstOrNull()?.currentFreq ?: "0 MHz"))
                                    CompactMetric("CPU", freqStr, 0f, false, Icons.Default.DeveloperBoard, showIconsOnlyState, null)
                                } else {
                                    CompactMetric("CPU", "${(homeData.cpu.overallUsage * 100).toInt()}%", homeData.cpu.overallUsage, showProgressBarsState, Icons.Default.DeveloperBoard, showIconsOnlyState, metricHistory["cpu_usage"])
                                }
                            }
                            if (showCpuFreqState) metricsMap["cpu_freq"] = {
                                val freqStr = when (cpuFreqDisplayState) {
                                    "avg" -> {
                                        val freqs = homeData.cpu.clusters.mapNotNull { cluster ->
                                            val mhz = parseFreqMHz(cluster.currentFreq)
                                            if (mhz > 0L) mhz else null
                                        }
                                        if (freqs.isNotEmpty()) formatFreqCompact(freqs.average().toLong()) else "0"
                                    }
                                    "max" -> {
                                        val maxMhz = homeData.cpu.clusters.mapNotNull { cluster ->
                                            val mhz = parseFreqMHz(cluster.currentFreq)
                                            if (mhz > 0L) mhz else null
                                        }.maxOrNull() ?: 0L
                                        formatFreqCompact(maxMhz)
                                    }
                                    else -> {
                                        homeData.cpu.clusters.sortedBy { it.id }.joinToString(" ") { cluster ->
                                            formatFreqCompact(parseFreqMHz(cluster.currentFreq))
                                        }
                                    }
                                }
                                CompactMetric("FREQ", freqStr, 0f, false, Icons.Default.Speed, showIconsOnlyState, metricHistory["cpu_freq"])
                            }
                            if (showGpuUsageState) metricsMap["gpu_usage"] = {
                                if (showAbsoluteValuesState && absGpuState) {
                                    val freqStr = if (homeData.gpu.frequencyAvailable) {
                                        formatFreqCompact(parseFreqMHz(homeData.gpu.currentFreq))
                                    } else "N/A"
                                    CompactMetric("GPU", freqStr, 0f, false, Icons.Default.VideogameAsset, showIconsOnlyState, null)
                                } else {
                                    CompactMetric("GPU", "${(homeData.gpu.loadPercent * 100).toInt()}%", homeData.gpu.loadPercent, showProgressBarsState, Icons.Default.VideogameAsset, showIconsOnlyState, metricHistory["gpu_usage"])
                                }
                            }
                            if (showRamUsageState) metricsMap["ram_usage"] = {
                                if (showAbsoluteValuesState && absRamState) {
                                    CompactMetric("RAM", homeData.memory.usedUi, 0f, false, Icons.Default.Memory, showIconsOnlyState, null)
                                } else {
                                    CompactMetric("RAM", "${(homeData.memory.usagePercent * 100).toInt()}%", homeData.memory.usagePercent, showProgressBarsState, Icons.Default.Memory, showIconsOnlyState, metricHistory["ram_usage"])
                                }
                            }
                            if (showSwapUsageState && homeData.swap.isActive) metricsMap["swap_usage"] = {
                                if (showAbsoluteValuesState && absSwapState) {
                                    CompactMetric("SWAP", homeData.swap.usedUi, 0f, false, Icons.Default.SwapCalls, showIconsOnlyState, null)
                                } else {
                                    CompactMetric("SWAP", "${(homeData.swap.usagePercent * 100).toInt()}%", homeData.swap.usagePercent, showProgressBarsState, Icons.Default.SwapCalls, showIconsOnlyState, metricHistory["swap_usage"])
                                }
                            }
                            if (showPowerUsageState) metricsMap["power_usage"] = {
                                val sign = if (homeData.power.isCharging) "+" else "-"
                                val powerStr = String.format("${sign}%.2f W", homeData.power.calibratedPowerW)
                                CompactMetric("PWR", powerStr, 0f, false, Icons.Default.FlashOn, showIconsOnlyState, metricHistory["power_usage"])
                            }
                            if (showCpuTempState) metricsMap["cpu_temp"] = {
                                CompactMetric("CTMP", String.format("%.1f°C", homeData.cpuTemp), homeData.cpuTemp / 100f, showProgressBarsState, Icons.Default.Thermostat, showIconsOnlyState, metricHistory["cpu_temp"])
                            }
                            if (showBatteryTempState) metricsMap["battery_temp"] = {
                                CompactMetric("BTMP", String.format("%.1f°C", homeData.batteryTemp), homeData.batteryTemp / 100f, showProgressBarsState, Icons.Default.BatteryChargingFull, showIconsOnlyState, metricHistory["battery_temp"])
                            }
                            if (showBatteryPercentState) metricsMap["battery_percent"] = {
                                CompactMetric("BAT", "${homeData.power.batteryPercent}%", homeData.power.batteryPercent / 100f, showProgressBarsState, Icons.Default.BatteryStd, showIconsOnlyState, metricHistory["battery_percent"])
                            }
                            metricsMap["fps"] = {
                                if (showFpsState) {
                                    val currentFps by fpsState.collectAsState()
                                    val maxRate = getActiveRefreshRate().coerceAtLeast(60f)
                                    val fpsDisplay = if (currentFps.fps > 0f) String.format("%.0f", currentFps.fps) else "0"
                                    val progress = (currentFps.fps / maxRate).coerceIn(0f, 1f)
                                    CompactMetric("FPS", "${fpsDisplay} FPS", progress, showProgressBarsState, Icons.Default.SlowMotionVideo, showIconsOnlyState, metricHistory["fps"])
                                }
                            }

                            val metrics = componentOrderState.mapNotNull { metricsMap[it] }

                            if (isHorizontalState) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    metrics.forEach { it() }
                                }
                            } else if (isGridViewState) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (i in 0 until gridColumnsState) {
                                        val colMetrics = metrics.filterIndexed { index, _ -> index % gridColumnsState == i }
                                        if (colMetrics.isNotEmpty()) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                colMetrics.forEach { it() }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    metrics.forEach { it() }
                                }
                            }
                        } ?: Text("Loading...", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        windowManager.addView(composeView, params)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun CompactMetric(
        label: String,
        value: String,
        progress: Float,
        showProgress: Boolean = true,
        icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        showIconsOnly: Boolean = false,
        history: List<Float>? = null
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "progress"
        )

        val metricColor = when {
            animatedProgress > 0.9f -> MaterialTheme.colorScheme.error
            animatedProgress > 0.7f -> Color(0xFFFFA000) // Warning Amber
            else -> MaterialTheme.colorScheme.primary
        }

        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(if (showProgress) 4.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIconsOnly && icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = metricColor,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "${label}:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = metricColor,
                    modifier = Modifier.widthIn(min = 32.dp)
                )
            }
            
            if (showProgress) {
                if (showSparklinesState && history != null) {
                    Sparkline(
                        history = history,
                        color = metricColor,
                        modifier = Modifier.width(68.dp).height(16.dp).padding(horizontal = 4.dp)
                    )
                } else {
                    Box(modifier = Modifier.width(68.dp), contentAlignment = Alignment.Center) {
                        LinearWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = metricColor,
                            trackColor = metricColor.copy(alpha = 0.15f)
                        )
                    }
                }
                
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(42.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun Sparkline(history: List<Float>, color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier = modifier) {
            if (history.isEmpty()) return@Canvas
            val width = size.width
            val height = size.height
            val step = width / (MAX_HISTORY - 1).coerceAtLeast(1)
            
            // Draw border
            drawRect(
                color = color.copy(alpha = 0.3f),
                topLeft = Offset(0f, 0f),
                size = Size(width, height),
                style = Stroke(width = 1.dp.toPx())
            )
            
            val path = Path().apply {
                history.forEachIndexed { index, value ->
                    val x = index * step
                    val y = height - (value.coerceIn(0f, 1f) * height)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        FpsMonitor.stopChoreographer()
        if (::lifecycleOwner.isInitialized) {
            (lifecycleOwner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.DESTROYED
        }
        if (::savedStateRegistryOwner.isInitialized) {
            (savedStateRegistryOwner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.DESTROYED
        }
        serviceScope.cancel()
        if (::viewModelStore.isInitialized) {
            viewModelStore.clear()
        }
        if (::composeView.isInitialized && composeView.isAttachedToWindow) {
            windowManager.removeView(composeView)
        }
        isRunning = false
        super.onDestroy()
    }

    private fun getPreferredRefreshRate(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val defaultDisplay = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                val maxFps = defaultDisplay?.supportedModes?.map { it.refreshRate }?.maxOrNull()
                if (maxFps != null && maxFps > 60f) {
                    return maxFps
                }
            } catch (e: Exception) {
                Log.w("OverlayService", "Failed to query supported refresh rates", e)
            }
        }
        return 0f
    }

    private fun getActiveRefreshRate(): Float {
        try {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val defaultDisplay = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val activeRate = defaultDisplay?.mode?.refreshRate
            if (activeRate != null && activeRate > 0f) {
                return activeRate
            }
        } catch (e: Exception) {
            Log.w("OverlayService", "Failed to query active refresh rate", e)
        }
        return 60f
    }
}

private fun parseFreqMHz(s: String): Long {
    val parts = s.trim().split(" ", limit = 2)
    val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return 0L
    val unit = parts.getOrNull(1)?.lowercase() ?: ""
    return when {
        unit.startsWith("ghz") -> (num * 1000.0).toLong()
        unit.startsWith("mhz") -> num.toLong()
        unit.startsWith("khz") -> (num / 1000.0).toLong()
        else -> num.toLong()
    }
}

private fun formatFreqCompact(mhz: Long): String {
    return when {
        mhz <= 0L -> "0"
        mhz >= 1000L -> String.format("%.2fG", mhz / 1000.0)
        else -> "${mhz}M"
    }
}
