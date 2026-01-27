# Navigation Rails - Accessibility

This document provides comprehensive accessibility guidelines for navigation rails.

## Core Accessibility Principles

Navigation rails must be accessible to all users, including those using:
- Screen readers (TalkBack, VoiceOver)
- Keyboard navigation
- Switch controls
- Voice commands
- High contrast modes
- Large text sizes

## Content Descriptions

### Icon Content Descriptions

All icons must have meaningful content descriptions:

#### ✅ Good Examples

```kotlin
NavigationRailItem(
    icon = {
        Icon(
            Icons.Default.Home,
            contentDescription = "Home" // Clear, concise
        )
    },
    label = { Text("Home") },
    selected = selected,
    onClick = onClick
)
```

```kotlin
NavigationRailItem(
    icon = {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Settings" // Matches label
        )
    },
    label = { Text("Settings") },
    selected = selected,
    onClick = onClick
)
```

#### ❌ Poor Examples

```kotlin
// Missing content description
Icon(Icons.Default.Home, contentDescription = null)

// Too verbose
Icon(Icons.Default.Home, contentDescription = "Click here to navigate to the home screen")

// Doesn't match purpose
Icon(Icons.Default.Home, contentDescription = "House icon")
```

### Badge Content Descriptions

Badges with counts should announce the count:

```kotlin
BadgedBox(
    badge = {
        Badge {
            Text("5")
        }
    }
) {
    Icon(
        Icons.Default.Message,
        contentDescription = "Messages, 5 unread" // Includes count
    )
}
```

### State Announcements

Screen readers should announce selection state:

```kotlin
NavigationRailItem(
    icon = { Icon(Icons.Default.Home, "Home") },
    label = { Text("Home") },
    selected = true, // Automatically announced by framework
    onClick = onClick
)
// Screen reader: "Home, selected, button"
```

## Color Contrast

### Minimum Contrast Ratios

**Text and Icons:**
- Normal text: 4.5:1 minimum
- Large text: 3:1 minimum
- UI components: 3:1 minimum
- Active indicators: 3:1 minimum

### Testing Contrast

Use tools to verify contrast:
- Chrome DevTools Color Picker
- WebAIM Contrast Checker
- Material Theme Builder

#### Examples

```kotlin
// ✅ Good: Uses theme colors with sufficient contrast
NavigationRail(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface
)

// ⚠️ Caution: Verify custom colors meet contrast requirements
NavigationRail(
    containerColor = Color(0xFFE0E0E0),
    contentColor = Color(0xFF757575) // Must test this combination
)
```

### High Contrast Mode

Support system high contrast settings:

```kotlin
@Composable
fun AccessibleNavigationRail() {
    val highContrast = // Detect system high contrast mode
    
    NavigationRail(
        containerColor = if (highContrast) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.surface
        }
    )
}
```

## Touch Targets

### Minimum Touch Target Size

**Required: 48dp × 48dp minimum**

```kotlin
NavigationRailItem(
    icon = { Icon(Icons.Default.Home, "Home") },
    label = { Text("Home") },
    selected = selected,
    onClick = onClick,
    modifier = Modifier
        .size(48.dp) // Minimum size
)
```

### Target Area Spans Full Width

Even if visual elements are smaller, clickable area should span rail width:

```kotlin
// Framework handles this automatically
// Target area always spans full width of navigation rail
NavigationRailItem(/* ... */)
```

## Keyboard Navigation

### Tab Order

Logical tab order for keyboard users:

1. Menu button (if present)
2. FAB (if present)
3. First navigation item
4. Second navigation item
5. Subsequent items in logical order

### Focus Management

```kotlin
@Composable
fun KeyboardAccessibleNavigationRail() {
    val focusManager = LocalFocusManager.current
    var selectedItem by remember { mutableIntStateOf(0) }
    
    NavigationRail {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label) },
                selected = selectedItem == index,
                onClick = {
                    selectedItem = index
                    // Focus is automatically managed
                }
            )
        }
    }
}
```

### Keyboard Shortcuts

Standard keyboard interactions:
- **Tab**: Navigate forward through items
- **Shift + Tab**: Navigate backward
- **Enter/Space**: Activate focused item
- **Arrow Keys**: Navigate between items (optional enhancement)

## Focus Indicators

### Visible Focus State

Clear focus indicators are required:

```kotlin
NavigationRailItem(
    icon = { Icon(Icons.Default.Home, "Home") },
    label = { Text("Home") },
    selected = selected,
    onClick = onClick,
    // Focus indicator provided by framework
    // Ensure custom styling doesn't remove it
)
```

### Focus Indicator Contrast

Focus indicators must have sufficient contrast:
- 3:1 contrast against adjacent colors
- Visible in both light and dark themes
- Respects system preferences

## Screen Reader Support

### Testing with TalkBack (Android)

Enable TalkBack and verify:

1. **Navigation**
   - Swipe right/left to move between items
   - Each item announces clearly

2. **Selection**
   - Current selection is announced
   - Changes in state are announced

3. **Activation**
   - Double-tap activates item
   - Confirmation is announced

### Expected Announcements

```
// Unselected item
"Home, button"

// Selected item
"Home, selected, button"

// Item with badge
"Messages, 5 unread, button"

// Disabled item
"Settings, dimmed, button"
```

## Semantic Information

### Semantics Modifiers

Add semantic information when needed:

```kotlin
NavigationRailItem(
    icon = { Icon(Icons.Default.Home, "Home") },
    label = { Text("Home") },
    selected = selected,
    onClick = onClick,
    modifier = Modifier.semantics {
        // Most semantics handled automatically
        // Add custom semantics only if needed
        stateDescription = if (selected) "Currently selected" else "Not selected"
    }
)
```

## Dynamic Content

### Announcing Updates

When content updates dynamically:

```kotlin
var badgeCount by remember { mutableStateOf(0) }
var announcement by remember { mutableStateOf("") }

LaunchedEffect(badgeCount) {
    if (badgeCount > 0) {
        announcement = "$badgeCount new messages"
    }
}

NavigationRailItem(
    icon = {
        BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
            Icon(Icons.Default.Message, "Messages, $badgeCount unread")
        }
    },
    label = { Text("Messages") },
    selected = selected,
    onClick = onClick,
    modifier = Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = announcement.ifEmpty { "Messages" }
    }
)
```

## Large Text Support

### Dynamic Type

Support system text scaling:

```kotlin
NavigationRailItem(
    icon = { Icon(Icons.Default.Home, "Home") },
    label = {
        Text(
            "Home",
            // Uses Material typography which scales with system settings
            style = MaterialTheme.typography.labelMedium
        )
    },
    selected = selected,
    onClick = onClick
)
```

### Testing Large Text

Test with system text size at:
- 100% (default)
- 150% (medium)
- 200% (large)
- 300%+ (accessibility sizes)

Ensure:
- Labels don't truncate
- Icons remain visible
- Touch targets maintain size
- Layout doesn't break

## Motion and Animation

### Reduced Motion

Respect user preference for reduced motion:

```kotlin
@Composable
fun AccessibleAnimations() {
    val reduceMotion = // Detect system preference
    
    val animationDuration = if (reduceMotion) {
        0 // No animation
    } else {
        300 // Standard duration
    }
    
    AnimatedVisibility(
        visible = expanded,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            expandHorizontally()
        }
    ) {
        // Expanded content
    }
}
```

## Testing Checklist

### Manual Testing

- [ ] Enable TalkBack and navigate through all items
- [ ] Verify all icons have content descriptions
- [ ] Test keyboard navigation with Tab/Shift+Tab
- [ ] Activate items with Enter and Space keys
- [ ] Check focus indicators are visible
- [ ] Verify selection state is announced
- [ ] Test with system text size at 200%
- [ ] Verify in high contrast mode
- [ ] Test with reduced motion enabled
- [ ] Check color contrast ratios

### Automated Testing

```kotlin
@Test
fun navigationRail_hasContentDescriptions() {
    composeTestRule.setContent {
        TestNavigationRail()
    }
    
    composeTestRule
        .onAllNodes(hasClickAction())
        .assertAll(hasContentDescription())
}

@Test
fun navigationRail_meetsMinimumTouchTargets() {
    composeTestRule.setContent {
        TestNavigationRail()
    }
    
    composeTestRule
        .onAllNodes(hasClickAction())
        .assertAll(hasTouchTarget(48.dp, 48.dp))
}
```

## Common Accessibility Issues

### Issue 1: Missing Content Descriptions

**Problem:**
```kotlin
Icon(Icons.Default.Home, contentDescription = null)
```

**Solution:**
```kotlin
Icon(Icons.Default.Home, contentDescription = "Home")
```

### Issue 2: Poor Color Contrast

**Problem:**
```kotlin
NavigationRail(
    containerColor = Color.LightGray,
    contentColor = Color.Gray // Insufficient contrast
)
```

**Solution:**
```kotlin
NavigationRail(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface
)
```

### Issue 3: Small Touch Targets

**Problem:**
```kotlin
Icon(
    Icons.Default.Home,
    modifier = Modifier.size(24.dp) // Too small for touch
)
```

**Solution:**
```kotlin
Icon(
    Icons.Default.Home,
    modifier = Modifier
        .size(24.dp)
        .padding(12.dp) // Adds padding to reach 48dp touch target
)
```

### Issue 4: Not Announcing State Changes

**Problem:** Selection changes without announcement

**Solution:** Framework handles this automatically when using `selected` parameter

### Issue 5: Custom Focus Indicators Removed

**Problem:**
```kotlin
NavigationRailItem(
    modifier = Modifier.focusable(false) // Removes focus
)
```

**Solution:**
```kotlin
NavigationRailItem(
    // Let framework handle focus
)
```

## Resources

### Testing Tools
- **TalkBack**: Android screen reader
- **Accessibility Scanner**: Android accessibility testing
- **Color Contrast Analyzer**: Desktop tool for contrast testing
- **Android Studio Layout Inspector**: Inspect accessibility properties

### Guidelines
- [WCAG 2.1](https://www.w3.org/WAI/WCAG21/quickref/)
- [Material Design Accessibility](https://m3.material.io/foundations/accessible-design/overview)
- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility)

---

*See also: [Best Practices](best-practices.md) | [Implementation](implementation.md) | [Usage Guidelines](usage.md)*
