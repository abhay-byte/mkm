# Loading Indicator - Implementation Guide

This guide provides practical examples and best practices for implementing the Material 3 Loading Indicator in Jetpack Compose.

## Prerequisites

### Dependency

The Loading Indicator is available in Material 3 version `1.5.0-alpha12` and later:

```kotlin
implementation("androidx.compose.material3:material3:1.5.0-alpha12")
```

### API Annotation

Loading Indicator is part of the Expressive API and requires the experimental annotation:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

## Basic Usage

### Indeterminate Loading Indicator

The simplest form continuously morphs between default shapes:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BasicLoadingExample() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator()
    }
}
```

### With Custom Color

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColoredLoadingIndicator() {
    LoadingIndicator(
        color = MaterialTheme.colorScheme.primary
    )
}
```

### With Custom Size

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SizedLoadingIndicator() {
    LoadingIndicator(
        modifier = Modifier.size(64.dp),
        color = MaterialTheme.colorScheme.tertiary
    )
}
```

## Determinate Loading Indicator

Shows progress from 0.0 to 1.0 by morphing through shapes:

```kotlin
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeterminateLoadingExample() {
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow,
            visibilityThreshold = 1 / 1000f,
        ),
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(progress = { animatedProgress })
        Spacer(Modifier.requiredHeight(30.dp))
        Text("Set loading progress:")
        Slider(
            modifier = Modifier.width(300.dp),
            value = progress,
            valueRange = 0f..1f,
            onValueChange = { progress = it },
        )
    }
}
```

## Real-World Examples

### File Upload with Progress

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileUploadScreen(uploadProgress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = uploadProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        )
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(80.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "${(uploadProgress * 100).toInt()}% Uploaded",
            style = MaterialTheme.typography.titleLarge
        )
    }
}
```

### Splash Screen Loading

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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // App logo or branding
            Icon(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(48.dp))
            LoadingIndicator(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

### Processing State in Content

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContentWithLoading(isProcessing: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isProcessing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        // Rest of your content
    }
}
```

## Custom Polygon Sequences

### Creating Custom Shapes

You can provide custom `RoundedPolygon` sequences for unique brand experiences:

```kotlin
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomShapeLoadingIndicator() {
    val customPolygons = remember {
        listOf(
            RoundedPolygon.star(
                numVerticesPerRadius = 6,
                radius = 1f,
                rounding = CornerRounding(0.1f)
            ),
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                radius = 1f,
                rounding = CornerRounding(0.2f)
            ),
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.3f)
            )
        )
    }

    LoadingIndicator(
        polygons = customPolygons,
        color = MaterialTheme.colorScheme.secondary
    )
}
```

## API Reference

### Indeterminate LoadingIndicator

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
): Unit
```

**Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `modifier` | `Modifier` | `Modifier` | Modifier to be applied to the loading indicator |
| `color` | `Color` | `LoadingIndicatorDefaults.indicatorColor` | The loading indicator's color |
| `polygons` | `List<RoundedPolygon>` | `LoadingIndicatorDefaults.IndeterminateIndicatorPolygons` | List of polygons to morph between (minimum 2 required) |

**Throws:**
- `IllegalArgumentException` if the polygons list holds less than two items

### Determinate LoadingIndicator

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun LoadingIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.DeterminateIndicatorPolygons
): Unit
```

**Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `progress` | `() -> Float` | - | Progress value (0.0 to 1.0), values outside are coerced |
| `modifier` | `Modifier` | `Modifier` | Modifier to be applied to the loading indicator |
| `color` | `Color` | `LoadingIndicatorDefaults.indicatorColor` | The loading indicator's color |
| `polygons` | `List<RoundedPolygon>` | `LoadingIndicatorDefaults.DeterminateIndicatorPolygons` | List of polygons to morph between based on progress (minimum 2 required) |

**Throws:**
- `IllegalArgumentException` if the polygons list holds less than two items

## Best Practices

### 1. Use Appropriate Sizing

```kotlin
// Good: Reasonable sizes for different contexts
LoadingIndicator(modifier = Modifier.size(48.dp))  // Default
LoadingIndicator(modifier = Modifier.size(64.dp))  // Prominent
LoadingIndicator(modifier = Modifier.size(80.dp))  // Splash screen

// Avoid: Too small or too large
LoadingIndicator(modifier = Modifier.size(16.dp))  // Too small
LoadingIndicator(modifier = Modifier.size(200.dp)) // Too large
```

### 2. Animate Progress Changes

Always animate progress updates for smooth transitions:

```kotlin
// Good: Animated progress
val animatedProgress by animateFloatAsState(
    targetValue = currentProgress,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow
    )
)
LoadingIndicator(progress = { animatedProgress })

// Avoid: Direct progress updates (will be jerky)
LoadingIndicator(progress = { currentProgress })
```

### 3. Center Alignment

Loading indicators should typically be centered:

```kotlin
// Good: Centered
Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    LoadingIndicator()
}

// Avoid: Off-center without reason
Row(modifier = Modifier.fillMaxWidth()) {
    LoadingIndicator() // Left-aligned
}
```

### 4. Provide Context

Always provide context about what's loading:

```kotlin
// Good: Clear context
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    LoadingIndicator()
    Spacer(Modifier.height(16.dp))
    Text("Loading your profile...")
}

// Avoid: No context
LoadingIndicator() // User doesn't know what's loading
```

### 5. Respect Motion Preferences

Consider users with motion sensitivity:

```kotlin
val prefersReducedMotion = // Check system preference

if (prefersReducedMotion) {
    // Use simpler indicator or static alternative
    CircularProgressIndicator()
} else {
    LoadingIndicator()
}
```

## Common Mistakes

### ❌ Mistake 1: Using for Quick Operations

```kotlin
// Bad: For operations < 200ms
if (isValidating) {
    LoadingIndicator() // Too prominent for quick validation
}
```

**Solution:** Use for operations that take noticeable time (> 500ms)

### ❌ Mistake 2: Multiple Indicators

```kotlin
// Bad: Multiple loading indicators
Column {
    LoadingIndicator() // For image 1
    LoadingIndicator() // For image 2
    LoadingIndicator() // For image 3
}
```

**Solution:** Use a single indicator for grouped operations

### ❌ Mistake 3: No Minimum Polygon Count

```kotlin
// Bad: Single polygon
LoadingIndicator(polygons = listOf(singlePolygon)) // Throws exception
```

**Solution:** Always provide at least 2 polygons

## Accessibility

### Screen Reader Support

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessibleLoadingIndicator(loadingMessage: String) {
    LoadingIndicator(
        modifier = Modifier.semantics {
            contentDescription = loadingMessage
            role = Role.ProgressIndicator
        }
    )
}
```

### Usage Example

```kotlin
AccessibleLoadingIndicator(loadingMessage = "Uploading your photo")
```

## Related Documentation

- [Loading Indicator - Overview](./overview.md)
- [Loading Indicator - Guidelines](./guidelines.md)
- [Progress Indicators](../progress-indicators/overview.md)

## References

- [Android Developers - LoadingIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#LoadingIndicator(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Color,kotlin.collections.List))
- [Material Design 3 - Loading Indicator](https://m3.material.io/components/loading-indicator/overview)
