# Problem Statement: Minimal Kernel Manager (MKM)

## Overview
**App Name:** Minimal Kernel Manager  
**Package:** `com.ivarna.mkm`  
**Target Platform:** Android (SDK 24+)

## Problem Description

Android devices, especially those with limited RAM (≤4GB), often suffer from performance degradation when running multiple applications or resource-intensive tasks. While modern Android has built-in memory management, many devices lack adequate swap space configuration, leading to:

- Frequent app reloads and background process kills
- Poor multitasking performance
- System slowdowns under memory pressure
- Limited ability to run memory-intensive applications

Existing solutions either require complex command-line operations, lack persistence across reboots, or provide no user-friendly interface for swap management.

## Solution: Persistent Swap Creation Feature

### Feature 1: Create Persistent Swap with Desirable Size

**Objective:** Enable users to create and manage persistent swap files on their Android devices with customizable sizes, providing enhanced memory management and improved multitasking capabilities.

### Technical Approach

#### Access Methods
The application will support two privilege escalation methods:

1. **Shizuku Integration**
   - Non-root solution for users with Shizuku installed
   - Leverages ADB permissions for system-level operations
   - Safer alternative to full root access
   - Requires user to grant Shizuku permissions

2. **Root Access**
   - Traditional superuser (su) access
   - Full system-level control
   - Requires rooted device with root management solution (Magisk, KernelSU, etc.)
   - Fallback option when Shizuku is unavailable

#### Core Functionality

**Swap File Creation:**
- User selects desired swap size (256MB, 512MB, 1GB, 2GB, 4GB, custom)
- App creates swap file at optimal location (`/data/local/swap` or user-specified path)
- Automatic format and activation of swap space
- Validation of available storage before creation

**Persistence Management:**
- Automatic swap activation on device boot
- Integration with init scripts or boot service
- Swap state monitoring and health checks
- Option to disable/enable swap without deletion

**Safety Features:**
- Storage space verification before creation
- Automatic cleanup on errors
- Warning for low storage scenarios
- Backup of system configurations before modifications

### User Experience

**Setup Flow:**
1. User launches app and grants necessary permissions (Shizuku or Root)
2. App detects current memory and swap configuration
3. User selects desired swap size from preset options or enters custom value
4. App validates available storage and creates swap file
5. Automatic activation and persistence configuration
6. Confirmation with current system memory status

**Monitoring:**
- Real-time display of swap usage statistics
- Memory pressure indicators
- Performance impact metrics
- Option to modify or remove swap configuration

## Success Criteria

- ✅ Successfully create swap files ranging from 256MB to 8GB
- ✅ Swap persists across device reboots
- ✅ Support both Shizuku and root access methods
- ✅ No system instability or boot issues
- ✅ Clear user feedback during all operations
- ✅ Graceful error handling and recovery

## Technical Constraints

- Minimum Android SDK: 24 (Android 7.0)
- Target SDK: 36 (Latest)
- Requires either Shizuku or root access
- File system must support large files (ext4, f2fs)
- Sufficient storage space for swap file

## Future Considerations

- Swap on zRAM configuration
- Swap priority management
- Multiple swap file support
- Automatic size recommendations based on device RAM
- Swap performance analytics and optimization suggestions
- Kernel parameter tuning (swappiness, vfs_cache_pressure)

---

**Document Version:** 1.0  
**Last Updated:** January 20, 2026  
**Status:** Initial Specification
