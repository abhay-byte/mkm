# Navigation Rails - Best Practices

This document outlines best practices and common pitfalls when implementing navigation rails.

## Do's and Don'ts

### Container and Placement

#### ✅ Do
- Place navigation rail on the leading edge of the window
- Use left side for LTR languages, right side for RTL languages
- Keep rail outside of panes and content areas
- Maintain consistent positioning across app

#### ❌ Don't
- Use navigation rail horizontally (use navigation bar instead)
- Place rail within body content or panes
- Hide collapsed navigation rail
- Use both navigation rail and navigation bar simultaneously

### FAB Placement

#### ✅ Do
- Place FAB at the top of the navigation rail
- Set FAB elevation to level 0 when nested in rail
- Use for app's primary action
- Transition to extended FAB when rail expands

#### ❌ Don't
- Place FAB below navigation items
- Use logo as menu button to expand rail
- Place multiple FABs in the rail
- Make FAB compete with navigation items

### Active Indicator

#### ✅ Do
- Use active indicator only for the current open page
- Show filled icon with color change when selected
- Ensure indicator spans full width of target area
- Maintain clear visual distinction

#### ❌ Don't
- Show active indicator on multiple items simultaneously
- Use indicator for non-navigation purposes
- Hide indicator on selected items
- Make indicator too subtle to notice

### Icons

#### ✅ Do
- Use icons that clearly symbolize page content
- Provide meaningful content descriptions
- Use filled icons for selected state
- Use outlined icons for unselected state
- Browse Google Fonts for appropriate icons

#### ❌ Don't
- Use ambiguous or unclear icons
- Forget content descriptions for accessibility
- Use same icon style for both states
- Create custom icons without testing comprehension

### Label Text

#### ✅ Do
- Write clear, concise labels that describe destinations
- Keep labels to one word when possible
- Provide labels for all navigation items
- Break longer phrases between words if necessary

#### ❌ Don't
- Truncate labels with ellipsis ("Sett...")
- Reduce type size to fit longer text
- Use vague or ambiguous labels
- Write multi-word phrases when single words work
- Leave items without labels

### Badges

#### ✅ Do
- Place badges in upper right of icons (collapsed rail)
- Place badges next to label text (expanded rail)
- Use for dynamic information like counts
- Show "99+" for values over 99
- Animate badge changes smoothly

#### ❌ Don't
- Overuse badges on every item
- Use badges for static information
- Place badges inconsistently
- Show excessively large numbers without truncation

## Design Patterns

### Number of Destinations

**Recommended: 3-7 destinations**

**Fewer than 3:**
- Consider using tabs or another pattern
- May not justify dedicated navigation rail
- User might expect different navigation pattern

**More than 7:**
- Use modal expanded rail for secondary destinations
- Group related destinations
- Consider information architecture redesign

### Destination Labels

**Best Practices:**

```
✅ Good Examples:
- "Home"
- "Search"
- "Profile"
- "Messages"
- "Settings"

❌ Avoid:
- "Go to Home Screen" (too verbose)
- "My Personal User Profile Settings" (way too long)
- "Stuff" (too vague)
- "Actions" (unclear)
```

### Icon Selection

**Guidelines:**

```kotlin
// ✅ Good: Clear, distinct icons
Icons.Default.Home        // for Home
Icons.Default.Search      // for Search
Icons.Default.Person      // for Profile
Icons.Default.Settings    // for Settings

// ❌ Poor: Similar or ambiguous icons
Icons.Default.Circle      // unclear purpose
Icons.Default.Square      // unclear purpose
Icons.Default.More        // should be Menu for rail
```

## Accessibility Best Practices

### Content Descriptions

#### ✅ Do
```kotlin
Icon(
    Icons.Default.Home,
    contentDescription = "Home" // Clear description
)
```

#### ❌ Don't
```kotlin
Icon(
    Icons.Default.Home,
    contentDescription = null // Missing description
)
```

### Color Contrast

#### ✅ Do
- Ensure 3:1 minimum contrast when container fill is disabled
- Test with color blindness simulators
- Verify contrast in both light and dark themes
- Use Material Design color roles

#### ❌ Don't
- Rely solely on color to indicate state
- Use low-contrast color combinations
- Forget to test in dark mode
- Ignore accessibility guidelines

### Focus Indicators

#### ✅ Do
- Provide clear, visible focus indicators
- Maintain 3:1 contrast for focus rings
- Support keyboard navigation
- Test with screen readers

#### ❌ Don't
- Remove focus indicators for aesthetic reasons
- Use subtle focus indicators users can't see
- Break keyboard navigation flow
- Ignore screen reader testing

## Performance Best Practices

### State Management

#### ✅ Do
```kotlin
// Preserve state across configuration changes
var selectedItem by rememberSaveable { mutableIntStateOf(0) }
```

#### ❌ Don't
```kotlin
// State lost on rotation
var selectedItem by remember { mutableIntStateOf(0) }
```

### Recomposition Optimization

#### ✅ Do
```kotlin
// Stable parameters prevent unnecessary recomposition
@Composable
fun NavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    NavigationRailItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick
    )
}
```

## Layout Best Practices

### Responsive Design

#### ✅ Do
- Use `WindowSizeClass` to determine navigation pattern
- Transform to navigation bar on compact screens
- Adapt content layout when rail expands/collapses
- Test on multiple screen sizes

#### ❌ Don't
- Force navigation rail on small screens
- Use fixed widths that don't adapt
- Ignore tablet and foldable devices
- Forget landscape orientations

### Content Integration

#### ✅ Do
```kotlin
Scaffold(
    navigationRail = { AppNavigationRail() }
) { paddingValues ->
    // Content respects rail space
    Box(modifier = Modifier.padding(paddingValues)) {
        MainContent()
    }
}
```

#### ❌ Don't
```kotlin
Box {
    // Content overlaps with rail
    NavigationRail()
    MainContent()
}
```

## Animation Best Practices

### Expand/Collapse Transitions

#### ✅ Do
- Use standard Material motion (250-300ms)
- Coordinate all element animations
- Maintain smooth 60fps
- Use appropriate easing curves

#### ❌ Don't
- Use jarring or instant transitions
- Animate elements independently
- Use inconsistent durations
- Create jank or dropped frames

### Selection Animation

#### ✅ Do
- Animate active indicator from center
- Smoothly transition icon states
- Use consistent timing across items
- Provide clear visual feedback

#### ❌ Don't
- Jump instantly between states
- Use overly long animations
- Skip animation for better performance
- Make animations distracting

## Testing Best Practices

### Unit Tests

```kotlin
@Test
fun navigationRail_correctItemSelected() {
    val selectedItem = 1
    
    composeTestRule.setContent {
        TestNavigationRail(selectedItem = selectedItem)
    }
    
    composeTestRule
        .onNodeWithText("Search")
        .assertIsSelected()
}
```

### Accessibility Tests

```kotlin
@Test
fun navigationRail_hasContentDescriptions() {
    composeTestRule.setContent {
        TestNavigationRail()
    }
    
    composeTestRule
        .onAllNodesWithContentDescription("Home")
        .assertCountEquals(1)
}
```

### Integration Tests

```kotlin
@Test
fun navigationRail_navigatesToCorrectScreen() {
    val navController = TestNavHostController(context)
    
    composeTestRule.setContent {
        AppWithNavigationRail(navController)
    }
    
    composeTestRule
        .onNodeWithText("Settings")
        .performClick()
    
    assertEquals("settings", navController.currentDestination?.route)
}
```

## Common Pitfalls

### Pitfall 1: Too Many Destinations
**Problem:** Cramming 10+ destinations in collapsed rail
**Solution:** Use modal expanded rail or reconsider architecture

### Pitfall 2: Inconsistent Icons
**Problem:** Mixing icon styles or unclear symbols
**Solution:** Use consistent icon family, test comprehension

### Pitfall 3: Poor Touch Targets
**Problem:** Items too small or close together
**Solution:** Maintain 48dp minimum touch targets

### Pitfall 4: Missing State Persistence
**Problem:** Losing selection on rotation
**Solution:** Use `rememberSaveable` for state

### Pitfall 5: Accessibility Oversights
**Problem:** Missing content descriptions, poor contrast
**Solution:** Test with TalkBack, verify contrast ratios

## Checklist

Before shipping navigation rail implementation:

- [ ] 3-7 destinations in collapsed rail
- [ ] All items have clear labels
- [ ] Icons have content descriptions
- [ ] Active indicator shows only current page
- [ ] FAB positioned at top (if used)
- [ ] State persists across configuration changes
- [ ] Adapts to different screen sizes
- [ ] Minimum 3:1 color contrast
- [ ] Touch targets at least 48dp
- [ ] Smooth expand/collapse animation
- [ ] Tested with screen readers
- [ ] Tested on physical devices
- [ ] Works in both light and dark themes

---

*See also: [Implementation](implementation.md) | [Accessibility](accessibility.md) | [Usage Guidelines](usage.md)*
