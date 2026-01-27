# Navigation Rails

Navigation rails let people switch between UI views on mid-sized devices. They provide access to primary destinations in apps when using tablet and desktop screens.

## Overview

Navigation rails display navigation items, a menu, and a floating action button (FAB) in a vertical orientation. They run along the leading edge of the window and should contain 3–7 navigation items.

## Types

### Collapsed Navigation Rail

The collapsed navigation rail is the primary type for modern applications:

- Runs along the leading edge of the window
- Contains 3–7 navigation items
- Should not be hidden
- Used in medium to extra large window sizes
- For medium windows with few destinations, consider using a navigation bar instead

### Expanded Navigation Rail

The expanded navigation rail can reveal additional destinations:

- Can be standard or modal
- Always opens from a menu icon
- Can reveal secondary destinations not visible when collapsed

#### Standard Configuration
- Placed beside body content
- Best for larger windows with lots of available space

#### Modal Configuration
- Overlaps the body content
- Should be opened from a menu icon
- Use for:
  - Information dense layouts where space is limited
  - Products with many navigation items

### Deprecated Types

**Original Navigation Rail**: The original navigation rail has been deprecated and should be replaced by the collapsed navigation rail.

| Type | Original M3 | M3 Expressive |
|------|-------------|---------------|
| Collapsed navigation rail | -- | Available |
| Expanded navigation rail | -- | Available |
| Navigation rail | Available | Will be deprecated. Use collapsed navigation rail |

## Anatomy

### Navigation Rail Elements

1. **Container**
2. **Menu** (optional)
3. **FAB or Extended FAB** (optional)
4. **Icon**
5. **Active indicator**
6. **Label text**
7. **Large badge** (optional)
8. **Large badge label** (optional)
9. **Small badge** (optional)

### Container

- Should be placed on the leading edge of the window
- Left side for left-to-right languages, right side for right-to-left languages
- Container fill can be turned off for direct surface appearance
- Always runs vertically along the side of a layout
- Items can be aligned as a group to top or center (use center alignment on tablets for easier reach)

### Menu (Optional)

- Transitions between collapsed and expanded navigation rails
- Can reveal secondary destinations when expanded
- Icon should change to represent collapse capability when expanded

### Floating Action Button (Optional)

- Ideal for anchoring to the top of the screen
- Places app's key action above navigation destinations
- When nested within navigation rail, resting elevation should be level 0
- Should be top-aligned, avoid placing below navigation items

### Active Indicator

- Shows which page is being displayed
- Use only for the current open page
- Hugs the label text in expanded nav rail
- Target area should always span the full width

### Icons

- Must symbolize the content of their page
- Fill and change color when destination is selected
- Should be clear and meaningful representations

### Label Text

- Should be short, meaningful descriptions of each destination
- All navigation items require a one-word label text
- Avoid wrapping long labels when possible
- Don't truncate or use ellipsis
- Don't reduce type size to fit more characters

### Badges

- Communicate dynamic information (counts, status)
- Collapsed nav rails: placed in upper right corner of icon
- Expanded nav rails: placed next to label text
- Types: Small badge, Large badge with number, Large badge with maximum character count

## Color Scheme

Navigation rail uses the following color roles:

**Light and Dark Schemes:**
- Surface container (optional)
- On secondary container
- Secondary container
- Secondary
- On surface variant
- Error
- On error

## States

Navigation rails support four interaction states:
1. **Enabled**
2. **Hovered**
3. **Focused**
4. **Pressed**

The navigation item's target area always spans the full width of the nav rail, even if the item container hugs its contents.

## Usage Guidelines

### When to Use

- Medium to extra large window sizes (tablets and desktop)
- When you need persistent vertical navigation
- For 3–7 primary destinations
- When space allows for dedicated navigation area

### When Not to Use

- Compact windows (use navigation bar instead)
- When you have more than 7 destinations without secondary grouping
- Horizontal navigation needs (use navigation bar)

### Placement

- Place outside any panes, along the leading edge of the window
- Don't place within body content
- Can be used alongside tabs for extra navigation layer
- When hidden, body content can fill remaining space if menu icon stays accessible

## Adaptive Design

### Resizing Behavior

**Screen Size Transitions:**
- Large → Small: Navigation rail transforms into navigation bar
- Never use navigation rail and navigation bar simultaneously

**Window Size Guidelines:**
- **Compact**: Don't use standard navigation rail (use navigation bar)
- **Medium**: Use navigation rail, especially for persistent vertical navigation
- **Expanded to Extra-Large**: Use navigation rail (not navigation bar)

### Presentation

- When transitioning collapsed → expanded, page contents should adjust automatically
- FAB should transition to extended FAB when expanding
- Extra destinations can be shown in expanded rail

## Behavior

### Scrolling

- **Vertical Scrolling**: Destinations remain visible and fixed
- **Horizontal Scrolling**: Rail can scroll off-screen or remain fixed
- Use divider or elevation to distinguish scrolling content

### Selection

- Tapped destinations use top-level transition pattern
- Icon becomes filled and active indicator expands from center
- Only one destination should show active indicator at a time

### Back Navigation

- Android predictive back gesture applies to modal expanded navigation rail
- Previous screen revealed in preview during gesture
- Rail pops off edge of window during predictive back

## Implementation

### Jetpack Compose Example

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun ExampleNavigationRail() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons = listOf(
        Icons.Outlined.Home, 
        Icons.Outlined.FavoriteBorder, 
        Icons.Outlined.StarBorder
    )
    
    NavigationRail {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = item,
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index },
            )
        }
    }
}
```

### Key Parameters

#### NavigationRail
- `modifier`: Modifier to be applied
- `containerColor`: Background color (use `Color.Transparent` for no color)
- `contentColor`: Preferred content color
- `header`: Optional header for FAB or logo
- `windowInsets`: Window insets configuration
- `content`: Navigation rail content (typically 3-7 NavigationRailItems)

#### NavigationRailItem
- `selected`: Whether item is selected
- `onClick`: Click handler
- `icon`: Icon composable
- `enabled`: Controls enabled state
- `label`: Optional text label
- `alwaysShowLabel`: Whether to always show label (default: true)
- `colors`: Color configuration
- `interactionSource`: Interaction source for custom handling

## Common Layouts

### Basic Configurations
1. Three navigation items
2. Three navigation items with menu
3. Three navigation items with FAB
4. Three navigation items with menu and FAB

### Best Practices

**Do:**
- Use clear, concise labels that describe destinations
- Place FAB at top of rail
- Use active indicator only for current page
- Write meaningful icon descriptions
- Maintain consistent navigation patterns

**Don't:**
- Use navigation rail horizontally (use navigation bar instead)
- Place FAB below navigation items
- Use active indicator for multiple items
- Truncate or use ellipsis in labels
- Reduce type size to fit longer text
- Use logo as menu button

## Accessibility

- Provide meaningful content descriptions for icons
- Ensure minimum 3:1 color contrast when container fill is off
- Support screen readers with proper labeling
- Maintain focus indicators for keyboard navigation
- Test with assistive technologies

## Related Components

- **Navigation Bar**: For horizontal navigation on smaller screens
- **Navigation Drawer**: For temporary navigation overlays
- **Tabs**: Can be used alongside navigation rails for additional hierarchy
- **Bottom App Bar**: Alternative for bottom navigation

---

*This documentation is based on Material Design 3 specifications and Jetpack Compose implementation guidelines.*