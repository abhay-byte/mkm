# Dhizuku vs Shizuku: Analysis for MKM

## Overview

**Dhizuku** is an alternative to Shizuku that provides elevated permissions through **Device Owner** instead of shell/root access.

## Technical Comparison

### Dhizuku
- **Permission Model:** Device Owner
- **Latest Version:** 2.5.4 (October 2025)
- **Maven:** `io.github.iamr0s:Dhizuku-API:2.5.4`
- **License:** MIT
- **Stars:** 165
- **Repository:** https://github.com/iamr0s/Dhizuku-API

### Shizuku
- **Permission Model:** Shell (ADB) or Root
- **Latest Version:** 13.1.5 (stable on Maven Central)
- **Maven:** `dev.rikka.shizuku:api:13.1.5`
- **License:** Apache 2.0
- **Stars:** ~10,000+ (much more popular)
- **Repository:** https://github.com/RikkaApps/Shizuku

## API Simplicity

### Dhizuku (Simpler)
```java
// Initialize
Dhizuku.init(context); // returns boolean

// Check permission
if (Dhizuku.isPermissionGranted()) {
    // Execute command - simple and straightforward
    DhizukuRemoteProcess process = Dhizuku.newProcess(
        new String[]{"sh", "-c", command},
        null,
        null
    );
    // Read output
    InputStream in = process.getInputStream();
}
```

### Shizuku 13.x (More Complex)
```kotlin
// Requires UserService pattern (no simple newProcess)
// Must define AIDL interfaces
// More complex setup with ContentProvider
// Deprecated newProcess() method
```

## User Setup Requirements

### Dhizuku
**Requirement:** Device Owner must be set
- ⚠️ **Requires factory reset** if not set during initial device setup
- Can only have ONE Device Owner app on device
- Complex ADB command: `adb shell dpm set-device-owner com.rosan.dhizuku/.server.DhizukuDAReceiver`
- More restrictive than Shizuku

### Shizuku
**Requirement:** Either root OR wireless debugging
- ✅ No factory reset needed
- ✅ Can use wireless debugging on Android 11+
- ✅ Falls back to root if available
- More user-friendly setup

## Pros & Cons for MKM

### Dhizuku Pros
1. ✅ **Simple API** - Direct `newProcess()` without deprecation
2. ✅ **Easy Integration** - Available on Maven Central
3. ✅ **No ADB restart** - Device Owner persists across reboots
4. ✅ **Well documented** - Clear examples

### Dhizuku Cons
1. ❌ **Device Owner requirement** - Major barrier to adoption
2. ❌ **Factory reset needed** - If not set up during initial setup
3. ❌ **Single Device Owner** - Conflicts with other Device Owner apps
4. ❌ **Small user base** - Only 165 GitHub stars
5. ❌ **Less tested** - Fewer users = fewer bug reports

### Shizuku Pros
1. ✅ **Huge user base** - Well tested and widely adopted
2. ✅ **No factory reset** - Easy to set up
3. ✅ **Multiple apps** - Many apps can use Shizuku simultaneously
4. ✅ **Wireless debugging** - No computer needed on Android 11+

### Shizuku Cons
1. ❌ **Complex API** - UserService pattern required in 13.x
2. ❌ **Deprecated methods** - newProcess() being removed
3. ❌ **Requires restart** - ADB or wireless debugging needs restart after reboot

## Recommendation for MKM

### Short Term (v1.0)
**Status Quo:** Root-only
- ✅ Already implemented
- ✅ Works reliably
- ✅ No additional dependencies

### Medium Term (v1.1)
**Option A: Shizuku** (Recommended)
- Larger user base means more potential users
- Better compatibility with other apps
- Worth the API complexity investment
- Users already familiar with it

**Option B: Dhizuku**
- Easier to implement
- Smaller user base limits adoption
- Device Owner setup barrier
- Could lead to user frustration

### Long Term (v1.2+)
**Ideal: Triple Support**
```
MKM v1.2
├── Root (libsu) - Most reliable
├── Shizuku - Wide adoption
└── Dhizuku - For Device Owner users
```

## Implementation Complexity

### Dhizuku Integration
```gradle
dependencies {
    implementation "io.github.iamr0s:Dhizuku-API:2.5.4"
}
```

```kotlin
// Simple implementation
fun execDhizuku(command: String): CommandResult {
    if (!Dhizuku.init(context)) {
        return CommandResult(-1, "", "Dhizuku not available")
    }
    
    if (!Dhizuku.isPermissionGranted()) {
        return CommandResult(-1, "", "Permission not granted")
    }
    
    val process = Dhizuku.newProcess(
        arrayOf("sh", "-c", command),
        null,
        null
    )
    
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    process.waitFor()
    
    return CommandResult(process.exitValue(), output.trim(), error.trim())
}
```

**Estimated effort:** ~2-3 hours

### Shizuku 13.x Integration
**Estimated effort:** ~8-12 hours (requires UserService implementation, AIDL, etc.)

## Verdict

### For MKM v1.1

**Decision: Prioritize Shizuku**

**Reasoning:**
1. **User Base:** Shizuku has 50-100x more users
2. **Ecosystem:** Many apps support Shizuku, users already have it
3. **Setup:** Easier for users (no factory reset)
4. **Long-term:** Better for app growth and adoption

**Dhizuku Consideration:**
- Add Dhizuku as **optional secondary method** in v1.2
- Market as "bonus feature" for Device Owner users
- Don't make it primary access method

### Implementation Roadmap

```
v1.0 (Current)  → Root only ✅
v1.1 (Next)     → Root + Shizuku
v1.2 (Future)   → Root + Shizuku + Dhizuku (optional)
```

## Conclusion

While **Dhizuku is easier to integrate**, **Shizuku should be prioritized** due to its larger user base and easier setup requirements. Dhizuku can be added as an optional bonus feature later for users who already have Device Owner set up for other purposes.

The Device Owner requirement is a significant barrier that would limit MKM's potential user base more than the implementation complexity of Shizuku's API.
