# Navigation Rails - Behavior

This document describes how navigation rails behave in different scenarios.

## Scrolling Behavior

### Vertical Scrolling

Navigation rail destinations should remain visible and fixed when content scrolls vertically.

**Guidelines:**
- Destinations stay in place
- Content scrolls beneath the rail
- Navigation always accessible
- No confusion about current location

**Implementation:**
```kotlin
// Rail remains fixed while content scrolls
Scaffold(
    navigationRail = { AppNavigationRail() }
) { paddingValues ->
    LazyColumn(
        modifier = Modifier.padding(paddingValues)
    ) {
        // Scrollable content
    }
}
```

### Horizontal Scrolling

When layout scrolls horizontally, the rail has two options:

#### Option 1: Fixed Rail
- Rail remains visible and fixed
- Content scrolls horizontally beneath it
- Use divider or elevation for visual distinction
- Rail elevation at level 1 creates depth

#### Option 2: Scrolling Rail
- Rail scrolls off-screen with content
- Less common pattern
- Only for specific use cases

**Visual Distinction Methods:**
1. **Divider**: Add vertical divider between rail and content
2. **Elevation**: Elevate rail to level 1 (casts shadow)
3. **Color Fill**: Use container color for rail background

## Selection Behavior

### Tap/Click Interaction

When a destination is tapped or clicked:

1. **Transition**: Uses top-level transition pattern
2. **Icon Change**: Icon becomes filled
3. **Active Indicator**: Expands from center of icon
4. **Color Change**: Updates to active color
5. **Content Update**: Destination screen loads

### Transition Pattern

```kotlin
NavigationRailItem(
    selected = selectedItem == index,
    onClick = {
        // Top-level transition
        selectedItem = index
        navController.navigate(route)
    },
    icon = {
        Icon(
            if (selectedItem == index) filledIcon else outlinedIcon,
            contentDescription = label
        )
    }
)
```

### Active State Rules
- Only one destination can be active at a time
- Active indicator appears only on current page
- Previous selection deactivates automatically
- State persists across navigation

## Expansion and Collapse

### Menu Button Interaction

**Collapse to Expand:**
1. User taps menu icon
2. Rail animates to expanded width
3. Labels become more prominent
4. FAB extends (if present)
5. Secondary destinations appear (if available)
6. Menu icon changes to collapse icon

**Expand to Collapse:**
1. User taps collapse icon
2. Rail animates to collapsed width
3. Labels may reduce (based on `alwaysShowLabel`)
4. Extended FAB becomes regular FAB
5. Secondary destinations hide
6. Icon changes back to menu icon

### Automatic Content Adjustment

Page contents should automatically adjust when rail expands/collapses:
- Content area resizes
- Layout reflows
- No content cut off
- Smooth, coordinated animation

### Animation Guidelines
- Duration: 250-300ms
- Easing: Standard deceleration curve
- Coordinate all elements
- Maintain 60fps

## Back Navigation

### Predictive Back Gesture (Android)

The predictive back gesture allows users to preview the previous screen:

**Behavior:**
1. User swipes left or right on screen edge
2. Previous screen revealed in preview
3. Current screen slides away partially
4. Release completes navigation
5. Cancel returns to current screen

**Applies to:**
- Modal expanded navigation rail only
- Not applicable to collapsed rail
- Android-specific interaction

**Visual Feedback:**
- Rail pops off edge of window during gesture
- Previous content previews behind
- Smooth, responsive animation
- Clear feedback of action

### Back Button Behavior

Standard back button behavior:
- Returns to previous screen in navigation stack
- Doesn't close collapsed rail
- Can close modal expanded rail
- Follows platform conventions

## Badge Updates

### Dynamic Badge Behavior

Badges update in real-time to reflect changes:

**Appearance:**
- Animate in when count changes from 0 to 1+
- Update number smoothly
- Animate out when count returns to 0

**Maximum Values:**
- Numbers typically max at 99
- Show "99+" for values ≥ 100
- Consider context for different maxes

**Animation:**
```kotlin
AnimatedContent(
    targetState = badgeCount,
    transitionSpec = { slideInVertically() + fadeIn() togetherWith
                       slideOutVertically() + fadeOut() }
) { count ->
    if (count > 0) {
        Badge { Text("$count") }
    }
}
```

## Focus Management

### Keyboard Navigation

Navigation rails support keyboard navigation:

**Tab Order:**
1. Menu button (if present)
2. FAB (if present)
3. First navigation item
4. Second navigation item
5. Additional items in order

**Keyboard Shortcuts:**
- **Tab**: Move to next item
- **Shift + Tab**: Move to previous item
- **Enter/Space**: Activate selected item
- **Arrow Keys**: Navigate between items (optional)

### Focus Indicators

Clear focus indicators required for accessibility:
- Visible focus ring or highlight
- Sufficient contrast (3:1 minimum)
- Consistent across all items
- Respects system preferences

## State Persistence

### Across Navigation
- Selected item persists when navigating
- Expanded/collapsed state can persist
- User preferences saved
- Restored on app restart

### Configuration Changes
- Maintain state during rotation
- Handle screen size changes
- Preserve selection across recomposition
- Use `rememberSaveable` for state

## Touch Target Sizes

### Minimum Sizes
- Touch targets: 48dp × 48dp minimum
- Actual visual elements can be smaller
- Padding extends clickable area
- Prevents accidental taps

### Full-Width Targets
- Target area spans full width of rail
- Even if visual container is smaller
- Easier to tap on edges
- Better mobile experience

## Loading States

### Initial Load
- Show skeleton or placeholder
- Maintain layout stability
- Don't shift positions
- Quick, smooth appearance

### Content Updates
- Update badges without layout shift
- Smooth icon transitions
- Maintain selected state
- Clear loading indicators

---

*See also: [Usage Guidelines](usage.md) | [Accessibility](accessibility.md) | [Implementation](implementation.md)*
