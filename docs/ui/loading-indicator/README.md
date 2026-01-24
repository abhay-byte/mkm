# Loading Indicator Documentation

Complete documentation for the Material 3 Loading Indicator component.

## Overview

The **Loading Indicator** is an expressive Material Design 3 component that morphs between polygon shapes to create engaging loading animations. It's part of the Material 3 Expressive API and provides both determinate and indeterminate variants.

## Quick Start

```kotlin
// Add dependency
implementation("androidx.compose.material3:material3:1.5.0-alpha12")

// Basic usage
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyLoadingScreen() {
    LoadingIndicator()
}
```

## Documentation Structure

### 📖 [Overview](./overview.md)
Introduction to Loading Indicator, key features, and when to use it.

**Topics covered:**
- What is a Loading Indicator
- Key features and benefits
- When to use vs traditional progress indicators
- Variants (determinate vs indeterminate)
- Comparison with other progress indicators

### 💻 [Implementation Guide](./implementation.md)
Practical code examples and API reference.

**Topics covered:**
- Prerequisites and setup
- Basic usage examples
- Determinate and indeterminate variants
- Real-world implementation patterns
- Custom polygon sequences
- Complete API reference
- Best practices and common mistakes

### 📐 [Guidelines](./guidelines.md)
Design principles and best practices.

**Topics covered:**
- When to use Loading Indicator
- Design principles and patterns
- Sizing and color guidelines
- Placement and spacing
- Accessibility requirements
- Common patterns and examples
- Do's and don'ts

## Quick Reference

### Indeterminate Loading Indicator

Continuously morphs between shapes:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IndeterminateExample() {
    LoadingIndicator(
        modifier = Modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
```

### Determinate Loading Indicator

Shows progress from 0.0 to 1.0:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeterminateExample(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        )
    )
    
    LoadingIndicator(
        progress = { animatedProgress },
        modifier = Modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
```

## Key Concepts

### Shape Morphing

The Loading Indicator smoothly morphs between different polygon shapes, creating a more engaging experience than traditional circular or linear indicators.

### Expressive Design

Part of Material 3's expressive design language, allowing for brand personality while maintaining Material Design principles.

### Customization

Supports custom polygon sequences for unique brand experiences:

```kotlin
val customPolygons = remember {
    listOf(
        RoundedPolygon(numVertices = 3, rounding = CornerRounding(0.2f)),
        RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.2f))
    )
}

LoadingIndicator(polygons = customPolygons)
```

## Common Use Cases

| Use Case | Variant | Example |
|----------|---------|---------|
| **Splash Screen** | Indeterminate | App launch loading |
| **File Upload** | Determinate | Show upload progress |
| **Data Sync** | Indeterminate | Background sync |
| **Content Loading** | Indeterminate | Loading feed/gallery |
| **Multi-step Process** | Determinate | Installation wizard |

## Comparison with Other Indicators

| Feature | Loading Indicator | Circular | Linear |
|---------|------------------|----------|--------|
| **Animation** | Shape morphing | Rotating arc | Sliding bar |
| **Attention** | High | Medium | Low |
| **Size** | 48-80dp | 24-48dp | Full width |
| **Best For** | Primary loading | Component loading | Background loading |

## Requirements

- **Material 3 Version:** 1.5.0-alpha12 or later
- **API Annotation:** `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
- **Minimum Polygons:** 2 (for morphing animation)

## Accessibility

Loading Indicators support:
- ✅ Screen reader announcements
- ✅ Progress state descriptions
- ✅ Reduced motion preferences
- ✅ Proper contrast ratios (minimum 3:1)

```kotlin
LoadingIndicator(
    modifier = Modifier.semantics {
        contentDescription = "Loading your content"
        role = Role.ProgressIndicator
    }
)
```

## Related Components

- **[Circular Progress Indicator](../progress-indicators/overview.md)** - Compact loading indicator
- **[Linear Progress Indicator](../progress-indicators/overview.md)** - Wide, page-level loading
- **[Linear Wavy Progress Indicator](../progress-indicators/overview.md)** - Expressive linear variant

## External Resources

### Official Documentation
- [Material Design 3 - Loading Indicator](https://m3.material.io/components/loading-indicator/overview)
- [Android Developers - LoadingIndicator API](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#LoadingIndicator(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Color,kotlin.collections.List))

### Design Resources
- [Material Design 3 - Guidelines](https://m3.material.io/components/loading-indicator/guidelines)
- [Material Design 3 - Accessibility](https://m3.material.io/components/loading-indicator/accessibility)

## Contributing

Found an issue or have a suggestion? Please update the documentation following these guidelines:

1. Keep examples practical and real-world
2. Include accessibility considerations
3. Provide both good and bad examples
4. Test all code snippets
5. Maintain consistent formatting

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-01-24 | Initial documentation created |

---

**Need help?** Check the [Implementation Guide](./implementation.md) for detailed code examples or the [Guidelines](./guidelines.md) for design best practices.
