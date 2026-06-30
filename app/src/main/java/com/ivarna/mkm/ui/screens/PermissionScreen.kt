package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

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
    Hidden,          // Not visible in PackageManager but binder is alive
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
            ShizukuManager.hasPermission() -> {
                permissionStatus.value = PermissionStatus.Granted
                kotlinx.coroutines.delay(500)
                onPermissionGranted()
            }
            ShizukuManager.isHidden() -> {
                // Binder alive but package hidden (Issue #6)
                permissionStatus.value = PermissionStatus.Hidden
            }
            !ShizukuManager.isInstalled() -> {
                permissionStatus.value = PermissionStatus.NotInstalled
            }
            !ShizukuManager.isRunning() -> {
                permissionStatus.value = PermissionStatus.NotRunning
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
                title = { Text(stringResource(com.ivarna.mkm.R.string.shizuku_permission)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(com.ivarna.mkm.R.string.back))
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
                        Text(stringResource(com.ivarna.mkm.R.string.download_shizuku))
                    }
                }

                PermissionStatus.Hidden -> {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Shizuku is Hidden",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Shizuku appears to be hidden from the system but its service is running. You can still grant permission to MKM.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { ShizukuManager.requestPermission() }
                    ) {
                        Text(stringResource(com.ivarna.mkm.R.string.grant_permission))
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
                        Text(stringResource(com.ivarna.mkm.R.string.open_shizuku))
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
                        Text(stringResource(com.ivarna.mkm.R.string.grant_permission))
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
                            Text(stringResource(com.ivarna.mkm.R.string.cancel))
                        }
                        Button(onClick = { ShizukuManager.requestPermission() }) {
                            Text(stringResource(com.ivarna.mkm.R.string.try_again))
                        }
                    }
                }
                
                PermissionStatus.Checking -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(com.ivarna.mkm.R.string.checking_status))
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
                        com.ivarna.mkm.shell.ShellManager.AccessMethod.ROOT -> stringResource(com.ivarna.mkm.R.string.root)
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
            val shizukuHidden = ShizukuManager.isHidden()
            AccessMethodItem(
                icon = Icons.Default.Security,
                title = if (shizukuHidden) "Shizuku (Hidden)" else stringResource(com.ivarna.mkm.R.string.shizuku),
                status = when {
                    ShizukuManager.hasPermission() -> stringResource(com.ivarna.mkm.R.string.active)
                    shizukuHidden -> "Hidden — tap to grant"
                    !ShizukuManager.isInstalled() -> "Not Installed"
                    !ShizukuManager.isRunning() -> "Not Running"
                    !ShizukuManager.hasPermission() -> stringResource(com.ivarna.mkm.R.string.shizuku_hidden)
                    else -> stringResource(com.ivarna.mkm.R.string.active)
                },
                statusColor = when {
                    ShizukuManager.hasPermission() -> MaterialTheme.colorScheme.primary
                    shizukuHidden -> MaterialTheme.colorScheme.tertiary
                    !ShizukuManager.isInstalled() -> MaterialTheme.colorScheme.error
                    !ShizukuManager.isRunning() -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.tertiary
                },
                onClick = when {
                    ShizukuManager.hasPermission() -> null
                    shizukuHidden -> onRequestShizukuPermission
                    !ShizukuManager.isInstalled() -> null
                    !ShizukuManager.isRunning() -> null
                    else -> onRequestShizukuPermission
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Root status
            AccessMethodItem(
                icon = Icons.Default.Security,
                title = stringResource(com.ivarna.mkm.R.string.root),
                status = if (com.topjohnwu.superuser.Shell.getShell().isRoot) stringResource(com.ivarna.mkm.R.string.active) else "Not Available",
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