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
import android.view.Gravity
import android.view.WindowManager
import android.content.pm.ServiceInfo
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var savedStateRegistryOwner: SavedStateRegistryOwner
    private val repository = SystemRepository()
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

    private val CHANNEL_ID = "overlay_service"
    private val NOTIFICATION_ID = 1001

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
        isMovableState = prefs.getBoolean("movable", true)
        attachPositionState = prefs.getString("attach_position", "top_center") ?: "top_center"
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
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }

    private fun startMonitoring() {
        val calibrationManager = PowerCalibrationManager(this)
        serviceScope.launch {
            while (isActive) {
                val data = repository.getHomeData(calibrationManager.getMultiplier())
                android.util.Log.d("MKM_Overlay", "Update: CPU ${data.cpu.overallUsage}, RAM ${data.memory.usagePercent}")
                uiState.value = data
                delay(2000)
            }
        }
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
                MaterialTheme(colorScheme = darkColorScheme()) {
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
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            data?.let { homeData ->
                                if (showCpuUsageState) {
                                    CompactMetric("CPU", "${(homeData.cpu.overallUsage * 100).toInt()}%", homeData.cpu.overallUsage)
                                }
                                if (showCpuFreqState) {
                                    val clusterFreqs = homeData.cpu.clusters.sortedBy { it.id }.joinToString(" ") { cluster ->
                                        cluster.currentFreq
                                            .replace(" GHz", "G")
                                            .replace(" MHz", "M")
                                    }
                                    CompactMetric("FREQ", clusterFreqs, 0f, false)
                                }
                                if (showGpuUsageState) {
                                    CompactMetric("GPU", "${(homeData.gpu.loadPercent * 100).toInt()}%", homeData.gpu.loadPercent)
                                }
                                if (showRamUsageState) {
                                    CompactMetric("RAM", "${(homeData.memory.usagePercent * 100).toInt()}%", homeData.memory.usagePercent)
                                }
                                if (showSwapUsageState && homeData.swap.isActive) {
                                    CompactMetric("SWAP", "${(homeData.swap.usagePercent * 100).toInt()}%", homeData.swap.usagePercent)
                                }
                                if (showPowerUsageState) {
                                    val powerStr = String.format("%.2f W", homeData.power.calibratedPowerW)
                                    CompactMetric("PWR", powerStr, 0f, false)
                                }
                            } ?: Text("Loading...", style = MaterialTheme.typography.labelSmall)
                        }
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
        showProgress: Boolean = true
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "progress"
        )

        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(36.dp)
            )
            
            if (showProgress) {
                Box(modifier = Modifier.width(68.dp), contentAlignment = Alignment.Center) {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = if (animatedProgress > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
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
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(114.dp), // 68 + 42 + 4 = 114
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
        super.onDestroy()
    }
}
