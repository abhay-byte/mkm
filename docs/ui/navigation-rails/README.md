# Navigation Rails Documentation

This folder contains comprehensive documentation for implementing navigation rails in your Android app using Material Design 3 and Jetpack Compose.

## Contents

### [Overview](overview.md)
Introduction to navigation rails, types (collapsed, expanded, modal), and when to use them.

**Topics covered:**
- What is a navigation rail
- Collapsed vs expanded types
- Standard vs modal configurations
- When to use navigation rails vs navigation bars
- Deprecated types

### [Anatomy](anatomy.md)
Detailed breakdown of all navigation rail components and their specifications.

**Topics covered:**
- Container and placement
- Menu button
- FAB integration
- Active indicators
- Icons and label text
- Badges
- Color schemes and states

### [Usage Guidelines](usage.md)
Best practices for implementing navigation rails in different contexts.

**Topics covered:**
- Placement in layouts
- Adaptive design patterns
- Window size considerations
- Common layouts
- Integration with other components
- Multi-pane layouts

### [Behavior](behavior.md)
How navigation rails behave in different scenarios and interactions.

**Topics covered:**
- Scrolling behavior (vertical and horizontal)
- Selection and navigation
- Expansion and collapse
- Back navigation
- Badge updates
- Focus management

### [Implementation](implementation.md)
Complete code examples and API documentation for Jetpack Compose.

**Topics covered:**
- Basic implementation
- Component parameters
- Advanced examples
- Navigation integration
- State management
- Testing

### [Best Practices](best-practices.md)
Do's and don'ts, common pitfalls, and design patterns.

**Topics covered:**
- Design patterns
- Common mistakes
- Performance optimization
- Testing strategies
- Checklist before shipping

### [Accessibility](accessibility.md)
Comprehensive accessibility guidelines and testing procedures.

**Topics covered:**
- Content descriptions
- Color contrast
- Touch targets
- Keyboard navigation
- Screen reader support
- Testing checklist

## Quick Start

### Basic Navigation Rail

```kotlin
@Composable
fun MyNavigationRail() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    
    NavigationRail {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, item) },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}
```

## Key Guidelines

### When to Use Navigation Rails

✅ **Use for:**
- Medium to extra-large screens (tablets, desktop)
- 3-7 primary destinations
- Persistent vertical navigation needs
- Multi-pane layouts

❌ **Don't use for:**
- Compact/phone screens (use navigation bar)
- Horizontal navigation (use navigation bar)
- More than 7 destinations without grouping

### Essential Rules

1. **Always provide label text** for all items
2. **Place on leading edge** of window (left for LTR, right for RTL)
3. **Don't hide collapsed rail** (can hide expanded modal rail)
4. **Use only one active indicator** at a time
5. **Maintain 48dp minimum touch targets**

## Resources

- [Material Design 3 Navigation Rail Spec](https://m3.material.io/components/navigation-rail)
- [Jetpack Compose API Reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#NavigationRail(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Color,androidx.compose.ui.graphics.Color,kotlin.Function1,androidx.compose.foundation.layout.WindowInsets,kotlin.Function1))
- [Material Design Guidelines](https://m3.material.io/foundations/layout/applying-layout/window-size-classes)

## Related Documentation

- [Navigation Bar Documentation](../navigation-bar/) - For compact screens
- [Navigation Drawer Documentation](../navigation-drawer/) - For temporary navigation overlays
- [App Architecture](../../navigation/architecture.md) - Overall navigation patterns

---

**Last Updated:** January 27, 2026
**Material Design Version:** M3 (Material Design 3)
**Compose Version:** material3:1.x.x
