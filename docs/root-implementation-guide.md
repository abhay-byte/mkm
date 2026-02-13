# Root Implementation Quick Start for MKM

This document provides a quick reference for implementing root functionality in the MKM app.

## Add Dependencies

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Libsu for root access
    implementation("com.github.topjohnwu:libsu-core:5.0.5")
    implementation("com.github.topjohnwu:libsu-service:5.0.5")
    implementation("com.github.topjohnwu:libsu-nio:5.0.5")
}
```

## Initialize in Application Class

```kotlin
import com.topjohnwu.superuser.Shell

class MKMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Shell with configuration
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
```

Don't forget to add to `AndroidManifest.xml`:
```xml
<application
    android:name=".MKMApplication"
    ...>
```

## Check Root Access

```kotlin
import com.topjohnwu.superuser.Shell

fun checkRootAccess(): Boolean {
    return Shell.isAppGrantedRoot() ?: false
}

// Usage
if (checkRootAccess()) {
    // Root is available
} else {
    // No root, show message or use alternative
}
```

## Execute Root Commands

### Simple Command

```kotlin
fun getCpuFrequency(cpuId: Int): Long? {
    val result = Shell.cmd(
        "cat /sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_cur_freq"
    ).exec()
    
    return if (result.isSuccess) {
        result.out.firstOrNull()?.toLongOrNull()
    } else {
        null
    }
}
```

### Multiple Commands

```kotlin
fun setCpuGovernorAndFrequency(cpuId: Int, governor: String, maxFreq: Long) {
    Shell.cmd(
        "echo $governor > /sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_governor",
        "echo $maxFreq > /sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_max_freq"
    ).submit { result ->
        if (result.isSuccess) {
            Log.d("Root", "Settings applied successfully")
        } else {
            Log.e("Root", "Failed: ${result.err.joinToString("\n")}")
        }
    }
}
```

### Async Execution with Coroutines

```kotlin
suspend fun setCpuFrequencyAsync(cpuId: Int, frequency: Long): Result<Unit> {
    return withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "echo $frequency > /sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_max_freq"
        ).exec()
        
        if (result.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(result.err.joinToString("\n")))
        }
    }
}

// Usage
viewModelScope.launch {
    setCpuFrequencyAsync(0, 2000000).onSuccess {
        // Success
    }.onFailure { error ->
        // Handle error
    }
}
```

## Read System Files

### Using Shell Commands

```kotlin
fun readSystemFile(path: String): String? {
    val result = Shell.cmd("cat $path").exec()
    return if (result.isSuccess) {
        result.out.joinToString("\n")
    } else {
        null
    }
}
```

### Using Libsu NIO (Recommended)

```kotlin
import com.topjohnwu.superuser.nio.FileSystemManager

fun readSystemFileNIO(path: String): String? {
    return try {
        val fs = FileSystemManager.getRemote()
        val file = fs.getFile(path)
        if (file.exists()) {
            file.newInputStream().bufferedReader().use { it.readText() }
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("Root", "Failed to read $path", e)
        null
    }
}
```

## Write System Files

### Using Shell Commands

```kotlin
fun writeSystemFile(path: String, content: String): Boolean {
    val result = Shell.cmd("echo '$content' > $path").exec()
    return result.isSuccess
}
```

### Using Libsu NIO (Recommended)

```kotlin
fun writeSystemFileNIO(path: String, content: String): Boolean {
    return try {
        val fs = FileSystemManager.getRemote()
        val file = fs.getFile(path)
        file.newOutputStream().bufferedWriter().use { 
            it.write(content)
        }
        true
    } catch (e: Exception) {
        Log.e("Root", "Failed to write $path", e)
        false
    }
}
```

## Create Privilege Manager

```kotlin
class PrivilegeManager(private val context: Context) {
    
    enum class PrivilegeLevel {
        ROOT,
        SHIZUKU,
        NONE
    }
    
    val privilegeLevel: PrivilegeLevel by lazy {
        when {
            Shell.isAppGrantedRoot() == true -> PrivilegeLevel.ROOT
            isShizukuAvailable() -> PrivilegeLevel.SHIZUKU
            else -> PrivilegeLevel.NONE
        }
    }
    
    fun hasAnyPrivilege(): Boolean {
        return privilegeLevel != PrivilegeLevel.NONE
    }
    
    suspend fun executeCommand(command: String): Result<List<String>> {
        return when (privilegeLevel) {
            PrivilegeLevel.ROOT -> executeWithRoot(command)
            PrivilegeLevel.SHIZUKU -> executeWithShizuku(command)
            PrivilegeLevel.NONE -> Result.failure(
                SecurityException("No elevated privileges available")
            )
        }
    }
    
    private suspend fun executeWithRoot(command: String): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            val result = Shell.cmd(command).exec()
            if (result.isSuccess) {
                Result.success(result.out)
            } else {
                Result.failure(Exception(result.err.joinToString("\n")))
            }
        }
    }
    
    private fun isShizukuAvailable(): Boolean {
        // Implement Shizuku check
        return false // placeholder
    }
    
    private suspend fun executeWithShizuku(command: String): Result<List<String>> {
        // Implement Shizuku execution
        return Result.failure(NotImplementedError("Shizuku not yet implemented"))
    }
}
```

## Integration with DeviceInfoProvider

Add to existing `DeviceInfoProvider.kt`:

```kotlin
class DeviceInfoProvider(context: Context) {
    
    private val privilegeManager = PrivilegeManager(context)
    
    suspend fun getCpuFrequencies(): Result<List<Long>> {
        if (!privilegeManager.hasAnyPrivilege()) {
            return Result.failure(SecurityException("Root or Shizuku required"))
        }
        
        return try {
            val cpuCount = getCpuCount()
            val frequencies = mutableListOf<Long>()
            
            for (i in 0 until cpuCount) {
                val result = privilegeManager.executeCommand(
                    "cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
                )
                
                result.onSuccess { output ->
                    output.firstOrNull()?.toLongOrNull()?.let { 
                        frequencies.add(it)
                    }
                }
            }
            
            Result.success(frequencies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun setCpuMaxFrequency(cpuId: Int, frequency: Long): Result<Unit> {
        if (!privilegeManager.hasAnyPrivilege()) {
            return Result.failure(SecurityException("Root or Shizuku required"))
        }
        
        return privilegeManager.executeCommand(
            "echo $frequency > /sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_max_freq"
        ).map { }
    }
    
    private fun getCpuCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }
}
```

## UI Integration

### Request Root Access

```kotlin
@Composable
fun RootAccessScreen(
    viewModel: SettingsViewModel
) {
    val hasRoot by viewModel.hasRoot.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (hasRoot) {
            Text(
                text = "✓ Root Access Granted",
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "Root Access Required",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "MKM requires root access to modify CPU/GPU settings. " +
                      "Please grant root permission when prompted.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = { viewModel.requestRootAccess() }) {
                Text("Request Root Access")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(onClick = { viewModel.useShizuku() }) {
                Text("Use Shizuku Instead")
            }
        }
    }
}
```

### ViewModel

```kotlin
class SettingsViewModel : ViewModel() {
    
    private val _hasRoot = MutableStateFlow(false)
    val hasRoot: StateFlow<Boolean> = _hasRoot.asStateFlow()
    
    init {
        checkRootAccess()
    }
    
    private fun checkRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val granted = Shell.isAppGrantedRoot() ?: false
            _hasRoot.value = granted
        }
    }
    
    fun requestRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            // This will trigger Magisk/SuperSU prompt
            Shell.getShell()
            checkRootAccess()
        }
    }
    
    fun useShizuku() {
        // Navigate to Shizuku setup
    }
}
```

## Error Handling

```kotlin
sealed class RootResult<out T> {
    data class Success<T>(val data: T) : RootResult<T>()
    data class Error(val message: String, val isRootDenied: Boolean = false) : RootResult<Nothing>()
}

suspend fun <T> executeRootOperation(
    operation: suspend () -> T
): RootResult<T> {
    return try {
        if (!Shell.isAppGrantedRoot()) {
            RootResult.Error("Root access denied", isRootDenied = true)
        } else {
            RootResult.Success(operation())
        }
    } catch (e: SecurityException) {
        RootResult.Error("Permission denied: ${e.message}", isRootDenied = true)
    } catch (e: Exception) {
        RootResult.Error("Operation failed: ${e.message}")
    }
}

// Usage
when (val result = executeRootOperation { setCpuFrequency(0, 2000000) }) {
    is RootResult.Success -> {
        // Success
    }
    is RootResult.Error -> {
        if (result.isRootDenied) {
            // Show root required dialog
        } else {
            // Show error message
        }
    }
}
```

## Testing

### Unit Tests

```kotlin
class RootCommandTest {
    
    @Test
    fun testRootAvailable() {
        // This test should run on rooted device/emulator
        assertTrue(Shell.isAppGrantedRoot() ?: false)
    }
    
    @Test
    fun testReadCpuFrequency() = runBlocking {
        val result = Shell.cmd(
            "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
        ).exec()
        
        assertTrue(result.isSuccess)
        assertTrue(result.out.isNotEmpty())
        assertNotNull(result.out.first().toLongOrNull())
    }
}
```

### Instrumented Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class DeviceInfoProviderTest {
    
    private lateinit var provider: DeviceInfoProvider
    
    @Before
    fun setup() {
        provider = DeviceInfoProvider(
            ApplicationProvider.getApplicationContext()
        )
        
        // Ensure root access
        assertTrue("Root required for tests", Shell.isAppGrantedRoot() ?: false)
    }
    
    @Test
    fun testGetCpuFrequencies() = runBlocking {
        val result = provider.getCpuFrequencies()
        
        assertTrue(result.isSuccess)
        val frequencies = result.getOrNull()
        assertNotNull(frequencies)
        assertTrue(frequencies!!.isNotEmpty())
    }
}
```

## Common Paths for MKM

```kotlin
object SystemPaths {
    // CPU
    fun cpuFreqPath(cpuId: Int) = 
        "/sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_cur_freq"
    
    fun cpuMaxFreqPath(cpuId: Int) = 
        "/sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_max_freq"
    
    fun cpuMinFreqPath(cpuId: Int) = 
        "/sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_min_freq"
    
    fun cpuGovernorPath(cpuId: Int) = 
        "/sys/devices/system/cpu/cpu$cpuId/cpufreq/scaling_governor"
    
    // GPU
    const val GPU_FREQ = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
    const val GPU_MAX_FREQ = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
    const val GPU_MIN_FREQ = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
    const val GPU_GOVERNOR = "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
    
    // Thermal
    fun thermalZonePath(zoneId: Int) = 
        "/sys/class/thermal/thermal_zone$zoneId/temp"
    
    // Memory
    const val MEM_INFO = "/proc/meminfo"
    const val SWAP_INFO = "/proc/swaps"
}
```

## Security Best Practices

1. **Validate Input**: Always sanitize user input before executing commands
2. **Whitelist Commands**: Use a whitelist of allowed operations
3. **Request Minimal Permissions**: Only request root when necessary
4. **Show Transparency**: Clearly explain what root operations do
5. **Provide Alternatives**: Offer Shizuku as non-root alternative
6. **Handle Denial Gracefully**: Don't crash if root denied

## Next Steps

1. Add libsu dependency to `build.gradle.kts`
2. Initialize Shell in Application class
3. Add root check to DeviceInfoProvider
4. Implement CPU/GPU frequency reading with root
5. Implement frequency modification with root
6. Add UI to request root access
7. Test on rooted device or emulator

## References

- [Libsu Documentation](https://github.com/topjohnwu/libsu)
- [rooted-adb.md](./rooted-adb.md) - Complete ADB root guide
- [root-adb-testing-results.md](./root-adb-testing-results.md) - Test results
