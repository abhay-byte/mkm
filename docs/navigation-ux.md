# Navigation & Settings UX Documentation

## Overview

This document defines the user experience for navigation and system settings pages in Minimal Kernel Manager (MKM). The app uses a **bottom navigation bar** for primary navigation between CPU, GPU, RAM, and Swap management features.

**Design System:** Material Design 3 Expressive  
**Navigation Pattern:** Bottom Navigation Bar  
**Screen Categories:** Dashboard, Settings, Monitoring

---

## Table of Contents

1. [App Navigation Architecture](#1-app-navigation-architecture)
2. [Bottom Navigation Bar](#2-bottom-navigation-bar)
3. [Home/Dashboard Screen](#3-homedashboard-screen)
4. [RAM Settings Page](#4-ram-settings-page)
5. [CPU Settings Page](#5-cpu-settings-page)
6. [GPU Settings Page](#6-gpu-settings-page)
7. [Swap Management Page](#7-swap-management-page)
8. [Navigation Patterns & User Flows](#8-navigation-patterns--user-flows)
9. [Component Specifications](#9-component-specifications)
10. [Responsive Behavior](#10-responsive-behavior)

---

## 1. App Navigation Architecture

### Information Architecture

```
Minimal Kernel Manager (MKM)
│
├── Home/Dashboard (default)
│   ├── System Overview
│   ├── Quick Stats (RAM, CPU, GPU, Swap)
│   └── Quick Actions
│
├── RAM (Memory Management)
│   ├── Memory Information
│   ├── Swap Configuration
│   └── Memory Monitoring
│
├── CPU (Processor Control)
│   ├── CPU Information
│   ├── Governor Configuration
│   ├── Frequency Control
│   └── CPU Monitoring
│
├── GPU (Graphics Control)
│   ├── GPU Information
│   ├── Frequency Control
│   └── GPU Monitoring
│
└── Settings (App Configuration)
    ├── General Settings
    ├── Permissions Management
    ├── Notifications
    ├── Data & Storage
    ├── Theme & Appearance
    ├── Advanced Options
    ├── Documentation
    └── About
```

### Navigation Strategy

**Primary Navigation:** Bottom Navigation Bar
- 4 main sections: Home, RAM, CPU, GPU
- Always visible for quick access
- Single tap navigation between sections

**Secondary Navigation:** 
- Overflow menu in top app bar for additional settings
- Modal bottom sheets for action dialogs
- Full-screen sheets for detailed configurations

---

## 2. Bottom Navigation Bar

### Design Specifications

#### Visual Design

**Component:** Material 3 Navigation Bar (Expressive)

```
┌───────────────────────────────────────────────────────────────┐
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐         │
│  │ 🏠   │  │ 💾   │  │ ⚙️   │  │ 🎮   │  │ ⚙️   │         │
│  │ Home │  │ RAM  │  │ CPU  │  │ GPU  │  │ Set  │         │
│  └──────┘  └──────┘  └──────┘  └──────┘  └──────┘         │
└───────────────────────────────────────────────────────────────┘
```

#### Navigation Items

| Position | Icon | Label | Destination | Purpose |
|----------|------|-------|-------------|---------|
| 1 | `home` | Home | Dashboard | System overview & quick actions |
| 2 | `memory` | RAM | RAM Management | Memory & swap management |
| 3 | `developer_board` | CPU | CPU Control | Processor control & monitoring |
| 4 | `videogame_asset` | GPU | GPU Control | Graphics control & monitoring |
| 5 | `settings` | Settings | App Settings | App configuration & preferences |

#### Component Properties

```kotlin
NavigationBar(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 3.dp,
    modifier = Modifier
        .fillMaxWidth()
        .height(80.dp) // Standard M3 height
) {
    // Navigation items
}
```

#### Navigation Item States

**Default State:**
```kotlin
NavigationBarItem(
    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
    label = { Text("Home") },
    selected = false,
    onClick = { },
    colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    )
)
```

**Selected State:**
- Filled icon variant
- Active indicator background
- Emphasized text color
- Subtle badge animation on selection

**Interaction:**
- Tap: Navigate to destination with crossfade transition
- Long press: Show tooltip with destination name
- No swipe gestures (reserved for page content)

### Behavior & Animation

#### Selection Animation

```kotlin
// Icon crossfade between outlined and filled variants
AnimatedContent(
    targetState = isSelected,
    transitionSpec = {
        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessHigh)) with
        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh))
    }
) { selected ->
    Icon(
        imageVector = if (selected) Icons.Filled.Home else Icons.Outlined.Home,
        contentDescription = null
    )
}
```

#### Indicator Animation

- Shape morph from hidden to pill shape (16dp corners)
- Scale and fade in with expressive spring
- Follow icon with slight delay for polish

```kotlin
val indicatorShape by animateShapeAsState(
    targetValue = if (selected) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp),
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
)
```

### Accessibility

```kotlin
NavigationBarItem(
    icon = { Icon(...) },
    label = { Text("Home") },
    selected = currentRoute == "home",
    onClick = { navigateTo("home") },
    modifier = Modifier.semantics {
        contentDescription = "Home tab, navigate to system overview"
        role = Role.Tab
        stateDescription = if (selected) "Selected" else "Not selected"
    }
)
```

**Requirements:**
- Each item has descriptive content description
- Selected state announced by screen readers
- Minimum 48dp touch target (80dp height provides this)
- Clear focus indicators for keyboard navigation

---

## 3. Home/Dashboard Screen

### Purpose

Central hub showing system overview and quick access to all features.

### Layout Structure

```
┌─────────────────────────────────────────────┐
│ ← Minimal Kernel Manager          [⋮]      │ Top App Bar
├─────────────────────────────────────────────┤
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓  │
│ ┃ System Overview Card                 ┃  │ Hero moment
│ ┃                                       ┃  │
│ ┃   Device: Pixel 8 Pro                ┃  │
│ ┃   Kernel: 6.1.68                     ┃  │
│ ┃   Shizuku: ● Active                  ┃  │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛  │
│                                             │
│ Quick Stats                                 │ Section header
│ ┌───────────────┐ ┌───────────────┐       │
│ │ RAM           │ │ CPU           │       │ Stat cards
│ │ 3.2 / 8.0 GB  │ │ 45% Usage     │       │
│ │ ▓▓▓▓▓░░░      │ │ 2.4 GHz       │       │
│ └───────────────┘ └───────────────┘       │
│ ┌───────────────┐ ┌───────────────┐       │
│ │ GPU           │ │ Swap          │       │
│ │ 380 MHz       │ │ 2.0 GB        │       │
│ │ 23% Load      │ │ ✓ Active      │       │
│ └───────────────┘ └───────────────┘       │
│                                             │
│ Quick Actions                               │
│ ┌───────────────────────────────────────┐  │
│ │ [↻] Refresh Stats                     │  │ Action list
│ │ [+] Create Swap                       │  │
│ │ [⚙] Advanced Settings                 │  │
│ └───────────────────────────────────────┘  │
│                                             │
├─────────────────────────────────────────────┤
│  🏠    💾    ⚙️    🎮    ⚙️               │ Bottom nav
└─────────────────────────────────────────────┘
```

### Component Breakdown

#### 1. Top App Bar

```kotlin
TopAppBar(
    title = { 
        Text(
            "Minimal Kernel Manager",
            style = MaterialTheme.typography.titleLarge
        )
    },
    actions = {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.MoreVert, "More options")
        }
    }
)
```

**Overflow Menu:**
- Settings
- Permissions
- About
- Documentation

#### 2. System Overview Card (Hero Moment)

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(32.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
) {
    Column(modifier = Modifier.padding(24.dp)) {
        // Device info with emphasized typography
        Text(
            text = "Pixel 8 Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Kernel: 6.1.68",
            style = MaterialTheme.typography.bodyLarge
        )
        
        // Status indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                tint = Color.Green,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Shizuku Active", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

**Visual Treatment:**
- Extra large corner radius (32dp)
- Primary container color
- Emphasized typography for device name
- Status indicator with semantic color (green = active)

#### 3. Quick Stats Grid

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    item { RamStatCard() }
    item { CpuStatCard() }
    item { GpuStatCard() }
    item { SwapStatCard() }
}
```

**Individual Stat Card:**
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .height(120.dp),
    shape = RoundedCornerShape(24.dp),
    onClick = { navigateToDetail() }
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Icon and label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("RAM", style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.Memory, contentDescription = null)
        }
        
        // Primary value
        Text(
            text = "3.2 GB",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Secondary info
        LinearProgressIndicator(
            progress = 0.4f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

**Interaction:**
- Tap card: Navigate to detailed page
- Visual feedback: Surface elevation increase on press
- Animation: Expressive spring on navigation

#### 4. Quick Actions List

```kotlin
Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    QuickActionItem(
        icon = Icons.Default.Refresh,
        label = "Refresh Stats",
        onClick = { refreshStats() }
    )
    QuickActionItem(
        icon = Icons.Default.Add,
        label = "Create Swap",
        onClick = { navigateToSwapCreation() }
    )
    QuickActionItem(
        icon = Icons.Default.Settings,
        label = "Advanced Settings",
        onClick = { navigateToSettings() }
    )
}
```

### User Interactions

**Primary Actions:**
1. Tap stat card → Navigate to detailed page (RAM/CPU/GPU)
2. Tap quick action → Execute action or navigate
3. Tap bottom nav item → Switch to another section
4. Pull to refresh → Update all stats

**Secondary Actions:**
1. Long press stat card → Show quick options menu
2. Tap overflow menu → Access app settings
3. Swipe card → Reveal additional info (optional)

---

## 4. RAM Page

### Purpose

Manage system RAM, configure swap, and monitor memory usage.

### Layout Structure

```
┌─────────────────────────────────────────────┐
│ ← RAM                              [⋮]      │
├─────────────────────────────────────────────┤
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓  │
│ ┃ Memory Overview                      ┃  │
│ ┃                                       ┃  │
│ ┃   3.2 GB / 8.0 GB                    ┃  │ Hero stats
│ ┃   ▓▓▓▓▓▓▓▓░░░░░░░░░                 ┃  │
│ ┃   40% Used · 4.8 GB Free             ┃  │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛  │
│                                             │
│ Swap Configuration                          │
│ ┌───────────────────────────────────────┐  │
│ │ Current Swap                          │  │
│ │ 2.0 GB · Active since boot           │  │
│ │ /data/local/swap                      │  │
│ │                                       │  │
│ │ Usage: 156 MB / 2.0 GB               │  │
│ │ ▓░░░░░░░░░░░                         │  │
│ │                                       │  │
│ │ [Disable Swap]  [Reconfigure]        │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Memory Details                              │
│ ┌───────────────────────────────────────┐  │
│ │ Available:        4.8 GB              │  │
│ │ Cached:          1.2 GB              │  │
│ │ Active:          2.1 GB              │  │
│ │ Inactive:        1.1 GB              │  │
│ │ Buffers:         256 MB              │  │
│ └───────────────────────────────────────┘  │
│                                             │
│              [+ Create New Swap]            │ FAB
├─────────────────────────────────────────────┤
│  🏠    💾    ⚙️    🎮                      │
└─────────────────────────────────────────────┘
```

### Component Details

#### 1. Memory Overview Card (Hero)

**Visual Treatment:**
- Extra large display typography for memory values
- Full-width progress indicator with semantic colors
- Primary container color for emphasis
- Real-time updating values

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(32.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main memory value - emphasized
        Text(
            text = "3.2 GB / 8.0 GB",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black
        )
        
        // Progress bar with semantic color
        val memoryColor = when (usagePercent) {
            in 0..60 -> Color(0xFF4CAF50)      // Green
            in 61..85 -> Color(0xFFFF9800)     // Orange
            else -> Color(0xFFF44336)          // Red
        }
        
        LinearProgressIndicator(
            progress = usagePercent / 100f,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = memoryColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        
        // Secondary info
        Text(
            text = "40% Used · 4.8 GB Free",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
```

#### 2. Swap Configuration Card

**State: Active Swap**
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Current Swap",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            // Status badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.2f)
            ) {
                Text(
                    "Active",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Swap details
        DetailRow(label = "Size", value = "2.0 GB")
        DetailRow(label = "Location", value = "/data/local/swap")
        DetailRow(label = "Active Since", value = "Boot")
        
        Spacer(Modifier.height(16.dp))
        
        // Usage indicator
        Text(
            "Usage: 156 MB / 2.0 GB",
            style = MaterialTheme.typography.bodyMedium
        )
        LinearProgressIndicator(
            progress = 0.078f,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showDisableDialog() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Disable")
            }
            FilledTonalButton(
                onClick = { showReconfigureSheet() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reconfigure")
            }
        }
    }
}
```

**State: No Swap**
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "No Swap Configured",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Create a swap file to improve multitasking performance",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { navigateToSwapCreation() },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create Swap")
        }
    }
}
```

#### 3. Memory Details Card

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Memory Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        MemoryDetailRow("Available", "4.8 GB", Color(0xFF4CAF50))
        MemoryDetailRow("Cached", "1.2 GB", Color(0xFF2196F3))
        MemoryDetailRow("Active", "2.1 GB", Color(0xFFFF9800))
        MemoryDetailRow("Inactive", "1.1 GB", Color(0xFF9E9E9E))
        MemoryDetailRow("Buffers", "256 MB", Color(0xFF9C27B0))
    }
}
```

#### 4. Create Swap FAB

```kotlin
ExtendedFloatingActionButton(
    onClick = { navigateToSwapCreation() },
    icon = { Icon(Icons.Default.Add, contentDescription = null) },
    text = { Text("Create New Swap") },
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    expanded = fabExpanded // Collapse on scroll
)
```

### User Flows

#### Flow 1: Create Swap
1. Tap "Create New Swap" FAB
2. Bottom sheet appears with size selection
3. Select size (512MB, 1GB, 2GB, 4GB, Custom)
4. Tap "Create" button
5. Loading indicator with progress
6. Success animation + confirmation
7. Card updates to show active swap

#### Flow 2: Disable Swap
1. Tap "Disable" button on swap card
2. Confirmation dialog appears
3. Confirm action
4. Loading indicator
5. Card transitions to "No Swap" state

#### Flow 3: Reconfigure Swap
1. Tap "Reconfigure" button
2. Bottom sheet with current settings
3. Adjust size or location
4. Confirm changes
5. Swap recreated with new settings

---

## 5. CPU Page

### Purpose

Monitor CPU usage, control frequencies, and manage CPU governor configuration.

### Layout Structure

```
┌─────────────────────────────────────────────┐
│ ← CPU                              [⋮]      │
├─────────────────────────────────────────────┤
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓  │
│ ┃ CPU Overview                         ┃  │
│ ┃                                       ┃  │
│ ┃   45%                                ┃  │ Current usage
│ ┃   ▓▓▓▓▓▓▓░░░░░░░░░                  ┃  │
│ ┃   Current: 2.4 GHz · Max: 3.2 GHz   ┃  │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛  │
│                                             │
│ CPU Cores                                   │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│ │ Core 0  │ │ Core 1  │ │ Core 2  │       │
│ │ 2.1 GHz │ │ 2.4 GHz │ │ 2.3 GHz │       │ Per-core info
│ │ 42%     │ │ 48%     │ │ 45%     │       │
│ └─────────┘ └─────────┘ └─────────┘       │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│ │ Core 3  │ │ Core 4  │ │ Core 5  │       │
│ │ ...     │ │ ...     │ │ ...     │       │
│ └─────────┘ └─────────┘ └─────────┘       │
│                                             │
│ Governor Settings                           │
│ ┌───────────────────────────────────────┐  │
│ │ Current: schedutil                    │  │
│ │                                       │  │
│ │ ○ performance                         │  │ Radio options
│ │ ● schedutil                           │  │
│ │ ○ powersave                           │  │
│ │ ○ ondemand                            │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Frequency Control                           │
│ ┌───────────────────────────────────────┐  │
│ │ Min: ━━━━━━━━━━━━━━━━━━ 0.3 GHz      │  │ Sliders
│ │ Max: ━━━━━━━━━━━━━━━━━━ 3.2 GHz      │  │
│ └───────────────────────────────────────┘  │
│                                             │
├─────────────────────────────────────────────┤
│  🏠    💾    ⚙️    🎮                      │
└─────────────────────────────────────────────┘
```

### Component Details

#### 1. CPU Overview Card (Hero)

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(32.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
    )
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CPU usage percentage - emphasized
        Text(
            text = "45%",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black
        )
        
        // Usage bar with animated gradient
        LinearProgressIndicator(
            progress = 0.45f,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        
        // Frequency info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoChip(label = "Current", value = "2.4 GHz")
            InfoChip(label = "Max", value = "3.2 GHz")
        }
    }
}
```

#### 2. CPU Cores Grid

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    items(cpuCores) { core ->
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Core ${core.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    core.frequency,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${core.usage}%",
                    style = MaterialTheme.typography.bodySmall
                )
                
                // Mini progress bar
                LinearProgressIndicator(
                    progress = core.usage / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(4.dp)
                )
            }
        }
    }
}
```

#### 3. Governor Settings Card

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Governor Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Radio button group
        governors.forEach { governor ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectGovernor(governor) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedGovernor == governor,
                    onClick = { selectGovernor(governor) }
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        governor.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        governor.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

**Governor Options:**
- **performance** - Maximum frequency always
- **schedutil** - Balanced, scheduler-driven (recommended)
- **powersave** - Minimum frequency, best battery
- **ondemand** - Dynamic based on load
- **interactive** - Responsive, quick ramp-up

#### 4. Frequency Control Card

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Frequency Control",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        // Min frequency slider
        Text(
            "Minimum Frequency",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = minFreq,
                onValueChange = { minFreq = it },
                valueRange = 0.3f..3.2f,
                steps = 9,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "${minFreq.format(1)} GHz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(80.dp)
            )
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Max frequency slider
        Text(
            "Maximum Frequency",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = maxFreq,
                onValueChange = { maxFreq = it },
                valueRange = 0.3f..3.2f,
                steps = 9,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "${maxFreq.format(1)} GHz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(80.dp)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Apply button
        Button(
            onClick = { applyFrequencySettings() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.percent)
        ) {
            Text("Apply Settings")
        }
    }
}
```

### Real-time Monitoring

**Auto-refresh:**
- CPU usage updates every 1 second
- Per-core frequency updates every 2 seconds
- Smooth animated transitions between values

```kotlin
// Animated value updates
val animatedUsage by animateFloatAsState(
    targetValue = currentUsage,
    animationSpec = spring(stiffness = Spring.StiffnessMedium)
)
```

---

## 6. GPU Page

### Purpose

Monitor GPU usage, control GPU frequencies, and track graphics performance.

### Layout Structure

```
┌─────────────────────────────────────────────┐
│ ← GPU                              [⋮]      │
├─────────────────────────────────────────────┤
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓  │
│ ┃ GPU Overview                         ┃  │
│ ┃                                       ┃  │
│ ┃   23%                                ┃  │ Current load
│ ┃   ▓▓░░░░░░░░░░░░░░░░                ┃  │
│ ┃   380 MHz · Max: 900 MHz            ┃  │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛  │
│                                             │
│ GPU Information                             │
│ ┌───────────────────────────────────────┐  │
│ │ Vendor:     Mali                      │  │
│ │ Model:      G710 MP7                  │  │ GPU info
│ │ Driver:     r40p0-01eac0              │  │
│ │ OpenGL:     ES 3.2                    │  │
│ │ Vulkan:     1.3.274                   │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Frequency Control                           │
│ ┌───────────────────────────────────────┐  │
│ │ Current Frequency: 380 MHz            │  │
│ │                                       │  │
│ │ ○  200 MHz - Power Save               │  │ Freq presets
│ │ ●  380 MHz - Balanced                 │  │
│ │ ○  680 MHz - Performance              │  │
│ │ ○  900 MHz - Maximum                  │  │
│ │                                       │  │
│ │ Custom: ━━━━━━━━━━━━━━ 380 MHz       │  │ Custom slider
│ └───────────────────────────────────────┘  │
│                                             │
│ Performance Stats                           │
│ ┌───────────────────────────────────────┐  │
│ │ Frames Rendered:    45,832            │  │
│ │ Avg FPS:           60                 │  │ Stats
│ │ Throttling:        No                 │  │
│ │ Temperature:       42°C               │  │
│ └───────────────────────────────────────┘  │
│                                             │
├─────────────────────────────────────────────┤
│  🏠    💾    ⚙️    🎮                      │
└─────────────────────────────────────────────┘
```

### Component Details

#### 1. GPU Overview Card (Hero)

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(32.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    )
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // GPU load percentage
        Text(
            text = "23%",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black
        )
        
        // Load bar
        LinearProgressIndicator(
            progress = 0.23f,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        
        // Frequency info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoChip(label = "Current", value = "380 MHz")
            InfoChip(label = "Max", value = "900 MHz")
        }
    }
}
```

#### 2. GPU Information Card

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "GPU Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        InfoRow(label = "Vendor", value = "Mali")
        InfoRow(label = "Model", value = "G710 MP7")
        InfoRow(label = "Driver", value = "r40p0-01eac0")
        InfoRow(label = "OpenGL ES", value = "3.2")
        InfoRow(label = "Vulkan", value = "1.3.274")
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
```

#### 3. Frequency Control Card

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Frequency Control",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Preset options
        frequencyPresets.forEach { preset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectPreset(preset) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPreset == preset,
                    onClick = { selectPreset(preset) }
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${preset.frequency} MHz - ${preset.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Custom frequency slider
        Text(
            "Custom Frequency",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = customFreq,
                onValueChange = { customFreq = it },
                valueRange = 200f..900f,
                steps = 6,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "$customFreq MHz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(100.dp)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { applyGpuSettings() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.percent)
        ) {
            Text("Apply Settings")
        }
    }
}
```

**Frequency Presets:**
- **200 MHz** - Power Save (minimum power consumption)
- **380 MHz** - Balanced (default, good efficiency)
- **680 MHz** - Performance (smooth gaming)
- **900 MHz** - Maximum (peak performance)

#### 4. Performance Stats Card

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Performance Stats",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        StatRow(
            label = "Frames Rendered",
            value = "45,832",
            icon = Icons.Default.Videocam
        )
        StatRow(
            label = "Avg FPS",
            value = "60",
            icon = Icons.Default.Speed
        )
        StatRow(
            label = "Throttling",
            value = "No",
            valueColor = Color(0xFF4CAF50),
            icon = Icons.Default.Thermostat
        )
        StatRow(
            label = "Temperature",
            value = "42°C",
            valueColor = temperatureColor(42),
            icon = Icons.Default.DeviceThermostat
        )
    }
}
```

---

## 7. Settings Page

### Purpose

Configure app preferences, manage permissions, and access app information.

### Layout Structure

```
┌─────────────────────────────────────────────┐
│ ← Settings                         [⋮]      │
├─────────────────────────────────────────────┤
│                                             │
│ General                                     │ Section
│ ┌───────────────────────────────────────┐  │
│ │ Auto-refresh Stats          [○─────] │  │
│ │ Refresh Interval    [ 2 seconds  ▼] │  │
│ │ Start on Boot               [●─────] │  │
│ │ Run in Background           [●─────] │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Permissions                                 │
│ ┌───────────────────────────────────────┐  │
│ │ ✓ Shizuku                    Active   │  │
│ │ ○ Root Access                Grant    │  │
│ │ ✓ Notifications              Allowed  │  │
│ │ ✓ Storage Access             Allowed  │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Notifications                               │
│ ┌───────────────────────────────────────┐  │
│ │ Enable Notifications        [●─────] │  │
│ │ Swap Status Alerts          [●─────] │  │
│ │ Memory Warnings             [●─────] │  │
│ │ Performance Alerts          [○─────] │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Data & Storage                              │
│ ┌───────────────────────────────────────┐  │
│ │ Cache Size:            24.5 MB        │  │
│ │ [Clear Cache]                         │  │
│ │                                       │  │
│ │ Log Files:             12.8 MB        │  │
│ │ [Clear Logs]                          │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Theme & Appearance                          │
│ ┌───────────────────────────────────────┐  │
│ │ Theme Mode                            │  │
│ │ ● System Default                      │  │
│ │ ○ Light                               │  │
│ │ ○ Dark                                │  │
│ │                                       │  │
│ │ Dynamic Colors (M3)     [●─────]     │  │
│ │ Expressive Motion       [●─────]     │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ Advanced                                    │
│ ┌───────────────────────────────────────┐  │
│ │ Debug Mode              [○─────]     │  │
│ │ Export Settings         [Export →]   │  │
│ │ Import Settings         [Import ↓]   │  │
│ │ Reset to Defaults       [Reset]      │  │
│ └───────────────────────────────────────┘  │
│                                             │
│ About                                       │
│ ┌───────────────────────────────────────┐  │
│ │ Version:           1.0.0              │  │
│ │ Build:             20260120           │  │
│ │                                       │  │
│ │ [Documentation]                       │  │
│ │ [Privacy Policy]                      │  │
│ │ [Licenses]                            │  │
│ │ [GitHub Repository]                   │  │
│ └───────────────────────────────────────┘  │
│                                             │
├─────────────────────────────────────────────┤
│  🏠    💾    ⚙️    🎮    ⚙️               │
└─────────────────────────────────────────────┘
```

### Component Details

#### 1. General Settings Section

**Purpose:** Control app behavior and refresh settings

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "General",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Auto-refresh toggle
        SettingsToggleItem(
            title = "Auto-refresh Stats",
            description = "Automatically update system information",
            checked = autoRefreshEnabled,
            onCheckedChange = { autoRefreshEnabled = it }
        )
        
        // Refresh interval dropdown
        SettingsDropdownItem(
            title = "Refresh Interval",
            description = "How often to update stats",
            selectedValue = refreshInterval,
            options = listOf("1 second", "2 seconds", "5 seconds", "10 seconds"),
            onValueChange = { refreshInterval = it }
        )
        
        // Start on boot
        SettingsToggleItem(
            title = "Start on Boot",
            description = "Launch app automatically when device starts",
            checked = startOnBoot,
            onCheckedChange = { startOnBoot = it }
        )
        
        // Run in background
        SettingsToggleItem(
            title = "Run in Background",
            description = "Continue monitoring when app is minimized",
            checked = runInBackground,
            onCheckedChange = { runInBackground = it }
        )
    }
}
```

#### 2. Permissions Section

**Purpose:** View and manage required permissions

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Permissions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Shizuku permission
        PermissionItem(
            title = "Shizuku",
            status = PermissionStatus.GRANTED,
            icon = Icons.Default.Security,
            onClick = { /* Open Shizuku settings */ }
        )
        
        // Root access
        PermissionItem(
            title = "Root Access",
            status = PermissionStatus.DENIED,
            icon = Icons.Default.AdminPanelSettings,
            onClick = { requestRootAccess() }
        )
        
        // Notifications
        PermissionItem(
            title = "Notifications",
            status = PermissionStatus.GRANTED,
            icon = Icons.Default.Notifications,
            onClick = { openNotificationSettings() }
        )
        
        // Storage access
        PermissionItem(
            title = "Storage Access",
            status = PermissionStatus.GRANTED,
            icon = Icons.Default.Storage,
            onClick = { openStorageSettings() }
        )
    }
}
```

**Permission Status Badges:**
```kotlin
@Composable
fun PermissionStatusBadge(status: PermissionStatus) {
    val (text, color) = when (status) {
        PermissionStatus.GRANTED -> "Active" to Color(0xFF4CAF50)
        PermissionStatus.DENIED -> "Grant" to Color(0xFFFF9800)
        PermissionStatus.UNAVAILABLE -> "Unavailable" to Color(0xFF9E9E9E)
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}
```

#### 3. Notifications Section

**Purpose:** Configure notification preferences

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Notifications",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        SettingsToggleItem(
            title = "Enable Notifications",
            description = "Receive app notifications",
            checked = notificationsEnabled,
            onCheckedChange = { notificationsEnabled = it }
        )
        
        AnimatedVisibility(visible = notificationsEnabled) {
            Column {
                SettingsToggleItem(
                    title = "Swap Status Alerts",
                    description = "Notify when swap is created or removed",
                    checked = swapAlerts,
                    onCheckedChange = { swapAlerts = it }
                )
                
                SettingsToggleItem(
                    title = "Memory Warnings",
                    description = "Alert when memory is critically low",
                    checked = memoryWarnings,
                    onCheckedChange = { memoryWarnings = it }
                )
                
                SettingsToggleItem(
                    title = "Performance Alerts",
                    description = "Notify about performance issues",
                    checked = performanceAlerts,
                    onCheckedChange = { performanceAlerts = it }
                )
            }
        }
    }
}
```

#### 4. Data & Storage Section

**Purpose:** Manage app data and storage

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Data & Storage",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Cache size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Cache Size",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${cacheSize} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { clearCache() },
                shape = RoundedCornerShape(100.percent)
            ) {
                Text("Clear Cache")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Log files
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Log Files",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${logSize} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { clearLogs() },
                shape = RoundedCornerShape(100.percent)
            ) {
                Text("Clear Logs")
            }
        }
    }
}
```

#### 5. Theme & Appearance Section

**Purpose:** Customize app appearance

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Theme & Appearance",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Theme mode selection
        Text(
            "Theme Mode",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Column {
            ThemeOption(
                label = "System Default",
                description = "Follow system theme settings",
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { themeMode = ThemeMode.SYSTEM }
            )
            ThemeOption(
                label = "Light",
                description = "Always use light theme",
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { themeMode = ThemeMode.LIGHT }
            )
            ThemeOption(
                label = "Dark",
                description = "Always use dark theme",
                selected = themeMode == ThemeMode.DARK,
                onClick = { themeMode = ThemeMode.DARK }
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Dynamic colors
        SettingsToggleItem(
            title = "Dynamic Colors (Material You)",
            description = "Use wallpaper-based colors",
            checked = dynamicColors,
            onCheckedChange = { dynamicColors = it }
        )
        
        // Expressive motion
        SettingsToggleItem(
            title = "Expressive Motion",
            description = "Use bouncy, expressive animations",
            checked = expressiveMotion,
            onCheckedChange = { expressiveMotion = it }
        )
    }
}
```

#### 6. Advanced Section

**Purpose:** Advanced features and debug options

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            "Advanced",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Debug mode
        SettingsToggleItem(
            title = "Debug Mode",
            description = "Show verbose logging and diagnostics",
            checked = debugMode,
            onCheckedChange = { debugMode = it }
        )
        
        Spacer(Modifier.height(12.dp))
        
        // Export settings
        SettingsClickableItem(
            title = "Export Settings",
            description = "Save settings to file",
            icon = Icons.Default.Upload,
            onClick = { exportSettings() }
        )
        
        // Import settings
        SettingsClickableItem(
            title = "Import Settings",
            description = "Restore settings from file",
            icon = Icons.Default.Download,
            onClick = { importSettings() }
        )
        
        // Reset to defaults
        SettingsClickableItem(
            title = "Reset to Defaults",
            description = "Restore all settings to default values",
            icon = Icons.Default.RestartAlt,
            onClick = { showResetDialog() },
            tint = MaterialTheme.colorScheme.error
        )
    }
}
```

#### 7. About Section

**Purpose:** App information and external links

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) {
        // App icon and name
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Minimal Kernel Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "com.ivarna.mkm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Version info
        InfoRow(label = "Version", value = "1.0.0")
        InfoRow(label = "Build", value = "20260120")
        InfoRow(label = "SDK Version", value = "36")
        InfoRow(label = "NDK Version", value = "29")
        
        Spacer(Modifier.height(20.dp))
        
        // Links
        AboutLinkItem(
            title = "Documentation",
            icon = Icons.Default.Description,
            onClick = { openDocumentation() }
        )
        AboutLinkItem(
            title = "Privacy Policy",
            icon = Icons.Default.PrivacyTip,
            onClick = { openPrivacyPolicy() }
        )
        AboutLinkItem(
            title = "Open Source Licenses",
            icon = Icons.Default.Code,
            onClick = { openLicenses() }
        )
        AboutLinkItem(
            title = "GitHub Repository",
            icon = Icons.Default.OpenInNew,
            onClick = { openGitHub() }
        )
    }
}
```

### Reusable Components

#### Settings Toggle Item
```kotlin
@Composable
fun SettingsToggleItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
```

#### Settings Clickable Item
```kotlin
@Composable
fun SettingsClickableItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tint
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### User Interactions

**Primary Actions:**
1. Toggle switches → Immediately apply setting changes
2. Tap clickable items → Navigate to detailed screens or trigger actions
3. Tap permission items → Open system settings or request permission
4. Clear cache/logs → Show confirmation dialog, then clear

**Confirmation Dialogs:**
```kotlin
// Reset to defaults confirmation
AlertDialog(
    onDismissRequest = { showDialog = false },
    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
    title = { Text("Reset All Settings?") },
    text = { Text("This will restore all settings to their default values. This action cannot be undone.") },
    confirmButton = {
        TextButton(
            onClick = { 
                resetSettings()
                showDialog = false 
            }
        ) {
            Text("Reset", color = MaterialTheme.colorScheme.error)
        }
    },
    dismissButton = {
        TextButton(onClick = { showDialog = false }) {
            Text("Cancel")
        }
    }
)
```

### Swap Management

Swap configuration is integrated into the RAM page, not in Settings.

**Quick Access:**
- Accessible from RAM tab in bottom navigation
- Swap section is prominent within RAM page
- Create/manage swap actions available via FAB

---

## 8. Navigation Patterns & User Flows

### Navigation Hierarchy

```
Home (Dashboard)
├─ RAM Tab → RAM Management
│  └─ Create Swap → Swap Creation Sheet
│     └─ Swap Configuration
│        └─ Success/Error State
│
├─ CPU Tab → CPU Control
│  ├─ Governor Selection
│  └─ Frequency Control
│
├─ GPU Tab → GPU Control
│  ├─ Frequency Presets
│  └─ Performance Monitoring
│
└─ Settings Tab → App Settings
   ├─ General Settings
   ├─ Permissions Management
   ├─ Notifications
   ├─ Data & Storage
   ├─ Theme & Appearance
   ├─ Advanced Options
   ├─ Documentation Links
   └─ About App
```

### Common User Journeys

#### Journey 1: First-Time Setup (Create Swap)
```
1. Launch app → Home screen
2. See "Create Swap" quick action
3. Tap action → Swap creation sheet appears
4. Select size (e.g., 2 GB)
5. Tap "Create" → Loading indicator
6. Success animation → Return to home
7. Swap card now shows active swap
```

**Duration:** ~30 seconds  
**Interactions:** 3 taps, 1 selection

#### Journey 2: Monitor System Performance
```
1. Launch app → Home dashboard
2. View quick stats at a glance
3. Tap RAM card → Navigate to RAM details
4. Scroll through memory breakdown
5. Tap CPU bottom nav → View CPU usage
6. Tap GPU bottom nav → View GPU load
```

**Duration:** ~20 seconds  
**Interactions:** 3 taps, scrolling

#### Journey 3: Adjust CPU Governor
```
1. Navigate to CPU tab (bottom nav)
2. Scroll to Governor Settings card
3. Tap desired governor option
4. Setting applied immediately
5. Visual confirmation of change
```

**Duration:** ~10 seconds  
**Interactions:** 2 taps

#### Journey 4: Change GPU Frequency
```
1. Navigate to GPU tab (bottom nav)
2. Scroll to Frequency Control
3. Select preset OR use custom slider
4. Tap "Apply Settings"
5. Confirmation with updated frequency
```

**Duration:** ~15 seconds  
**Interactions:** 2-3 taps

### Transition Animations

#### Bottom Nav Transitions
```kotlin
// Crossfade between destinations
AnimatedContent(
    targetState = currentDestination,
    transitionSpec = {
        fadeIn(
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium
            )
        ) with fadeOut(
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium
            )
        )
    }
) { destination ->
    when (destination) {
        Destination.Home -> HomeScreen()
        Destination.RAM -> RamScreen()
        Destination.CPU -> CpuScreen()
        Destination.GPU -> GpuScreen()
    }
}
```

#### Card Navigation
```kotlin
// Shared element transition for stat cards
SharedTransitionLayout {
    AnimatedContent(showDetail) {
        if (it) {
            DetailScreen(
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "card"),
                    animatedVisibilityScope = this
                )
            )
        } else {
            SummaryCard(
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = "card"),
                    animatedVisibilityScope = this
                )
            )
        }
    }
}
```

---

## 9. Component Specifications

### Bottom Navigation Bar

**Dimensions:**
- Height: 80dp
- Item width: Flexible (equal distribution)
- Icon size: 24dp
- Label: Body medium typography
- Indicator: 64dp width, 32dp height, 16dp corner radius

**Spacing:**
- Content padding: 12dp horizontal, 16dp vertical
- Icon-to-label gap: 4dp

**Elevation:**
- Default: 3dp tonal elevation
- No shadow (uses tonal elevation only)

### Cards

**Corner Radii:**
- Hero cards: 32dp (extra large)
- Content cards: 24dp (large)
- Stat cards: 20dp (large)
- Core cards: 16dp (medium)

**Elevation:**
- Hero cards: 4dp
- Content cards: 2dp
- Interactive cards: 1dp default, 4dp on hover/press

**Padding:**
- Hero cards: 24dp
- Content cards: 20dp
- Stat cards: 16dp
- Compact cards: 12dp

### Buttons

**Shapes:**
- Primary/Secondary: Fully rounded (100%)
- Outlined: Fully rounded (100%)
- Text: No background shape

**Sizes:**
- Height: 40dp (standard), 48dp (large)
- Min width: 64dp
- Horizontal padding: 24dp
- Icon-text gap: 8dp

### Sliders

**Dimensions:**
- Track height: 4dp
- Thumb size: 20dp
- Active track height: 4dp

**Colors:**
- Thumb: Primary color
- Active track: Primary color
- Inactive track: Surface variant

---

## 10. Responsive Behavior

### Phone (Width < 600dp)

**Bottom Navigation:**
- Visible and fixed at bottom
- 4 items with icons + labels
- Expands to accommodate labels

**Layout:**
- Single column
- Full-width cards
- 16dp side margins
- 16dp vertical spacing

**FAB:**
- Bottom-right corner
- 16dp margin from edges
- Collapses to icon-only on scroll

### Tablet (600dp ≤ Width < 840dp)

**Navigation:**
- Consider switching to Navigation Rail (left side)
- Or keep bottom nav with wider spacing

**Layout:**
- Two-column grid for stat cards
- Larger cards with more spacing (24dp)
- Wider maximum width (720dp) centered

**FAB:**
- Larger size (extended stays expanded)
- More prominent positioning

### Foldable/Desktop (Width ≥ 840dp)

**Navigation:**
- **Navigation Rail** (vertical, left side)
- Icons + labels always visible
- 72dp width (standard)

**Layout:**
- Three-column grid for stat cards
- Master-detail pattern for settings
- Maximum width 1200dp, centered
- Extra large spacing (32dp)

**FAB:**
- Can be positioned in different locations
- Or use toolbar buttons instead

---

## Accessibility Requirements

### Navigation Bar

```
✓ Each item has unique contentDescription
✓ Selected state announced ("Selected" / "Not selected")
✓ Minimum 48dp touch target (80dp height exceeds this)
✓ Clear focus indicators for keyboard navigation
✓ Role.Tab assigned for screen reader context
✓ Labels provide context without requiring icons
```

### Content

```
✓ All interactive elements ≥ 48dp touch target
✓ Color contrast ≥ 4.5:1 for text, ≥ 3:1 for UI
✓ Status colors supplemented with text/icons
✓ Slider values announced as they change
✓ Loading states announced to screen readers
✓ Error messages are clear and actionable
```

### Motion

```
✓ Respect prefers-reduced-motion system setting
✓ Critical info never conveyed by animation alone
✓ Animations can be paused/stopped
✓ No flashing content above 3 Hz
```

---

## Implementation Checklist

### Phase 1: Navigation Structure
```
□ Implement bottom navigation bar with 4 tabs
□ Set up navigation destinations and routing
□ Add navigation animations (crossfade)
□ Implement selected state indicators
□ Test navigation accessibility
```

### Phase 2: Home/Dashboard
```
□ Create system overview hero card
□ Implement quick stats grid (4 cards)
□ Add quick actions list
□ Implement pull-to-refresh
□ Add real-time stat updates
```

### Phase 3: RAM Settings
```
□ Build memory overview hero card
□ Create swap configuration card (active/inactive states)
□ Implement memory details breakdown
□ Add create swap FAB
□ Build swap creation bottom sheet
```

### Phase 4: CPU Settings
```
□ Implement CPU overview hero card
□ Create CPU cores grid with per-core stats
□ Build governor selection card
□ Add frequency control sliders
□ Implement real-time monitoring
```

### Phase 5: GPU Settings
```
□ Build GPU overview hero card
□ Create GPU information card
□ Implement frequency control with presets
□ Add performance stats display
□ Real-time GPU monitoring
```

### Phase 6: Polish & Testing
```
□ Add all hero moment animations
□ Implement loading states
□ Add error handling and states
□ Conduct accessibility audit
□ Test on multiple screen sizes
□ Performance optimization
```

---

## Design Resources

**Figma Files:**
- Navigation components mockup
- All screen layouts
- Component library
- Interactive prototype

**Design Tokens:**
```kotlin
// In theme/Spacing.kt
object MkmSpacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
}

// In theme/Shape.kt
val MkmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
```

---

**Document Version:** 1.0  
**Last Updated:** January 20, 2026  
**Status:** Active UX Specification

---

## Next Steps

1. Review navigation structure with development team
2. Create interactive Figma prototype
3. Implement bottom navigation in Jetpack Compose
4. Build out each screen incrementally
5. Conduct usability testing with beta users
6. Iterate based on feedback

For questions or updates, refer to the main [UI Guidelines](ui-guidelines.md) document.
