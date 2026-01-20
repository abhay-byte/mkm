# UI Design Guidelines for Minimal Kernel Manager (MKM)

## Overview

This document outlines the UI/UX design guidelines for Minimal Kernel Manager, following **Material Design 3 (M3) Expressive** principles. These guidelines ensure a consistent, engaging, and user-friendly interface across the application.

**Design System:** Material Design 3 Expressive  
**Platform:** Android (Jetpack Compose)  
**Target SDK:** 36  
**Package:** `com.ivarna.mkm`

---

## Table of Contents

1. [Material 3 Expressive Philosophy](#1-material-3-expressive-philosophy)
2. [Core Design Principles](#2-core-design-principles)
3. [Shape System](#3-shape-system)
4. [Motion & Animation](#4-motion--animation)
5. [Typography](#5-typography)
6. [Color System](#6-color-system)
7. [Component Guidelines](#7-component-guidelines)
8. [Layout & Spacing](#8-layout--spacing)
9. [Accessibility](#9-accessibility)
10. [Implementation Resources](#10-implementation-resources)

---

## 1. Material 3 Expressive Philosophy

### What is M3 Expressive?

Material 3 Expressive is an evolution of Material Design that creates emotionally impactful user experiences through:

- **Enhanced hierarchy** - Making interfaces more useful and easier to navigate
- **Improved visual style** - Creating personal connections with users
- **Better usability** - Users spot key UI elements up to 4× faster
- **Emotional engagement** - Interfaces that feel playful, energetic, creative, and friendly

### Research-Backed Benefits

- Preferred by users of all ages
- Higher scores on creativity and friendliness
- Increased likelihood of product adoption
- Significantly improved element discoverability

### Key Philosophy for MKM

As a utility app dealing with system-level operations (swap management), MKM should:

- Use **expressive design** for hero moments (swap creation, status displays)
- Apply **standard motion** for utilitarian operations (settings, monitoring)
- Balance **visual interest** with **functional clarity**
- Create **confidence** through clear visual hierarchy and feedback

---

## 2. Core Design Principles

### The 7 Expressive Tactics

Apply these tactics strategically throughout MKM:

#### 1. **Use a Variety of Shapes**

**Application in MKM:**
- Use **rounded shapes** for primary actions (Create Swap button)
- Use **square/rectangular shapes** for data containers (memory stats, swap info cards)
- Break from surrounding shape style to draw attention to critical actions
- Create visual tension by mixing round FABs with rectangular cards

**Guidelines:**
```
✓ DO: Mix round and square shapes for visual contrast
✓ DO: Use distinct shapes to emphasize primary actions
✗ DON'T: Make smaller shapes for essential actions
✗ DON'T: Use too many different shapes causing visual clutter
```

#### 2. **Apply Rich and Nuanced Colors**

**Application in MKM:**
- Use **primary colors** for main actions (create, enable/disable swap)
- Use **secondary colors** for monitoring displays and progress indicators
- Use **tertiary colors** for settings and auxiliary features
- Apply **surface tones** to create depth and grouping

**Guidelines:**
```
✓ DO: Use color contrast to emphasize main takeaways
✓ DO: Apply color-coded status indicators (green=active, red=error)
✗ DON'T: Use similar colors for different priority elements
✗ DON'T: Over-rely on color alone for critical information
```

#### 3. **Guide Attention with Typography**

**Application in MKM:**
- Use **emphasized display styles** for memory statistics
- Use **headline styles** for section titles (Current Swap, Create New)
- Use **body text** for descriptions and status messages
- Apply **label text** for input fields and buttons

**Guidelines:**
```
✓ DO: Use larger, heavier weights for critical numbers (RAM usage, swap size)
✓ DO: Create editorial moments with emphasized typography
✗ DON'T: Use too many type weights in one screen
✗ DON'T: Make warning text less prominent than regular text
```

#### 4. **Contain Content for Emphasis**

**Application in MKM:**
- Group **swap information** in cards with elevated surfaces
- Separate **memory stats** from **swap controls**
- Use **ample spacing** around primary actions
- Apply **containers** for settings groupings

**Guidelines:**
```
✓ DO: Group similar content logically (all memory info together)
✓ DO: Give most important content visual prominence
✗ DON'T: Let information blend together without clear grouping
✗ DON'T: Use equal spacing for all elements
```

#### 5. **Add Fluid and Natural Motion**

**Application in MKM:**
- Use **expressive motion** for swap creation confirmation
- Apply **shape morph** for progress indicators (square → circle during loading)
- Use **standard motion** for routine navigation
- Add **micro-animations** for toggle switches and status changes

**Guidelines:**
```
✓ DO: Use motion to indicate progress and state changes
✓ DO: Apply springs for natural, predictable animations
✗ DON'T: Animate every single interaction
✗ DON'T: Use slow animations for frequent actions
```

#### 6. **Leverage Component Flexibility**

**Application in MKM:**
- Adapt **app bars** based on scroll state (collapse on scroll down)
- Use **bottom sheets** for swap size selection
- Apply **FAB menus** for quick actions (refresh, settings)
- Implement **adaptive layouts** for tablets and foldables

**Guidelines:**
```
✓ DO: Adapt UI to user context and device size
✓ DO: Use appropriate component variants for each situation
✗ DON'T: Force desktop patterns on mobile
✗ DON'T: Use the same layout for all screen sizes
```

#### 7. **Combine Tactics to Create Hero Moments**

**Application in MKM:**

**Hero Moment 1: Swap Creation Success**
- Large, emphasized typography for "Swap Created!"
- Shape morph animation (button → checkmark)
- Vibrant color transition (primary → success green)
- Fluid spring animation with slight overshoot

**Hero Moment 2: Memory Status Dashboard**
- Editorial typography for large memory numbers
- Rich color gradients for usage bars
- Animated charts with expressive motion
- Prominent cards with varied shapes

**Guidelines:**
```
✓ DO: Identify 1-2 key interactions for hero treatment
✓ DO: Combine multiple tactics for emotional impact
✗ DON'T: Make every screen a hero moment
✗ DON'T: Sacrifice clarity for visual flair
```

---

## 3. Shape System

### Shape Library

Material 3 Expressive provides **35 iconic shapes** for use throughout the UI. For MKM, focus on:

#### Essential Component Shapes

- **Buttons:** Fully rounded (pill shape) or large rounded corners (20dp)
- **Cards:** Extra large corners (32dp) for main content cards
- **FABs:** Fully circular with morph capability
- **Chips:** Fully rounded capsule shape
- **Input fields:** Medium rounded (8dp) for functional clarity

#### Corner Radius Scale

```kotlin
// Updated M3 Expressive corner radii
val CornerRadii = object {
    val None = 0.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 20.dp          // Increased from 16dp
    val ExtraLarge = 32.dp     // Increased from 28dp
    val ExtraExtraLarge = 48.dp // New
    val Full = 50.percent      // Fully rounded
}
```

#### Shape Usage Guidelines

**Primary Actions (Create Swap, Enable/Disable):**
```kotlin
shape = RoundedCornerShape(100.percent) // Fully rounded pill buttons
```

**Content Cards (Memory Stats, Swap Info):**
```kotlin
shape = RoundedCornerShape(32.dp) // Extra large for emphasis
```

**Input Fields (Size Selection):**
```kotlin
shape = RoundedCornerShape(12.dp) // Medium for functionality
```

### Shape Morphing

Use built-in shape morphing for:

- **Loading states:** Square → Circle during progress
- **Button states:** Normal → Expanded on press
- **Status indicators:** Circle → Checkmark on success

**Implementation:**
```kotlin
val targetShape by animateShapeAsState(
    targetValue = if (isLoading) CircleShape else RoundedCornerShape(16.dp),
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
)
```

### Creating Visual Tension

MKM should use **shape tension** strategically:

```
Primary Action (Round) + Info Cards (Rectangular) = Visual Hierarchy
```

- Use **rounded shapes** for interactive elements
- Use **rectangular shapes** with rounded corners for data display
- Mix **circular FAB** with **rectangular bottom sheet** for contrast

### Shape Best Practices

```
✓ DO: Echo typography roundness in shape choices
✓ DO: Use shape morph to communicate progress
✓ DO: Create tension with contrasting shapes
✓ DO: Use abstract shapes for decorative elements only

✗ DON'T: Assign specific meanings to individual shapes
✗ DON'T: Compromise clarity with excessive decoration
✗ DON'T: Use tiny shapes for important actions
✗ DON'T: Apply shapes without clear purpose
```

---

## 4. Motion & Animation

### Motion Physics System

MKM uses the **M3 Motion Physics System** based on **springs** rather than easing curves. This creates more natural, predictable, and interruptible animations.

### Motion Schemes

#### Expressive Motion Scheme
Use for:
- Swap creation success animation
- Hero moments and key interactions
- Feature discovery moments
- Celebratory confirmations

**Characteristics:** Bouncy, overshoots final values, energetic

#### Standard Motion Scheme
Use for:
- Navigation transitions
- Settings changes
- List scrolling
- Routine operations

**Characteristics:** Functional, minimal bounce, efficient

### Spring Tokens

#### Token Structure
```
md.sys.motion.spring.[speed].[style]
```

**Speeds:**
- `fast` - Small components (switches, buttons)
- `default` - Medium components (bottom sheets, nav rail)
- `slow` - Large/full-screen animations

**Styles:**
- `spatial` - Movement, position, rotation, size
- `effects` - Color, opacity changes

### Motion Application in MKM

#### 1. Button Interactions
```kotlin
// Fast spatial for button press
Button(
    modifier = Modifier.animateContentSize(
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioMediumBouncy
        )
    )
)
```

#### 2. Swap Creation Animation
```kotlin
// Default spatial for card appearance
Card(
    modifier = Modifier.animateEnterExit(
        enter = fadeIn(animationSpec = spring()) + 
                scaleIn(animationSpec = spring()),
        exit = fadeOut() + scaleOut()
    )
)
```

#### 3. Status Changes
```kotlin
// Fast effects for color transitions
val backgroundColor by animateColorAsState(
    targetValue = if (isActive) Color.Green else Color.Gray,
    animationSpec = spring(stiffness = Spring.StiffnessHigh)
)
```

#### 4. Progress Indicators
```kotlin
// Shape morph for loading
val cornerRadius by animateFloatAsState(
    targetValue = if (isLoading) 50f else 16f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
)
```

### Motion Best Practices

```
✓ DO: Use expressive motion for hero moments
✓ DO: Apply standard motion for utilitarian features
✓ DO: Make animations interruptible (spring-based)
✓ DO: Use consistent spring tokens throughout app

✗ DON'T: Animate everything - be selective
✗ DON'T: Use slow animations for frequent actions
✗ DON'T: Mix easing curves with physics springs
✗ DON'T: Create custom springs unless necessary
```

### Spring Token Reference

| Action | Token | Usage in MKM |
|--------|-------|--------------|
| Button press | `fast.spatial` | Toggle swap, size buttons |
| Button color | `fast.effects` | State color changes |
| Card appearance | `default.spatial` | Memory stat cards |
| Background color | `default.effects` | Screen background changes |
| Full screen | `slow.spatial` | Settings screen transition |
| Screen fade | `slow.effects` | Full-screen content refresh |

---

## 5. Typography

### M3 Expressive Typography

Use **emphasized typography** to create hierarchy and draw attention to important information.

### Type Scale for MKM

#### Display Styles (Large Numbers & Stats)
```kotlin
// Memory usage numbers
displayLarge = TextStyle(
    fontSize = 57.sp,
    lineHeight = 64.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.25).sp
)

displayMedium = TextStyle(
    fontSize = 45.sp,
    lineHeight = 52.sp,
    fontWeight = FontWeight.Bold
)
```

#### Headline Styles (Section Titles)
```kotlin
// "Current Swap Status", "Create Swap"
headlineLarge = TextStyle(
    fontSize = 32.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.Bold
)

headlineMedium = TextStyle(
    fontSize = 28.sp,
    lineHeight = 36.sp,
    fontWeight = FontWeight.SemiBold
)
```

#### Body Styles (Descriptions)
```kotlin
// Status messages, descriptions
bodyLarge = TextStyle(
    fontSize = 16.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.5.sp
)

bodyMedium = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.25.sp
)
```

#### Label Styles (UI Elements)
```kotlin
// Button text, input labels
labelLarge = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.1.sp
)

labelMedium = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.5.sp
)
```

### Emphasized Typography

Use **variable fonts** (Google Sans Flex) or **bold weights** to emphasize:

- **Memory statistics** (RAM used, swap size)
- **Status indicators** (Active, Inactive, Error)
- **Primary action labels** (Create Swap, Enable)
- **Warning messages**

### Typography Application in MKM

#### Memory Dashboard
```kotlin
Column {
    // Emphasized display for current RAM
    Text(
        text = "3.2 GB",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Black // Extra emphasis
    )
    
    // Headline for label
    Text(
        text = "RAM Used",
        style = MaterialTheme.typography.headlineSmall
    )
}
```

#### Swap Info Card
```kotlin
Card {
    // Medium headline for swap size
    Text(
        text = "2.0 GB",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    
    // Body text for details
    Text(
        text = "Persistent swap file",
        style = MaterialTheme.typography.bodyMedium
    )
}
```

### Typography Best Practices

```
✓ DO: Use display styles for large, important numbers
✓ DO: Apply bold weights to critical information
✓ DO: Create clear hierarchy with size and weight
✓ DO: Use emphasized text for primary actions

✗ DON'T: Mix too many weights in one section
✗ DON'T: Use small text for critical warnings
✗ DON'T: Apply decorative fonts to body text
✗ DON'T: Sacrifice readability for style
```

---

## 6. Color System

### M3 Dynamic Color

MKM should support **dynamic color** (Material You) while providing sensible defaults.

### Color Roles

#### Primary Colors
- **Primary:** Main brand color, primary actions (Create Swap)
- **OnPrimary:** Text/icons on primary color
- **PrimaryContainer:** Subtle primary backgrounds
- **OnPrimaryContainer:** Text on primary container

#### Secondary Colors
- **Secondary:** Secondary actions, monitoring features
- **SecondaryContainer:** Swap status displays

#### Tertiary Colors
- **Tertiary:** Settings, auxiliary features
- **TertiaryContainer:** Additional information cards

### Semantic Colors for MKM

#### Status Colors
```kotlin
object MkmColors {
    // Swap status indicators
    val SwapActive = Color(0xFF4CAF50)      // Green
    val SwapInactive = Color(0xFF9E9E9E)    // Gray
    val SwapError = Color(0xFFF44336)       // Red
    val SwapWarning = Color(0xFFFF9800)     // Orange
    
    // Memory indicators
    val MemoryLow = Color(0xFF4CAF50)       // Green (plenty)
    val MemoryMedium = Color(0xFFFF9800)    // Orange (caution)
    val MemoryHigh = Color(0xFFF44336)      // Red (critical)
}
```

#### Surface Tones
```kotlin
// Create depth and hierarchy
surface = Color(0xFFFFFBFE)
surfaceVariant = Color(0xFFE7E0EC)
surfaceContainerLowest = Color(0xFFFFFFFF)
surfaceContainerLow = Color(0xFFF7F2FA)
surfaceContainer = Color(0xFFF3EDF7)
surfaceContainerHigh = Color(0xFFECE6F0)
surfaceContainerHighest = Color(0xFFE6E0E9)
```

### Color Application Guidelines

#### Memory Status Display
```kotlin
// Use color to indicate memory pressure
val memoryColor = when (memoryUsagePercent) {
    in 0..60 -> MkmColors.MemoryLow
    in 61..85 -> MkmColors.MemoryMedium
    else -> MkmColors.MemoryHigh
}

LinearProgressIndicator(
    progress = memoryUsagePercent / 100f,
    color = memoryColor
)
```

#### Swap Status Card
```kotlin
Card(
    colors = CardDefaults.cardColors(
        containerColor = when (swapStatus) {
            SwapStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
            SwapStatus.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
            SwapStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        }
    )
)
```

#### Button Colors
```kotlin
// Primary action - most prominent
Button(
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
    )
) { Text("Create Swap") }

// Secondary action - less prominent
FilledTonalButton(
    colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    )
) { Text("Settings") }
```

### Color Best Practices

```
✓ DO: Use color contrast to create hierarchy
✓ DO: Apply semantic colors for status (green=good, red=error)
✓ DO: Support dynamic color (Material You)
✓ DO: Test color combinations for accessibility

✗ DON'T: Rely solely on color to convey critical info
✗ DON'T: Use similar colors for different priorities
✗ DON'T: Ignore dark mode color requirements
✗ DON'T: Use pure white or pure black for surfaces
```

### Accessibility Note

All color combinations must meet **WCAG 2.1 Level AA** contrast requirements:
- **Normal text:** 4.5:1 minimum contrast ratio
- **Large text:** 3:1 minimum contrast ratio
- **UI components:** 3:1 minimum contrast ratio

---

## 7. Component Guidelines

### Primary Components for MKM

#### 1. App Bars

**Top App Bar:**
```kotlin
// Use toolbar with expressive configuration
MediumTopAppBar(
    title = { 
        Text(
            "Memory Manager",
            style = MaterialTheme.typography.headlineMedium
        )
    },
    colors = TopAppBarDefaults.mediumTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
)
```

**Applications:**
- Main screen: Medium app bar with scroll behavior
- Settings: Small app bar with back navigation
- About: Center-aligned small app bar

#### 2. Floating Action Button (FAB)

**Primary FAB:**
```kotlin
// Use for main action (Create Swap)
ExtendedFloatingActionButton(
    onClick = { /* Create swap */ },
    icon = { Icon(Icons.Default.Add, null) },
    text = { Text("Create Swap") },
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    shape = RoundedCornerShape(16.dp)
)
```

**FAB Menu (New in M3 Expressive):**
```kotlin
// Use for multiple quick actions
FabMenu(
    items = listOf(
        FabMenuItem(icon = Icons.Default.Refresh, label = "Refresh"),
        FabMenuItem(icon = Icons.Default.Settings, label = "Settings")
    )
)
```

#### 3. Cards

**Swap Information Card:**
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(32.dp), // Extra large corners
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Current Swap", style = MaterialTheme.typography.headlineSmall)
        Text("2.0 GB", style = MaterialTheme.typography.displayMedium)
        Text("Active since boot", style = MaterialTheme.typography.bodyMedium)
    }
}
```

#### 4. Buttons

**Button Hierarchy:**

```kotlin
// Primary action - filled button
Button(
    onClick = { /* Create */ },
    shape = RoundedCornerShape(100.percent)
) {
    Icon(Icons.Default.Add, null)
    Spacer(Modifier.width(8.dp))
    Text("Create")
}

// Secondary action - filled tonal button
FilledTonalButton(
    onClick = { /* Enable */ },
    shape = RoundedCornerShape(100.percent)
) {
    Text("Enable")
}

// Tertiary action - text button
TextButton(onClick = { /* Cancel */ }) {
    Text("Cancel")
}
```

**Button Groups (New in M3 Expressive):**
```kotlin
// For size selection
ButtonGroup(
    items = listOf("512 MB", "1 GB", "2 GB", "4 GB"),
    selectedIndex = selectedSize,
    onSelectionChange = { selectedSize = it }
)
```

#### 5. Progress Indicators (M3 Expressive)

Progress indicators in MKM follow the latest Material 3 Expressive guidelines, featuring enhanced visibility and personality.

**Key Expressive Features:**
- **Wavy Shape:** Active tracks can use a wavy/squiggly pattern to increase expressiveness.
- **Variable Track Height:** Use **6dp (Thick)** for a prominent and modern look.
- **End Stop Indicator:** A small dot at the end of the active track to improve accessibility and progress perception.
- **Dynamic Color:** Fully compatible with primary and secondary color schemes.

**Wavy Linear Progress:**
```kotlin
// Use for memory usage or active operations
SquigglyLinearProgressIndicator(
    progress = { memoryUsage / 100f },
    modifier = Modifier.fillMaxWidth().height(6.dp), // Use 6dp for a thick, expressive look
    color = MaterialTheme.colorScheme.primary,
    trackColor = MaterialTheme.colorScheme.surfaceVariant,
    amplitude = 1.5.dp,
    wavelength = 24.dp // Longer wavelength for low-frequency waves
)
```

**Circular Progress:**
```kotlin
// Use for centered loading states
CircularProgressIndicator(
    strokeWidth = 4.dp, // Thicker style
    trackColor = MaterialTheme.colorScheme.surfaceVariant,
    strokeCap = StrokeCap.Round
)
```

**Loading Indicator (M3 Expressive):**
```kotlin
// Use for major swap creation progress
LoadingIndicator(
    modifier = Modifier.size(48.dp),
    color = MaterialTheme.colorScheme.primary,
    trackColor = MaterialTheme.colorScheme.surfaceVariant
)
```

#### 6. Sliders

**Swap Size Slider:**
```kotlin
// Use for custom size selection
Slider(
    value = swapSize,
    onValueChange = { swapSize = it },
    valueRange = 256f..8192f,
    steps = 15,
    colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary
    )
)
```

#### 7. Bottom Sheets

**Swap Configuration Sheet:**
```kotlin
ModalBottomSheet(
    onDismissRequest = { showSheet = false },
    sheetState = rememberModalBottomSheetState(),
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Select Swap Size", style = MaterialTheme.typography.headlineMedium)
        // Size options
    }
}
```

#### 8. Switches & Toggles

**Enable/Disable Swap:**
```kotlin
Switch(
    checked = isSwapEnabled,
    onCheckedChange = { isSwapEnabled = it },
    colors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.primary,
        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
    )
)
```

---

## 8. Layout & Spacing

### Spacing System

Use consistent spacing throughout MKM:

```kotlin
object Spacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    val Huge = 48.dp
}
```

### Layout Guidelines

#### Main Screen Layout
```
┌─────────────────────────────────┐
│ Top App Bar                     │ ← Medium, scroll behavior
├─────────────────────────────────┤
│ [Spacing.Large padding]         │
│ ┌─────────────────────────────┐ │
│ │ Memory Status Card          │ │ ← Extra large corners
│ │ [Display typography]        │ │
│ └─────────────────────────────┘ │
│ [Spacing.Medium]                │
│ ┌─────────────────────────────┐ │
│ │ Swap Status Card            │ │
│ └─────────────────────────────┘ │
│ [Spacing.Medium]                │
│ ┌─────────────────────────────┐ │
│ │ Quick Actions               │ │
│ └─────────────────────────────┘ │
│                                 │
│         [Extended FAB] ────────►│ ← Bottom right
└─────────────────────────────────┘
```

#### Card Internal Padding
```kotlin
Card {
    Column(
        modifier = Modifier.padding(24.dp), // Large padding for spaciousness
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Content
    }
}
```

### Responsive Layout

#### Phone (Compact)
- Single column layout
- Full-width cards
- Bottom FAB
- Medium padding

#### Tablet (Medium/Expanded)
- Two-column layout for cards
- Navigation rail instead of bottom navigation
- Larger padding (32dp)
- Adapted component sizes

#### Foldable (Unfolded)
- Three-column layout
- Master-detail pattern
- Persistent navigation rail
- Extra large spacing

---

## 9. Accessibility

### Core Accessibility Requirements

#### 1. Content Descriptions
```kotlin
Icon(
    imageVector = Icons.Default.Memory,
    contentDescription = "Memory icon" // Always provide
)

Button(onClick = { }) {
    Text("Create")
    // Label is inherently accessible
}
```

#### 2. Touch Targets
- Minimum touch target: **48dp × 48dp**
- Recommended: **56dp × 56dp** for primary actions
- Spacing between targets: **8dp minimum**

```kotlin
IconButton(
    onClick = { },
    modifier = Modifier.size(48.dp) // Ensures minimum touch target
) {
    Icon(Icons.Default.Settings, "Settings")
}
```

#### 3. Contrast Ratios

Test all color combinations:
```kotlin
// Use Material Theme color roles - they're designed for accessibility
containerColor = MaterialTheme.colorScheme.primaryContainer
contentColor = MaterialTheme.colorScheme.onPrimaryContainer
```

#### 4. Semantic Grouping
```kotlin
Column(
    modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Memory status: 3.2 GB used of 4 GB total"
    }
) {
    Text("3.2 GB")
    Text("/ 4.0 GB")
}
```

#### 5. Dynamic Type Support
```kotlin
// Always use MaterialTheme.typography
Text(
    text = "Swap Size",
    style = MaterialTheme.typography.bodyLarge
    // Will scale with user's font size settings
)
```

#### 6. Screen Reader Optimization
```kotlin
Switch(
    checked = isEnabled,
    onCheckedChange = { },
    modifier = Modifier.semantics {
        stateDescription = if (isEnabled) "Swap enabled" else "Swap disabled"
    }
)
```

### Accessibility Checklist

```
□ All interactive elements have 48dp minimum touch targets
□ All icons have contentDescription
□ Color contrast meets WCAG AA (4.5:1 for text, 3:1 for UI)
□ Text scales with user font size preferences
□ Critical information doesn't rely solely on color
□ Screen reader announces all important state changes
□ Focus indicators are visible
□ Animations can be disabled (respect prefers-reduced-motion)
```

---

## 10. Implementation Resources

### Jetpack Compose Dependencies

```kotlin
// build.gradle.kts (app module)
dependencies {
    // Material 3
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.0")
    
    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    
    // Compose Animation
    implementation("androidx.compose.animation:animation:1.7.0")
    
    // Compose Foundation
    implementation("androidx.compose.foundation:foundation:1.7.0")
}
```

### Design Resources

- **Figma Design Kit:** [Material 3 Design Kit](https://www.figma.com/community/file/1035203688168086460)
- **Shape Library:** [Material Shape Library](https://www.figma.com/community/file/1035203688168086460)
- **Component Gallery:** [M3 Component Gallery](https://m3.material.io/components)

### Code Resources

- **Jetpack Compose Documentation:** [developer.android.com/jetpack/compose](https://developer.android.com/jetpack/compose)
- **Material 3 Compose:** [developer.android.com/jetpack/compose/designsystems/material3](https://developer.android.com/jetpack/compose/designsystems/material3)
- **GitHub Samples:** [github.com/material-components](https://github.com/material-components)

### Official Guidelines

- **M3 Guidelines:** [m3.material.io](https://m3.material.io/)
- **M3 Expressive Blog:** [Building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- **Shape Guidelines:** [m3.material.io/styles/shape](https://m3.material.io/styles/shape)
- **Motion Guidelines:** [m3.material.io/styles/motion](https://m3.material.io/styles/motion)
- **Typography:** [m3.material.io/styles/typography](https://m3.material.io/styles/typography)
- **Color System:** [m3.material.io/styles/color](https://m3.material.io/styles/color)

### Testing Tools

- **Accessibility Scanner:** [Google Play Store](https://play.google.com/store/apps/details?id=com.google.android.apps.accessibility.auditor)
- **TalkBack:** Enable in device accessibility settings
- **Layout Inspector:** Android Studio built-in tool
- **Compose Preview:** For rapid UI iteration

---

## Quick Reference Checklist

When designing any screen in MKM, ensure:

```
✓ Uses Material 3 Expressive components
✓ Applies appropriate shape tokens (corners 20dp, 32dp, or full)
✓ Uses spring-based motion physics
✓ Implements emphasized typography for important info
✓ Applies semantic colors with proper contrast
✓ Groups related content in containers
✓ Provides 1-2 hero moments per flow
✓ Meets all accessibility requirements
✓ Adapts to different screen sizes
✓ Supports dynamic color (Material You)
```

---

**Document Version:** 1.0  
**Last Updated:** January 20, 2026  
**Based on:** Material Design 3 Expressive (May 2025 Update)  
**Status:** Active Guidelines

---

## Document History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | January 20, 2026 | Initial guidelines based on M3 Expressive | MKM Team |

---

**Next Steps:**
1. Review these guidelines with the development team
2. Create Figma mockups following these principles
3. Implement Material 3 theme in Jetpack Compose
4. Build component library based on these specs
5. Conduct accessibility audit on initial implementation

For questions or clarifications, refer to the official Material Design 3 documentation at [m3.material.io](https://m3.material.io/).
