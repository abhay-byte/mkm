# Shizuku Integration Implementation Plan for MKM v1.1

## Executive Summary

This document outlines the complete implementation plan for adding Shizuku support to Minimal Kernel Manager (MKM) version 1.1. The goal is to enable non-root users to access privileged system features via Shizuku's ADB shell permissions.

**Target Release:** MKM v1.1  
**Estimated Effort:** 12-16 hours  
**Priority:** High

---

## 1. Overview

### What We're Adding
- Shizuku API support for executing privileged shell commands
- Automatic Sui support (comes free with Shizuku API)
- Intelligent fallback: Shizuku → Root → Error
- Permission management UI

### Benefits
- Non-root users can use MKM via ADB wireless debugging (Android 11+)
- Larger potential user base (~100k-500k Shizuku users)
- Better user experience for non-root scenarios

---

## 2. Technical Requirements

### Dependencies
```gradle
// Add to app/build.gradle.kts
dependencies {
    // Shizuku API & Provider
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    
    // Existing
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}
```

### Maven Repository
Already configured - Shizuku is on Maven Central.

### Manifest Changes
```xml
<!-- Add to AndroidManifest.xml -->
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />

<application>
    <!-- ... existing ... -->
    
    <provider
        android:name="rikka.shizuku.ShizukuProvider"
        android:authorities="${applicationId}.shizuku"
        android:enabled="true"
        android:exported="true"
        android:multiprocess="false"
        android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
</application>
```

---

## 3. Architecture Changes

### Current Architecture (v1.0)
```
ShellManager.exec()
    └─> Root (libsu) → Success/Fail
```

### New Architecture (v1.1)
```
ShellManager.exec()
    ├─> Shizuku (if available & permitted) → Success
    ├─> Root (libsu fallback)              → Success
    └─> Error (no access)
```

### Component Diagram
```
┌─────────────────────────────────────────┐
│          MkmApplication                 │
│  - onCreate(): Initialize Shizuku       │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│        ShizukuManager (NEW)             │
│  - init()                               │
│  - isAvailable(): Boolean               │
│  - hasPermission(): Boolean             │
│  - requestPermission()                  │
│  - addListeners()                       │
│  - removeListeners()                    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         ShellManager                    │
│  - exec(command): Result                │
│  - getAvailableMethod(): Method         │
│                                         │
│  Priority:                              │
│  1. execShizuku() - if available       │
│  2. execRoot()    - if available       │
│  3. execLocal()   - fallback           │
└─────────────────────────────────────────┘
```

---

## 4. Implementation Steps

### Phase 1: Foundation (2-3 hours)

#### Step 1.1: Create ShizukuManager
**File:** `app/src/main/java/com/ivarna/mkm/shell/ShizukuManager.kt`

```kotlin
package com.ivarna.mkm.shell

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

object ShizukuManager {
    private val binderReceivedListeners = mutableListOf<Shizuku.OnBinderReceivedListener>()
    private val binderDeadListeners = mutableListOf<Shizuku.OnBinderDeadListener>()
    
    @Volatile
    private var initialized = false
    
    /**
     * Initialize Shizuku. Call this in Application.onCreate()
     * @return true if Shizuku binder is available
     */
    fun init(context: Context): Boolean {
        if (initialized) return isAvailable()
        
        // Enable multi-process support if needed
        ShizukuProvider.enableMultiProcessSupport(false)
        
        // Disable automatic Sui initialization if you want to handle it separately
        // ShizukuProvider.disableAutomaticSuiInitialization()
        
        initialized = true
        return isAvailable()
    }
    
    /**
     * Check if Shizuku is available (app installed and running)
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if we have permission to use Shizuku
     */
    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false // Don't support old versions
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Request permission from user
     * Result will be delivered to OnRequestPermissionResultListener
     */
    fun requestPermission() {
        if (!isAvailable()) return
        Shizuku.requestPermission(REQUEST_CODE)
    }
    
    /**
     * Add listener for binder received events
     */
    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        binderReceivedListeners.add(listener)
        Shizuku.addBinderReceivedListener(listener)
    }
    
    /**
     * Remove binder received listener
     */
    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        binderReceivedListeners.remove(listener)
        Shizuku.removeBinderReceivedListener(listener)
    }
    
    /**
     * Add listener for binder dead events
     */
    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        binderDeadListeners.add(listener)
        Shizuku.addBinderDeadListener(listener)
    }
    
    /**
     * Remove binder dead listener
     */
    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        binderDeadListeners.remove(listener)
        Shizuku.removeBinderDeadListener(listener)
    }
    
    /**
     * Get Shizuku version
     */
    fun getVersion(): Int {
        return try {
            Shizuku.getVersion()
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Get Shizuku UID (0 for root, 2000 for adb)
     */
    fun getUid(): Int {
        return try {
            Shizuku.getUid()
        } catch (e: Exception) {
            -1
        }
    }
    
    companion object {
        const val REQUEST_CODE = 1001
    }
}
```

#### Step 1.2: Update MkmApplication
**File:** `app/src/main/java/com/ivarna/mkm/MkmApplication.kt`

```kotlin
class MkmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Shell (existing)
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
        
        // Initialize Shizuku (NEW)
        ShizukuManager.init(this)
    }
}
```

### Phase 2: Shell Execution (3-4 hours)

#### Step 2.1: Update ShellManager
**File:** `app/src/main/java/com/ivarna/mkm/shell/ShellManager.kt`

Add Shizuku execution method:

```kotlin
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShellManager {
    
    enum class AccessMethod {
        SHIZUKU,
        ROOT,
        LOCAL,
        NONE
    }
    
    /**
     * Get the currently available access method
     */
    fun getAvailableMethod(): AccessMethod {
        return when {
            ShizukuManager.isAvailable() && ShizukuManager.hasPermission() -> AccessMethod.SHIZUKU
            Shell.getShell().isRoot -> AccessMethod.ROOT
            else -> AccessMethod.LOCAL
        }
    }
    
    /**
     * Execute command with automatic fallback
     */
    fun exec(command: String): CommandResult {
        return when (getAvailableMethod()) {
            AccessMethod.SHIZUKU -> execShizuku(command)
            AccessMethod.ROOT -> execRoot(command)
            AccessMethod.LOCAL -> execLocal(command)
            AccessMethod.NONE -> CommandResult(-1, "", "No elevated access available")
        }
    }
    
    /**
     * Execute via Shizuku
     */
    private fun execShizuku(command: String): CommandResult {
        return try {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", command),
                null,
                null
            )
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            
            // Read stdout
            outReader.useLines { lines ->
                lines.forEach { line ->
                    output.append(line).append("\n")
                }
            }
            
            // Read stderr
            errReader.useLines { lines ->
                lines.forEach { line ->
                    error.append(line).append("\n")
                }
            }
            
            process.waitFor()
            CommandResult(
                process.exitValue(),
                output.toString().trim(),
                error.toString().trim()
            )
        } catch (e: Exception) {
            CommandResult(-1, "", "Shizuku error: ${e.message}")
        }
    }
    
    // Existing execRoot() and execLocal() remain unchanged
}
```

### Phase 3: User Interface (4-5 hours)

#### Step 3.1: Update HomeScreen to show access method
**File:** `app/src/main/java/com/ivarna/mkm/ui/screens/HomeScreen.kt`

```kotlin
@Composable
fun HomeScreen(/* ... */) {
    val accessMethod by remember {
        derivedStateOf { ShellManager.getAvailableMethod() }
    }
    
    // ... existing code ...
    
    // Update status badges
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusBadge(
            label = "Shizuku",
            isActive = accessMethod == ShellManager.AccessMethod.SHIZUKU,
            modifier = Modifier.weight(1f)
        )
        StatusBadge(
            label = "Root",
            isActive = accessMethod == ShellManager.AccessMethod.ROOT,
            modifier = Modifier.weight(1f)
        )
    }
    
    // Add setup button if no access
    if (accessMethod == ShellManager.AccessMethod.LOCAL) {
        SetupAccessCard(
            onShizukuSetup = { /* Navigate to Shizuku setup */ },
            onRootInfo = { /* Show root info */ }
        )
    }
}
```

#### Step 3.2: Create Permission Request Screen
**File:** `app/src/main/java/com/ivarna/mkm/ui/screens/PermissionScreen.kt`

```kotlin
@Composable
fun PermissionRequestScreen(
    onNavigateBack: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionStatus = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Checking) }
    
    val permissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuManager.REQUEST_CODE) {
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
        
        // Check current status
        when {
            !ShizukuManager.isAvailable() -> {
                permissionStatus.value = PermissionStatus.NotInstalled
            }
            ShizukuManager.hasPermission() -> {
                permissionStatus.value = PermissionStatus.Granted
                delay(500)
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
                        Icon(Icons.Default.ArrowBack, "Back")
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
                    Text(
                        "Please install Shizuku to use this feature",
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
                    Text(
                        "MKM needs Shizuku permission to manage system settings",
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
                    Text(
                        "MKM cannot function without Shizuku permission",
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

enum class PermissionStatus {
    Checking,
    NotInstalled,
    NotGranted,
    Granted,
    Denied
}
```

### Phase 4: Settings Integration (2-3 hours)

#### Step 4.1: Add Access Method Settings
**File:** `app/src/main/java/com/ivarna/mkm/ui/screens/SettingsScreen.kt`

Add section showing current access method and allowing permission management:

```kotlin
@Composable
fun AccessMethodCard(
    accessMethod: ShellManager.AccessMethod,
    onRequestShizukuPermission: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Access Method",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
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
                        ShellManager.AccessMethod.SHIZUKU -> "Shizuku"
                        ShellManager.AccessMethod.ROOT -> "Root"
                        ShellManager.AccessMethod.LOCAL -> "None"
                        ShellManager.AccessMethod.NONE -> "None"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (accessMethod) {
                        ShellManager.AccessMethod.SHIZUKU,
                        ShellManager.AccessMethod.ROOT -> MaterialTheme.colorScheme.primary
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
                    !ShizukuManager.isAvailable() -> "Not Installed"
                    !ShizukuManager.hasPermission() -> "Not Permitted"
                    else -> "Active"
                },
                statusColor = when {
                    !ShizukuManager.isAvailable() -> MaterialTheme.colorScheme.error
                    !ShizukuManager.hasPermission() -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                onClick = if (!ShizukuManager.hasPermission() && ShizukuManager.isAvailable()) {
                    onRequestShizukuPermission
                } else null
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Root status
            AccessMethodItem(
                icon = Icons.Default.AdminPanelSettings,
                title = "Root",
                status = if (Shell.getShell().isRoot) "Active" else "Not Available",
                statusColor = if (Shell.getShell().isRoot) {
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
    icon: ImageVector,
    title: String,
    status: String,
    statusColor: Color,
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
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
```

### Phase 5: Testing & Polish (2-3 hours)

#### Step 5.1: Test Cases

Create test scenarios:

1. **Shizuku Priority Test**
   - With Shizuku + Root: Should use Shizuku
   - Verify CPU/GPU commands work

2. **Fallback Test**
   - Revoke Shizuku permission: Should fall back to Root
   - Kill Shizuku: Should fall back to Root

3. **Permission Flow Test**
   - First launch: Request permission
   - Permission denied: Show error, allow retry
   - Permission granted: Enable features

4. **Sui Test**
   - Install Sui module (Magisk)
   - Verify automatic detection
   - Verify commands execute

#### Step 5.2: Error Handling

Add comprehensive error messages:

```kotlin
// In ShellManager
fun exec(command: String): CommandResult {
    val method = getAvailableMethod()
    
    return when (method) {
        AccessMethod.SHIZUKU -> {
            val result = execShizuku(command)
            if (!result.isSuccess && result.stderr.contains("permission denied", ignoreCase = true)) {
                // Fallback to root if Shizuku permission was revoked
                execRoot(command)
            } else {
                result
            }
        }
        AccessMethod.ROOT -> execRoot(command)
        AccessMethod.LOCAL -> CommandResult(
            -1, 
            "", 
            "No elevated access available. Please install Shizuku or root your device."
        )
        AccessMethod.NONE -> CommandResult(-1, "", "No access method available")
    }
}
```

---

## 5. Testing Plan

### Manual Testing Checklist

- [ ] Fresh install with Shizuku not installed
  - [ ] App starts without crash
  - [ ] Shows "Shizuku not available" message
  - [ ] Can still use root if available

- [ ] Install Shizuku, don't grant permission
  - [ ] App detects Shizuku
  - [ ] Shows permission request button
  - [ ] Request permission flow works
  - [ ] Denial handled gracefully

- [ ] Grant Shizuku permission
  - [ ] App detects permission
  - [ ] Commands execute via Shizuku
  - [ ] CPU/GPU changes apply correctly
  - [ ] Swap creation works

- [ ] With both Shizuku and Root
  - [ ] Prioritizes Shizuku
  - [ ] Can manually test root fallback

- [ ] Revoke Shizuku permission at runtime
  - [ ] Falls back to root gracefully
  - [ ] No crashes

- [ ] Kill Shizuku process
  - [ ] Falls back to root
  - [ ] Recovers when Shizuku restarts

### Automated Testing

```kotlin
// Add to androidTest
@Test
fun testShizukuExecution() {
    // Assuming Shizuku is available and permitted
    val result = ShellManager.exec("id")
    assertTrue(result.isSuccess)
    assertTrue(result.stdout.contains("uid="))
}

@Test
fun testAccessMethodPriority() {
    // Mock Shizuku and Root both available
    val method = ShellManager.getAvailableMethod()
    assertEquals(ShellManager.AccessMethod.SHIZUKU, method)
}

@Test
fun testFallbackToRoot() {
    // Mock Shizuku unavailable but Root available
    val result = ShellManager.exec("id")
    assertTrue(result.isSuccess)
}
```

---

## 6. Documentation Updates

### Files to Update

1. **README.md**
   ```markdown
   ## Requirements
   
   MKM requires elevated system access via one of:
   - **Root access** via Magisk, KernelSU, etc.
   - **Shizuku** (Android 11+ for wireless debugging, or root)
   - **Sui** (Magisk module, detected automatically)
   ```

2. **User Guide** (new file: `docs/user-guide.md`)
   - How to set up Shizuku
   - How to use wireless debugging on Android 11+
   - Troubleshooting

3. **F-Droid Metadata**
   ```
   Update description to mention Shizuku support
   ```

---

## 7. Release Plan

### Version 1.1 Changelog

```markdown
## v1.1 (March 2026)

### New Features
- ✨ Shizuku support - use MKM without root!
- ✨ Automatic Sui detection for rooted devices
- ✨ Intelligent fallback: Shizuku → Root
- ✨ Permission management UI
- ✨ Access method indicator in home screen

### Improvements
- 🔧 Better error messages for access failures
- 🔧 Improved status indicators
- 🔧 Settings screen shows current access method

### Technical
- Updated dependencies
- Better error handling
- Comprehensive test coverage
```

### Migration Notes

No breaking changes - v1.0 users will seamlessly upgrade with root access still working.

---

## 8. Timeline & Milestones

### Week 1 (Estimated: 8 hours)
- [ ] Day 1-2: Phase 1 - Foundation (ShizukuManager)
- [ ] Day 3-4: Phase 2 - Shell Execution
- [ ] Day 5: Testing Phase 1-2

### Week 2 (Estimated: 8 hours)
- [ ] Day 1-2: Phase 3 - UI Implementation
- [ ] Day 3: Phase 4 - Settings Integration
- [ ] Day 4-5: Phase 5 - Testing & Polish

### Week 3 (Buffer)
- [ ] Bug fixes
- [ ] Documentation
- [ ] Beta testing
- [ ] Final release

**Total Estimated Time:** 12-16 hours  
**Target Release Date:** Early March 2026

---

## 9. Risk Assessment

### High Risk
- **Shizuku API Changes**: Mitigated by using stable 13.1.5 version
- **Permission Flows**: Extensive testing required

### Medium Risk
- **User Setup Complexity**: Mitigated by clear UI/documentation
- **Testing Coverage**: Need real devices with different setups

### Low Risk
- **Backward Compatibility**: Root path unchanged
- **Dependencies**: Stable versions on Maven Central

---

## 10. Success Criteria

### Functional
- [ ] App works with Shizuku + no root
- [ ] App works with Root + no Shizuku
- [ ] App works with both (Shizuku priority)
- [ ] Clean fallback when methods fail
- [ ] No crashes in any scenario

### User Experience
- [ ] Clear status indicators
- [ ] Intuitive permission request flow
- [ ] Helpful error messages
- [ ] Smooth transitions

### Technical
- [ ] No new compiler warnings
- [ ] All tests pass
- [ ] F-Droid build succeeds
- [ ] APK size increase < 500KB

---

## 11. Future Enhancements (v1.2+)

Possible additions after v1.1 stabilizes:

- Dhizuku support (optional, for Device Owner users)
- Advanced: User preference for access method priority
- Advanced: Automatic Shizuku setup guide in-app
- Advanced: Shizuku status widget

---

## Appendix A: Code Structure

```
app/src/main/java/com/ivarna/mkm/
├── shell/
│   ├── ShellManager.kt          (MODIFIED - add Shizuku)
│   ├── ShizukuManager.kt        (NEW)
│   ├── ShellScripts.kt          (unchanged)
│   └── ShizukuJavaHelper.java   (DELETE - not needed)
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt        (MODIFIED - show access method)
│   │   ├── SettingsScreen.kt    (MODIFIED - add access settings)
│   │   └── PermissionScreen.kt  (NEW)
│   └── components/
│       └── AccessMethodCard.kt  (NEW)
└── MkmApplication.kt            (MODIFIED - init Shizuku)
```

---

## Appendix B: Dependencies

```gradle
dependencies {
    // Existing
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    
    // New for v1.1
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    
    // Compose BOM (existing)
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
}
```

---

## Contact & Questions

For questions or clarifications on this implementation plan:
- Create GitHub issue
- Discussion in F-Droid merge request

---

**Document Version:** 1.0  
**Created:** February 13, 2026  
**Last Updated:** February 13, 2026  
**Status:** Ready for Implementation
