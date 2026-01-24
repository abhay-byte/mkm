# Loading Indicator - Guidelines

Best practices and design guidelines for using the Material 3 Loading Indicator effectively.

## When to Use Loading Indicator

### ✅ Appropriate Use Cases

| Scenario | Why Loading Indicator Works |
|----------|----------------------------|
| **App Launch** | Creates a memorable first impression with engaging animation |
| **Major State Transitions** | Signals important changes (e.g., switching accounts, loading new workspace) |
| **Full-Screen Loading** | When the entire screen content is loading |
| **Branded Experiences** | Custom polygons can reinforce brand identity |
| **Long Operations** | Engaging animation reduces perceived wait time |

### ❌ Inappropriate Use Cases

| Scenario | Better Alternative |
|----------|-------------------|
| **Quick Operations** (< 500ms) | No indicator or subtle CircularProgressIndicator |
| **Background Tasks** | Small CircularProgressIndicator in status area |
| **Inline Content** | LinearProgressIndicator or skeleton screens |
| **Button Actions** | Small CircularProgressIndicator inside button |
| **Frequent Updates** | Less prominent indicator to avoid fatigue |

## Design Principles

### 1. Prominence and Attention

Loading Indicators are **high-attention** components. Use them when loading is the primary user focus.

**Good Example:**
```
┌─────────────────────┐
│                     │
│    [App Logo]       │
│                     │
│   ◆ → ● → ★ → ◆    │  ← Loading Indicator
│                     │
│  Loading MKM...     │
│                     │
└─────────────────────┘
```

**Bad Example:**
```
┌─────────────────────┐
│ Header              │
│ ◆ → ● → ★ → ◆      │  ← Too prominent for header
│                     │
│ [Content]           │
│ [Content]           │
└─────────────────────┘
```

### 2. Single Indicator Principle

Use **one Loading Indicator per screen** to avoid visual chaos.

**✅ Good:**
```kotlin
// Single indicator for entire gallery load
if (isLoadingGallery) {
    LoadingIndicator()
} else {
    LazyGrid { /* 100 images */ }
}
```

**❌ Bad:**
```kotlin
// Multiple indicators for each image
LazyGrid {
    items(images) { image ->
        if (image.isLoading) {
            LoadingIndicator() // Don't do this!
        }
    }
}
```

### 3. Contextual Clarity

Always provide context about what's loading.

**✅ Good:**
```kotlin
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    LoadingIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        "Syncing your data...",
        style = MaterialTheme.typography.bodyLarge
    )
}
```

**❌ Bad:**
```kotlin
LoadingIndicator() // What's loading? User doesn't know.
```

## Sizing Guidelines

### Recommended Sizes

| Context | Size | Use Case |
|---------|------|----------|
| **Default** | 48dp | Standard loading screens |
| **Container Variant** | 48dp (Box) / 38dp (Icon) | Pull-to-refresh, floating indicators |
| **Prominent** | 64-80dp | Splash screens, major transitions |
| **Compact** | 40dp | Smaller containers, cards |
| **Large Display** | 96-120dp | Tablet/desktop full-screen loading |

### Size Examples

```kotlin
// Splash screen - large and prominent
LoadingIndicator(modifier = Modifier.size(80.dp))

// Standard loading - default size
LoadingIndicator(modifier = Modifier.size(48.dp))

// Card loading - compact
LoadingIndicator(modifier = Modifier.size(40.dp))
```

## Color Guidelines

### Using Theme Colors

Always use theme colors for consistency:

```kotlin
// Primary - most common
LoadingIndicator(color = MaterialTheme.colorScheme.primary)

// Secondary - for secondary actions
LoadingIndicator(color = MaterialTheme.colorScheme.secondary)

// Tertiary - for accent/special cases
LoadingIndicator(color = MaterialTheme.colorScheme.tertiary)

// On Surface - for neutral contexts
LoadingIndicator(color = MaterialTheme.colorScheme.onSurface)
```

### Contrast Requirements

Ensure the indicator has sufficient contrast with the background:

- **Minimum contrast ratio:** 3:1 (WCAG AA)
- **Recommended contrast ratio:** 4.5:1 (WCAG AAA)

```kotlin
// Good: High contrast
Box(
    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
) {
    LoadingIndicator(color = MaterialTheme.colorScheme.primary)
}

// Bad: Low contrast
Box(
    modifier = Modifier.background(Color(0xFFE0E0E0))
) {
    LoadingIndicator(color = Color(0xFFF0F0F0)) // Too similar!
}
```

## Placement Guidelines

### Vertical Centering

Loading Indicators should be vertically centered in their container:

```kotlin
Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Loading...")
    }
}
```

### Spacing

Maintain proper spacing around the indicator:

```kotlin
Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Spacer(Modifier.height(48.dp))  // Top spacing
    LoadingIndicator()
    Spacer(Modifier.height(16.dp))  // Space before text
    Text("Loading your content...")
    Spacer(Modifier.height(48.dp))  // Bottom spacing
}
```

## Determinate vs Indeterminate

### When to Use Indeterminate

Use indeterminate when:
- Total duration is unknown
- Progress cannot be measured
- Initial loading phase
- Waiting for external response

**Example:**
```kotlin
// Waiting for server response
@Composable
fun WaitingForServer() {
    LoadingIndicator() // Duration unknown
}
```

### When to Use Determinate

Use determinate when:
- Progress can be measured (0-100%)
- User benefits from seeing progress
- Operation takes significant time (> 5 seconds)
- Multiple steps with known completion

**Example:**
```kotlin
// File download with known size
@Composable
fun FileDownload(downloadProgress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(progress = { downloadProgress })
        Text("${(downloadProgress * 100).toInt()}% Complete")
    }
}
```

### Transitioning States

Start indeterminate, transition to determinate when progress becomes measurable:

```kotlin
@Composable
fun SmartLoadingIndicator(
    isProgressKnown: Boolean,
    progress: Float
) {
    if (isProgressKnown) {
        LoadingIndicator(progress = { progress })
    } else {
        LoadingIndicator() // Indeterminate
    }
}
```

## Custom Polygon Sequences

### Brand Expression

Custom polygons can reinforce brand identity:

```kotlin
// Example: Tech brand with geometric shapes
val techBrandPolygons = listOf(
    RoundedPolygon(numVertices = 3, rounding = CornerRounding(0.2f)), // Triangle
    RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.2f)), // Square
    RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.2f))  // Hexagon
)

LoadingIndicator(polygons = techBrandPolygons)
```

### Guidelines for Custom Polygons

1. **Minimum 2 polygons** - Required for morphing
2. **3-6 polygons recommended** - Sweet spot for smooth transitions
3. **Similar complexity** - Polygons should have similar vertex counts
4. **Consistent rounding** - Use similar corner rounding values
5. **Test thoroughly** - Ensure smooth morphing between all shapes

## Accessibility

### Screen Reader Announcements

Provide clear, descriptive labels:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessibleLoading() {
    LoadingIndicator(
        modifier = Modifier.semantics {
            contentDescription = "Loading your photos from the gallery"
            role = Role.ProgressIndicator
        }
    )
}
```

### Motion Sensitivity

Respect user motion preferences:

```kotlin
@Composable
fun MotionAwareLoading() {
    val context = LocalContext.current
    val prefersReducedMotion = remember {
        val resolver = context.contentResolver
        Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        ) == 0f
    }

    if (prefersReducedMotion) {
        // Use simpler alternative
        CircularProgressIndicator()
    } else {
        LoadingIndicator()
    }
}
```

### Progress Announcements

For determinate indicators, announce progress at intervals:

```kotlin
@Composable
fun AnnouncedProgress(progress: Float) {
    val progressPercent = (progress * 100).toInt()
    
    LoadingIndicator(
        progress = { progress },
        modifier = Modifier.semantics {
            contentDescription = "Upload progress: $progressPercent percent"
            stateDescription = when {
                progressPercent < 25 -> "Just started"
                progressPercent < 50 -> "Quarter complete"
                progressPercent < 75 -> "Half complete"
                progressPercent < 100 -> "Almost done"
                else -> "Complete"
            }
        }
    )
}
```

## Common Patterns

### Pattern 1: Splash Screen

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(48.dp))
            LoadingIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Loading MKM...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
```

### Pattern 2: Content Loading State

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContentLoadingState(
    isLoading: Boolean,
    content: @Composable () -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    } else {
        content()
    }
}
```

### Pattern 3: Upload/Download Progress

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UploadProgress(
    fileName: String,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoadingIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Uploading $fileName",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${(animatedProgress * 100).toInt()}% Complete",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

## Do's and Don'ts

### ✅ Do's

- **Do** use Loading Indicator for prominent, full-screen loading states
- **Do** provide clear context about what's loading
- **Do** center the indicator in its container
- **Do** use appropriate sizing for the context
- **Do** animate progress changes smoothly
- **Do** respect user motion preferences
- **Do** ensure sufficient color contrast
- **Do** provide accessibility labels
- **Do** use custom polygons to reinforce brand identity (when appropriate)

### ❌ Don'ts

- **Don't** use for quick operations (< 500ms)
- **Don't** show multiple Loading Indicators simultaneously
- **Don't** use in small, confined spaces (use CircularProgressIndicator instead)
- **Don't** use for background tasks (too prominent)
- **Don't** forget to provide loading context
- **Don't** use jarring or overly complex polygon sequences
- **Don't** ignore accessibility requirements
- **Don't** use with low contrast colors

## Performance Considerations

### Efficient Usage

```kotlin
// Good: Single indicator for grouped operations
@Composable
fun EfficientLoading(isLoading: Boolean) {
    if (isLoading) {
        LoadingIndicator()
    }
}

// Bad: Recreating indicator unnecessarily
@Composable
fun InefficientLoading(isLoading: Boolean) {
    if (isLoading) {
        repeat(10) {
            LoadingIndicator() // Wasteful!
        }
    }
}
```

### Memory Management

```kotlin
// Good: Remember custom polygons
val customPolygons = remember {
    listOf(
        RoundedPolygon(numVertices = 4),
        RoundedPolygon(numVertices = 6)
    )
}
LoadingIndicator(polygons = customPolygons)

// Bad: Recreating polygons every composition
LoadingIndicator(
    polygons = listOf( // Recreated every time!
        RoundedPolygon(numVertices = 4),
        RoundedPolygon(numVertices = 6)
    )
)
```

## Related Documentation

- [Loading Indicator - Overview](./overview.md)
- [Loading Indicator - Implementation](./implementation.md)
- [Progress Indicators - Guidelines](../progress-indicators/guidelines.md)

## References

- [Material Design 3 - Loading Indicator Guidelines](https://m3.material.io/components/loading-indicator/guidelines)
- [Material Design 3 - Loading Indicator Accessibility](https://m3.material.io/components/loading-indicator/accessibility)
