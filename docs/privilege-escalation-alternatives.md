# Android Privilege Escalation Alternatives for MKM

## Complete Comparison Matrix

Based on research of available Android privilege escalation methods for app development.

---

## 1. **Root (libsu)** ✅ CURRENT

### Overview
Direct superuser access via su binary.

### Requirements
- Rooted device (Magisk, KernelSU, etc.)

### Pros
- ✅ Most powerful - full system access
- ✅ Already implemented in MKM
- ✅ Well-established library (topjohnwu/libsu)
- ✅ No additional app dependencies
- ✅ Persistent across reboots
- ✅ 6.8k GitHub stars

### Cons
- ❌ Requires root access
- ❌ Security concerns (full root)
- ❌ Root detection by some apps

### Implementation
```gradle
implementation "com.github.topjohnwu.libsu:core:6.0.0"
```

**Status:** ✅ Implemented in v1.0  
**GitHub:** https://github.com/topjohnwu/libsu

---

## 2. **Shizuku** 📋 RECOMMENDED FOR v1.1

### Overview
Provides privileged API access via ADB shell permissions or root.

### Requirements
- ADB wireless debugging (Android 11+) OR root
- Shizuku app installed

### Pros
- ✅ Huge user base (~10k+ stars)
- ✅ No root required (uses ADB shell)
- ✅ Wireless debugging on Android 11+
- ✅ Multiple apps can use simultaneously
- ✅ Well documented
- ✅ Active development

### Cons
- ❌ Complex API (UserService pattern in v13+)
- ❌ Requires restart after reboot (ADB mode)
- ❌ Deprecated `newProcess()` method
- ❌ Steeper learning curve

### Implementation Effort
~8-12 hours (UserService integration)

### Latest Version
- 13.1.5 (Maven Central)
- 14.x (not yet on Maven)

**Status:** ⏳ Planned for v1.1  
**GitHub:** https://github.com/RikkaApps/Shizuku  
**API:** https://github.com/RikkaApps/Shizuku-API

---

## 3. **Sui** 🔄 AUTO-INITIALIZED WITH SHIZUKU

### Overview
Magisk module that provides Shizuku API for rooted devices automatically.

### Requirements
- Magisk installed
- Root access

### Pros
- ✅ Automatic - no user setup beyond Magisk
- ✅ Uses same Shizuku API
- ✅ No separate app needed
- ✅ Persistent (Magisk module)
- ✅ 3.8k GitHub stars

### Cons
- ❌ Requires root (defeats Shizuku's purpose)
- ❌ Magisk dependency
- ❌ Less active (last update 2023)

### Key Point
**If targeting Shizuku API, Sui support comes for free!**  
Sui initializes automatically with ShizukuProvider.

**Status:** 🎁 Bonus - comes with Shizuku  
**GitHub:** https://github.com/RikkaApps/Sui

---

## 4. **Dhizuku** ⚠️ LIMITED USE CASE

### Overview
Device Owner-based privilege escalation API.

### Requirements
- Device Owner set (requires factory reset if not initial setup)
- Dhizuku app installed

### Pros
- ✅ Simple API (direct `newProcess()`)
- ✅ Available on Maven Central
- ✅ No ADB restart needed
- ✅ Easy to integrate (~2-3 hours)

### Cons
- ❌ **Requires Device Owner setup** (major barrier!)
- ❌ **Factory reset needed** if not set during initial setup
- ❌ Only ONE Device Owner per device
- ❌ Small user base (165 stars)
- ❌ Conflicts with other DO apps
- ❌ Less tested

### User Setup (Complex!)
```shell
adb shell dpm set-device-owner com.rosan.dhizuku/.server.DhizukuDAReceiver
```

**Status:** ⏸️ Not recommended as primary  
**Possible:** Optional v1.2 feature for DO users  
**GitHub:** https://github.com/iamr0s/Dhizuku-API

---

## 5. **Island** 🏝️ NOT SUITABLE

### Overview
Work profile/Device Policy Controller app with open API.

### Requirements
- Island app installed
- Profile owner or Device owner permissions

### Why Not Suitable for MKM
- ❌ Designed for app isolation, not system control
- ❌ APIs focus on work profile management
- ❌ Cannot access kernel parameters (CPU/GPU)
- ❌ Limited to Android's DPC capabilities
- ❌ Outdated (last update 2023)

### Use Case
App sandboxing, privacy, work profiles - NOT system tuning.

**Status:** ❌ Not applicable  
**GitHub:** https://github.com/oasisfeng/island (3.5k stars)

---

## 6. **Direct ADB Commands** 🔌 NOT PRACTICAL

### Overview
Execute shell commands via ADB connection.

### Why Not Suitable
- ❌ Requires USB cable or wireless ADB
- ❌ Connection drops frequently
- ❌ Poor user experience
- ❌ Not practical for regular app use
- ❌ Would need constant setup

### Use Case
Development/debugging only.

**Status:** ❌ Not applicable for production app

---

## 7. **Custom ROM / Kernel Modules** 🛠️ OUT OF SCOPE

### Overview
Build custom ROM or kernel modules for device.

### Why Not Suitable
- ❌ Requires device unlock & custom ROM flashing
- ❌ Device-specific
- ❌ Maintenance nightmare
- ❌ Not distributable via F-Droid/Play Store
- ❌ Far beyond app scope

**Status:** ❌ Not applicable

---

## Recommendations for MKM

### Short Term (v1.0) ✅
**Root-only (libsu)**
- Already implemented
- Works reliably
- Clear requirement

### Medium Term (v1.1) 📋
**Add Shizuku support**
- Priority: HIGH
- Massive user base
- Worth the implementation effort
- Makes MKM accessible to non-root users with ADB

### Optional (v1.2) 🎁
**Add Dhizuku support**
- Priority: LOW
- For Device Owner users only
- Market as "bonus feature"
- Simple to implement (~2-3 hours)

---

## Final Implementation Roadmap

```
┌─────────────────────────────────────────────────┐
│ MKM v1.0 (February 2026)                       │
│ • Root access via libsu              ✅         │
│ • Fully functional                              │
└─────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────┐
│ MKM v1.1 (Planned - March 2026)                │
│ • Root access via libsu              ✅         │
│ • Shizuku support                    📋         │
│   - Non-root users via ADB                      │
│   - Sui support automatic                       │
│ • Fallback logic: Shizuku → Root → Error       │
└─────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────┐
│ MKM v1.2 (Optional - Future)                   │
│ • Root access via libsu              ✅         │
│ • Shizuku support                    ✅         │
│ • Dhizuku support (optional)         🎁         │
│ • Fallback: Shizuku → Root → Dhizuku → Error   │
└─────────────────────────────────────────────────┘
```

---

## User Count Estimates

Based on GitHub stars and community size:

| Method | Potential Users | Setup Difficulty | MKM Priority |
|--------|----------------|------------------|--------------|
| Root | ~500k-1M | Medium | ✅ High |
| Shizuku | ~100k-500k | Easy (Android 11+) | 📋 High |
| Sui | ~10k-50k | Easy (if rooted) | 🎁 Bonus |
| Dhizuku | ~1k-5k | Very Hard | ⏸️ Low |

---

## Conclusion

**For MKM v1.1, prioritize Shizuku integration.**

The combination of:
1. **Root (libsu)** - Maximum power
2. **Shizuku** - Wide accessibility
3. **Sui** - Automatic with Shizuku

...will give MKM the broadest user base and best user experience.

Dhizuku can be added later if there's demand from Device Owner users, but should NOT be the primary non-root method due to setup complexity.

---

**Last Updated:** February 13, 2026  
**Author:** MKM Development Team
