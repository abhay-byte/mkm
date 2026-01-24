# Loading Indicator - Overview

The **Loading Indicator** is a Material Design 3 component that provides an expressive, animated loading experience by morphing between various shapes. It's part of the Material 3 Expressive API and offers a modern alternative to traditional circular and linear progress indicators.

## What is a Loading Indicator?

A Loading Indicator is an animated component that morphs between different polygon shapes to indicate that a process is ongoing. Unlike traditional progress indicators that use circular or linear animations, the Loading Indicator uses shape morphing to create a more engaging and dynamic visual experience.

![Loading indicator](https://m3.material.io/components/loading-indicator/assets/loading-indicator.png)

## Key Features

- **Shape Morphing**: Continuously morphs between customizable polygon shapes
- **Determinate & Indeterminate**: Supports both progress-based and continuous animations
- **Customizable**: Configure custom polygon sequences for unique brand experiences
- **Expressive**: Part of Material 3's expressive design system for more personality
- **Smooth Animations**: Uses advanced morphing algorithms for fluid transitions

## When to Use

### Use Loading Indicator When:

- ✅ You want a more expressive, branded loading experience
- ✅ The loading process is the primary focus of the screen
- ✅ You need to create a memorable, engaging wait experience
- ✅ Your app uses Material 3 Expressive design language
- ✅ You want to reduce perceived wait time with engaging animations

### Use Traditional Progress Indicators When:

- ❌ Loading is a background process (use subtle indicators)
- ❌ You need a compact indicator for small spaces (use CircularProgressIndicator)
- ❌ Loading happens frequently and should be unobtrusive
- ❌ You need to show progress across a wide area (use LinearProgressIndicator)

## Variants

### Indeterminate Loading Indicator

Continuously morphs between shapes as long as the loading process is active. Use when the duration or progress is unknown.

**Best for:**
- Initial app loading
- Waiting for server responses
- Processing without measurable progress
- Background operations

### Determinate Loading Indicator

Morphs between shapes based on a progress value (0.0 to 1.0). The shape transformation corresponds to the completion percentage.

**Best for:**
- File uploads/downloads
- Multi-step processes with known progress
- Operations where users benefit from seeing exact progress
- Installations or updates

## Comparison with Other Progress Indicators

| Feature | Loading Indicator | Circular Indicator | Linear Indicator |
|---------|------------------|-------------------|------------------|
| **Visual Style** | Shape morphing | Rotating arc | Horizontal bar |
| **Space Required** | Medium (48dp+) | Small (24-48dp) | Wide (full width) |
| **Attention Level** | High | Medium | Low |
| **Best Use Case** | Primary loading | Component loading | Page/background loading |
| **Customization** | High (custom polygons) | Medium (colors) | Medium (colors) |
| **API Level** | Expressive (new) | Standard | Standard |

## Design Principles

### 1. Purposeful Animation

The morphing animation should feel purposeful and smooth, not distracting or jarring. The default polygon sequence is carefully designed to create a pleasing visual rhythm.

### 2. Brand Expression

Custom polygon sequences allow you to create unique loading experiences that align with your brand identity while maintaining Material Design principles.

### 3. Performance

Despite the complex morphing animations, Loading Indicators are optimized for smooth performance across devices.

### 4. Accessibility

Loading Indicators maintain proper accessibility support with screen reader announcements and respect for reduced motion preferences.

## Related Components

- [Circular Progress Indicator](../progress-indicators/overview.md) - For compact, traditional loading states
- [Linear Progress Indicator](../progress-indicators/overview.md) - For wide, page-level loading states
- [Linear Wavy Progress Indicator](../progress-indicators/overview.md) - For expressive linear loading

## References

- [Material Design 3 - Loading Indicator](https://m3.material.io/components/loading-indicator/overview)
- [Android Developers - LoadingIndicator API](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#LoadingIndicator(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Color,kotlin.collections.List))
