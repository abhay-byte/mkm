package com.ivarna.mkm.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivarna.mkm.shell.ShizukuManager
import com.ivarna.mkm.shell.SHIZUKU_REQUEST_CODE
import rikka.shizuku.Shizuku

/**
 * Permission status enum for UI state
 */
enum class PermissionStatus {
    Checking,
    NotInstalled,
    NotRunning,      // Installed but service not running
    NotGranted,
    Granted,
    Denied
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    onNavigateBack: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionStatus = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Checking) }
    
    val permissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                permissionStatus.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    PermissionStatus.Granted
                } else {
                    PermissionStatus.Denied
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        
        // Check current status with granular states
        when {
            !ShizukuManager.isInstalled() -> {
                permissionStatus.value = PermissionStatus.NotInstalled
            }
            !ShizukuManager.isRunning() -> {
                permissionStatus.value = PermissionStatus.NotRunning
            }
            ShizukuManager.hasPermission() -> {
                permissionStatus.value = PermissionStatus.Granted
                kotlinx.coroutines.delay(500)
                onPermissionGranted()
            }
            else -> {
                permissionStatus.value = PermissionStatus.NotGranted
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shizuku Permission") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (permissionStatus.value) {
                PermissionStatus.NotInstalled -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Shizuku Not Installed",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please install Shizuku to use this feature without root access.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Download Shizuku")
                    }
                }
                
                PermissionStatus.NotRunning -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Shizuku Not Running",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Shizuku is installed but the service is not running. Please open Shizuku and start the service.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            // Try to open Shizuku app
                            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                // Fallback to Play Store
                                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("market://details?id=moe.shizuku.privileged.api")
                                }
                                context.startActivity(playStoreIntent)
                            }
                        }
                    ) {
                        Text("Open Shizuku")
                    }
                }
                
                PermissionStatus.NotGranted -> {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Permission Required",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "MKM needs Shizuku permission to manage system settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { ShizukuManager.requestPermission() }
                    ) {
                        Text("Grant Permission")
                    }
                }
                
                PermissionStatus.Granted -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Permission Granted",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Setting up...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                
                PermissionStatus.Denied -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Permission Denied",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "MKM cannot function without Shizuku permission. You can still use root access if available.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("Cancel")
                        }
                        Button(onClick = { ShizukuManager.requestPermission() }) {
                            Text("Try Again")
                        }
                    }
                }
                
                PermissionStatus.Checking -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking status...")
                }
            }
        }
    }
}

/**
 * Card component for showing access method status in settings
 */
@Composable
fun AccessMethodCard(
    onRequestShizukuPermission: () -> Unit
) {
    val accessMethod = remember { com.ivarna.mkm.shell.ShellManager.getAvailableMethod() }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Access Method",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Current Method:",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    when (accessMethod) {
                        com.ivarna.mkm.shell.ShellManager.AccessMethod.ROOT -> "Root"
                        com.ivarna.mkm.shell.ShellManager.AccessMethod.LOCAL -> "None"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (accessMethod) {
                        com.ivarna.mkm.shell.ShellManager.AccessMethod.ROOT -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Shizuku status
            AccessMethodItem(
                icon = Icons.Default.Security,
                title = "Shizuku",
                status = when {
                    !ShizukuManager.isInstalled() -> "Not Installed"
                    !ShizukuManager.isRunning() -> "Not Running"
                    !ShizukuManager.hasPermission() -> "Not Permitted"
                    else -> "Active"
                },
                statusColor = when {
                    !ShizukuManager.isInstalled() -> MaterialTheme.colorScheme.error
                    !ShizukuManager.isRunning() -> MaterialTheme.colorScheme.tertiary
                    !ShizukuManager.hasPermission() -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                onClick = when {
                    !ShizukuManager.isInstalled() -> null  // Can't do anything from here
                    !ShizukuManager.isRunning() -> null    // User needs to open Shizuku app
                    !ShizukuManager.hasPermission() -> onRequestShizukuPermission
                    else -> null
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Root status
            AccessMethodItem(
                icon = Icons.Default.Security,
                title = "Root",
                status = if (com.topjohnwu.superuser.Shell.getShell().isRoot) "Active" else "Not Available",
                statusColor = if (com.topjohnwu.superuser.Shell.getShell().isRoot) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                onClick = null
            )
        }
    }
}

@Composable
fun AccessMethodItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    statusColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor
                )
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }
    }
}
