# Navigation Drawer - Implementation

This document provides implementation guidance for navigation drawers in Jetpack Compose using Material 3 components.

## Overview

The navigation drawer component provides a slide-in menu for app navigation. This guide covers implementation using Compose Material 3.

## Basic Implementation

### Modal Navigation Drawer

Use the `ModalNavigationDrawer` composable to implement a basic navigation drawer:

```kotlin
@Composable
fun BasicNavigationDrawerExample() {
    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Text("Drawer title", modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(text = "Drawer Item") },
                    selected = false,
                    onClick = { /*TODO*/ }
                )
                // ...other drawer items
            }
        }
    ) {
        // Screen content
    }
}
```

### Key Components

- **`ModalNavigationDrawer`**: The main container for the drawer
- **`ModalDrawerSheet`**: Provides Material Design styling for the drawer content
- **`NavigationDrawerItem`**: Individual navigation items
- **`HorizontalDivider`**: Separates sections within the drawer

## Controlling Drawer State

Use `DrawerState` to programmatically control the drawer:

```kotlin
@Composable
fun ControlledDrawerExample() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet { 
                /* Drawer content */ 
            }
        },
    ) {
        Scaffold(
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text("Show drawer") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "") },
                    onClick = {
                        scope.launch {
                            drawerState.apply {
                                if (isClosed) open() else close()
                            }
                        }
                    }
                )
            }
        ) { contentPadding ->
            // Screen content
        }
    }
}
```

### DrawerState API

- **`open()`**: Opens the drawer (suspending function)
- **`close()`**: Closes the drawer (suspending function)
- **`isClosed`**: Boolean property indicating if drawer is closed
- **`isOpen`**: Boolean property indicating if drawer is open

## Gesture Control

Control whether the drawer responds to swipe gestures:

```kotlin
ModalNavigationDrawer(
    drawerContent = {
        ModalDrawerSheet {
            // Drawer contents
        }
    },
    gesturesEnabled = false  // Disables swipe-to-open/close
) {
    // Screen content
}
```

## Detailed Example with Sections

Create a navigation drawer with multiple sections, dividers, and icons:

```kotlin
@Composable
fun DetailedDrawerExample(
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Drawer Title", 
                        modifier = Modifier.padding(16.dp), 
                        style = MaterialTheme.typography.titleLarge
                    )
                    HorizontalDivider()

                    // Section 1
                    Text(
                        "Section 1", 
                        modifier = Modifier.padding(16.dp), 
                        style = MaterialTheme.typography.titleMedium
                    )
                    NavigationDrawerItem(
                        label = { Text("Item 1") },
                        selected = false,
                        onClick = { /* Handle click */ }
                    )
                    NavigationDrawerItem(
                        label = { Text("Item 2") },
                        selected = false,
                        onClick = { /* Handle click */ }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Section 2
                    Text(
                        "Section 2", 
                        modifier = Modifier.padding(16.dp), 
                        style = MaterialTheme.typography.titleMedium
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        icon = { 
                            Icon(Icons.Outlined.Settings, contentDescription = null) 
                        },
                        badge = { Text("20") },
                        onClick = { /* Handle click */ }
                    )
                    NavigationDrawerItem(
                        label = { Text("Help and feedback") },
                        selected = false,
                        icon = { 
                            Icon(Icons.AutoMirrored.Outlined.Help, contentDescription = null) 
                        },
                        onClick = { /* Handle click */ },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        drawerState = drawerState
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Navigation Drawer Example") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isClosed) {
                                    drawerState.open()
                                } else {
                                    drawerState.close()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}
```

### Key Implementation Points

1. **Scrollable Content**: Use `verticalScroll(rememberScrollState())` to enable scrolling for long lists
2. **Section Labels**: Use `Text` with `titleMedium` typography for section headers
3. **Dividers**: Use `HorizontalDivider` to separate sections
4. **Icons**: Add icons to items using the `icon` parameter
5. **Badges**: Add notification badges using the `badge` parameter
6. **Menu Control**: Toggle drawer from `TopAppBar` navigation icon

## Managing Selected State

Track and display the currently selected navigation item:

```kotlin
@Composable
fun NavigationDrawerWithSelection() {
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Inbox", "Sent", "Drafts", "Trash")
    val icons = listOf(
        Icons.Default.Inbox,
        Icons.Default.Send,
        Icons.Default.Drafts,
        Icons.Default.Delete
    )
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                items.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(item) },
                        selected = index == selectedItem,
                        onClick = {
                            selectedItem = index
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(items[selectedItem]) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            // Content for selected item
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(items[selectedItem])
            }
        }
    }
}
```

## Permanent Navigation Drawer

For larger screens, implement a permanent drawer using `PermanentNavigationDrawer`:

```kotlin
@Composable
fun PermanentDrawerExample() {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(Modifier.width(240.dp)) {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("Item 1") },
                    selected = false,
                    onClick = { /* Handle */ }
                )
                NavigationDrawerItem(
                    label = { Text("Item 2") },
                    selected = true,
                    onClick = { /* Handle */ }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Permanent Drawer") })
            }
        ) { padding ->
            // Main content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text("Screen content")
            }
        }
    }
}
```

## Dismissible Navigation Drawer

For medium-sized screens, implement a dismissible drawer:

```kotlin
@Composable
fun DismissibleDrawerExample() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    DismissibleNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DismissibleDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("Item 1") },
                    selected = false,
                    onClick = { /* Handle */ }
                )
                NavigationDrawerItem(
                    label = { Text("Item 2") },
                    selected = true,
                    onClick = { /* Handle */ }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dismissible Drawer") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.apply {
                                    if (isClosed) open() else close()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            // Main content
        }
    }
}
```

## Responsive Implementation

Adapt drawer type based on window size:

```kotlin
@Composable
fun ResponsiveDrawer(windowSizeClass: WindowSizeClass) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Use modal drawer with bottom navigation
            ModalNavigationDrawerExample()
        }
        WindowWidthSizeClass.Medium -> {
            // Use modal drawer with navigation rail
            NavigationRailWithModalDrawer()
        }
        WindowWidthSizeClass.Expanded -> {
            // Use permanent or dismissible drawer
            PermanentNavigationDrawerExample()
        }
    }
}
```

## Customization

### Custom Colors

```kotlin
NavigationDrawerItem(
    label = { Text("Item") },
    selected = false,
    onClick = { },
    colors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedContainerColor = Color.Transparent,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
)
```

### Custom Shape

```kotlin
ModalDrawerSheet(
    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
) {
    // Drawer content
}
```

## Best Practices

1. **State Management**: Always use `rememberDrawerState` and `rememberCoroutineScope`
2. **Closing After Selection**: Close modal drawers after item selection
3. **Accessibility**: Provide content descriptions for icons
4. **Spacing**: Use consistent padding with `NavigationDrawerItemDefaults.ItemPadding`
5. **Scrolling**: Enable vertical scrolling for long lists
6. **Selection State**: Maintain and display the current selection
7. **Animation**: Let the framework handle animations (don't override unless necessary)

## Common Patterns

### Account Switcher

```kotlin
@Composable
fun DrawerWithAccountSwitcher() {
    ModalDrawerSheet {
        // Account section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("User Name", style = MaterialTheme.typography.titleMedium)
                Text("user@email.com", style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        
        // Navigation items
        NavigationDrawerItem(
            label = { Text("Home") },
            selected = true,
            onClick = { }
        )
        // More items...
    }
}
```

## Resources

- [Material Design Navigation Drawer](https://m3.material.io/components/navigation-drawer/overview)
- [Android Developer Compose Drawer](https://developer.android.com/develop/ui/compose/components/drawer)
- [Navigation Drawer Guidelines](./guidelines.md)
- [Navigation Drawer Specs](./specs.md)
