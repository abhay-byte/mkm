# Cards – Material Design 3

Complete documentation for implementing Material Design 3 cards in your application.

## Overview

Cards display content and actions about a single subject. They are versatile containers that group related elements and provide a clear visual boundary for content.

### Key Features

- **Three card types**: Elevated, Filled, and Outlined
- **Flexible content**: Images, text, buttons, lists, and more
- **Adaptive layouts**: Responsive to different screen sizes
- **Accessible**: Full keyboard and screen reader support

---

## Documentation Structure

This documentation is organized into the following sections:

### 📋 [Overview](overview.md)
Introduction to Material Design 3 cards, including:
- Card types and their use cases
- Content flexibility
- Differences from Material 2
- Platform availability

### 📐 [Specifications](specs.md)
Technical specifications for implementing cards:
- Design tokens and color roles
- Interaction states (hover, focus, pressed, dragged, disabled)
- Measurements and dimensions
- Shape and padding guidelines

### 📖 [Guidelines](guidelines.md)
Best practices and usage patterns:
- When to use cards vs other containers
- Anatomy and content organization
- Media, text, and action placement
- Card collections (grids, lists, carousels)
- Adaptive design for different screen sizes
- Behavior and gestures

### ♿ [Accessibility](accessibility.md)
Making cards accessible to all users:
- Keyboard navigation
- Screen reader support
- Touch target sizes
- Focus management
- Interaction patterns for assistive technology

### 💻 [Implementation](implementation.md)
Jetpack Compose implementation guide:
- Basic and advanced examples
- All three card types (Filled, Elevated, Outlined)
- Clickable cards
- Cards with images
- Swipe to dismiss
- Scrollable content

---

## Quick Start

### Basic Card

```kotlin
@Composable
fun BasicCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Hello, Card!",
            modifier = Modifier.padding(16.dp)
        )
    }
}
```

### Card Types

#### Filled Card (Default)
```kotlin
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    )
) { /* Content */ }
```

#### Elevated Card
```kotlin
ElevatedCard(
    elevation = CardDefaults.cardElevation(
        defaultElevation = 6.dp
    )
) { /* Content */ }
```

#### Outlined Card
```kotlin
OutlinedCard(
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
) { /* Content */ }
```

---

## Card Types Comparison

| Type | Elevation | Border | Best For |
|------|-----------|--------|----------|
| **Filled** | None | None | Default choice, subtle separation |
| **Elevated** | Drop shadow | None | Moderate emphasis, floating appearance |
| **Outlined** | None | Visible border | Maximum separation, high contrast |

---

## Key Specifications

### Dimensions
- **Corner radius**: 12dp
- **Internal padding**: 16dp (left/right)
- **Card spacing**: 8dp max between cards

### Color Tokens

| Card Type | Surface Token | Additional Tokens |
|-----------|---------------|-------------------|
| Filled | `surfaceContainerHighest` | - |
| Elevated | `surfaceContainerLow` | Elevation: level 1 |
| Outlined | `surface` | Outline: `outlineVariant` |

### States
All card types support:
- Enabled (default)
- Hovered
- Focused
- Pressed
- Dragged
- Disabled

---

## Common Use Cases

### Product Card
```kotlin
@Composable
fun ProductCard(product: Product) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        onClick = { /* Navigate to product details */ }
    ) {
        Column {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.price,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
```

### News Article Card
```kotlin
@Composable
fun NewsCard(article: Article) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = article.headline,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = article.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { /* Read more */ }) {
                    Text("Read More")
                }
            }
        }
    }
}
```

---

## Best Practices

### ✅ Do

- Use cards to display content and actions on a single topic
- Maintain clear visual hierarchy with text and images
- Group related cards in collections (grid, list, or carousel)
- Ensure sufficient contrast for text on images
- Provide keyboard and screen reader support
- Use appropriate card type based on emphasis needed

### ❌ Don't

- Force content into cards when simpler layouts would work
- Stack actionable elements (don't put buttons on clickable cards)
- Let cards internally scroll on mobile (expand instead)
- Place text on images without ensuring contrast
- Extend cards across entire width on large screens

---

## Accessibility Checklist

- [ ] All interactive elements are keyboard accessible
- [ ] Focus order is logical and follows visual layout
- [ ] Focus indicators are clearly visible
- [ ] Touch targets meet minimum size (48dp)
- [ ] Decorative images are hidden from screen readers
- [ ] Actionable elements have appropriate labels
- [ ] Drag/swipe actions have single-pointer alternatives

---

## Card Collections

### Grid Layout
```kotlin
@Composable
fun CardGrid(items: List<Item>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Card content
            }
        }
    }
}
```

### Vertical List
```kotlin
@Composable
fun CardList(items: List<Item>) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Card content
            }
        }
    }
}
```

### Horizontal Carousel
```kotlin
@Composable
fun CardCarousel(items: List<Item>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier.width(300.dp)
            ) {
                // Card content
            }
        }
    }
}
```

---

## Adaptive Design

Cards should adapt to different screen sizes:

### Compact (< 600dp)
- Full-width cards in vertical lists
- Single column layouts
- Consider using lists instead of cards for very compact screens

### Medium (600dp - 840dp)
- 2-3 column grids
- Horizontal carousels for featured content

### Expanded (> 840dp)
- Multi-column grids (3-4 columns)
- Larger card sizes with more content
- Horizontal orientation for some cards

---

## Additional Resources

### Material Design 3
- [Cards Overview](https://m3.material.io/components/cards/overview)
- [Cards Specs](https://m3.material.io/components/cards/specs)
- [Cards Guidelines](https://m3.material.io/components/cards/guidelines)
- [Cards Accessibility](https://m3.material.io/components/cards/accessibility)

### Android Documentation
- [Compose Card Reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#Card(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Shape,androidx.compose.material3.CardColors,androidx.compose.material3.CardElevation,androidx.compose.foundation.BorderStroke,kotlin.Function1))
- [Material Design 3 in Compose](https://developer.android.com/jetpack/compose/designsystems/material3)

---

## Related Components

- **Lists** – For simpler, more compact content organization
- **Buttons** – For actions within cards
- **Images** – For visual content in cards
- **Chips** – For tags and filters in cards
- **Dividers** – For separating content within cards

---

*Last updated: January 2026*
