# Navigation Rails - Implementation

This document provides implementation details and code examples for navigation rails using Jetpack Compose.

## Basic Implementation

### Simple Navigation Rail

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
fun BasicNavigationRail() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Favorites", "Settings")
    val selectedIcons = listOf(
        Icons.Filled.Home, 
        Icons.Filled.Favorite, 
        Icons.Filled.Star
    )
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
                        if (selectedItem == index) selectedIcons[index] 
                        else unselectedIcons[index],
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

## NavigationRail Component

### Parameters

```kotlin
@Composable
fun NavigationRail(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationRailDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    header: (@Composable ColumnScope.() -> Unit)? = null,
    windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit
): Unit
```

#### Parameter Details

**modifier**
- Type: `Modifier`
- Default: `Modifier`
- Description: Modifier to be applied to navigation rail

**containerColor**
- Type: `Color`
- Default: `NavigationRailDefaults.ContainerColor`
- Description: Background color (use `Color.Transparent` for no color)

**contentColor**
- Type: `Color`
- Default: `contentColorFor(containerColor)`
- Description: Preferred content color, defaults to matching color for containerColor

**header**
- Type: `(@Composable ColumnScope.() -> Unit)?`
- Default: `null`
- Description: Optional header for FAB or logo

**windowInsets**
- Type: `WindowInsets`
- Default: `NavigationRailDefaults.windowInsets`
- Description: Window insets configuration

**content**
- Type: `@Composable ColumnScope.() -> Unit`
- Description: Navigation rail content, typically 3-7 NavigationRailItems

## NavigationRailItem Component

### Parameters

```kotlin
@Composable
fun NavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationRailItemColors = NavigationRailItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
): Unit
```

#### Parameter Details

**selected**
- Type: `Boolean`
- Required: Yes
- Description: Whether this item is currently selected

**onClick**
- Type: `() -> Unit`
- Required: Yes
- Description: Callback invoked when item is clicked

**icon**
- Type: `@Composable () -> Unit`
- Required: Yes
- Description: Icon composable, typically an `Icon`

**enabled**
- Type: `Boolean`
- Default: `true`
- Description: Controls enabled state; when false, item won't respond to input

**label**
- Type: `(@Composable () -> Unit)?`
- Default: `null`
- Description: Optional text label for this item

**alwaysShowLabel**
- Type: `Boolean`
- Default: `true`
- Description: Whether to always show label; if false, only shown when selected

**colors**
- Type: `NavigationRailItemColors`
- Default: `NavigationRailItemDefaults.colors()`
- Description: Color configuration for the item

**interactionSource**
- Type: `MutableInteractionSource?`
- Default: `null`
- Description: Interaction source for custom handling

## Advanced Examples

### With FAB Header

```kotlin
@Composable
fun NavigationRailWithFAB() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    
    NavigationRail(
        header = {
            FloatingActionButton(
                onClick = { /* Handle FAB click */ },
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, contentDescription = item) },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}
```

### With Badges

```kotlin
@Composable
fun NavigationRailWithBadges() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val badgeCounts = listOf(0, 5, 99)
    
    NavigationRail {
        items.forEachIndexed { index, (item, icon) ->
            NavigationRailItem(
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCounts[index] > 0) {
                                Badge {
                                    Text(badgeCounts[index].toString())
                                }
                            }
                        }
                    ) {
                        Icon(icon, contentDescription = item)
                    }
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}
```

### With Menu Button

```kotlin
@Composable
fun NavigationRailWithMenu() {
    var selectedItem by remember { mutableIntStateOf(0) }
    var isExpanded by remember { mutableStateOf(false) }
    
    NavigationRail(
        header = {
            IconButton(onClick = { isExpanded = !isExpanded }) {
                Icon(
                    if (isExpanded) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = "Menu"
                )
            }
        }
    ) {
        // Primary items always visible
        PrimaryNavigationItems(selectedItem) { selectedItem = it }
        
        // Secondary items shown when expanded
        AnimatedVisibility(visible = isExpanded) {
            Column {
                SecondaryNavigationItems(selectedItem) { selectedItem = it }
            }
        }
    }
}
```

### Integrated with Scaffold

```kotlin
@Composable
fun AppWithNavigationRail() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    
    Scaffold(
        navigationRail = {
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
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Main content based on selectedItem
            when (selectedItem) {
                0 -> HomeScreen()
                1 -> SearchScreen()
                2 -> SettingsScreen()
            }
        }
    }
}
```

### With Navigation Component Integration

```kotlin
@Composable
fun NavigationRailWithNavController() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    
    val items = listOf(
        NavigationItem("home", "Home", Icons.Default.Home),
        NavigationItem("search", "Search", Icons.Default.Search),
        NavigationItem("settings", "Settings", Icons.Default.Settings)
    )
    
    Scaffold(
        navigationRail = {
            NavigationRail {
                items.forEach { item ->
                    NavigationRailItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { 
                            it.route == item.route 
                        } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") { HomeScreen() }
            composable("search") { SearchScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
```

### Custom Colors

```kotlin
@Composable
fun CustomColorNavigationRail() {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        NavigationRailItem(
            icon = { Icon(Icons.Default.Home, "Home") },
            label = { Text("Home") },
            selected = true,
            onClick = { },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
```

## Adaptive Layout Implementation

### Window Size Class Integration

```kotlin
@Composable
fun AdaptiveNavigationScaffold() {
    val windowSizeClass = calculateWindowSizeClass()
    
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Use bottom navigation bar
            ScaffoldWithBottomBar()
        }
        WindowWidthSizeClass.Medium,
        WindowWidthSizeClass.Expanded -> {
            // Use navigation rail
            ScaffoldWithNavigationRail()
        }
    }
}
```

## State Management

### Remember State with SavedStateHandle

```kotlin
@Composable
fun StatefulNavigationRail() {
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }
    
    NavigationRail {
        // Items maintain state across configuration changes
    }
}
```

## Testing

### UI Tests

```kotlin
@Test
fun navigationRail_selectionWorks() {
    composeTestRule.setContent {
        NavigationRailWithState()
    }
    
    // Click second item
    composeTestRule.onNodeWithText("Search").performClick()
    
    // Verify selection
    composeTestRule.onNodeWithText("Search")
        .assertIsSelected()
}
```

---

*See also: [Overview](overview.md) | [Behavior](behavior.md) | [Best Practices](best-practices.md)*
