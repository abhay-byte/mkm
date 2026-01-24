# Cards – Specifications

Technical specifications for implementing Material Design 3 cards.

## Tokens & Specs

Material Design 3 cards use design tokens for consistent styling across platforms. Color values, elevations, and dimensions are all implemented through tokens that reference theme values.

> [!NOTE]
> For design, work with color values that correspond with tokens. For implementation, a color value will be a token that references a value. Learn more about [design tokens](https://m3.material.io/foundations/design-tokens).

---

## Elevated Card

### Elements

1. **Container** – The card surface that contains content

### Color Tokens

**Surface Color:**
- Token: `Surface Container Low`
- Used for both light and dark themes
- Provides subtle elevation above the background

### States

Elevated cards support the following interaction states:

| State | Description |
|-------|-------------|
| **Enabled** | Default state |
| **Hovered** | Mouse cursor over the card |
| **Focused** | Card has keyboard/input focus |
| **Pressed** | Card is being actively pressed |
| **Dragged** | Card is being dragged |
| **Disabled** | Card is non-interactive |

---

## Filled Card

### Elements

1. **Container** – The card surface that contains content

### Color Tokens

**Surface Color:**
- Token: `Surface Container Highest`
- Used for both light and dark themes
- Provides minimal separation from background

### States

Filled cards support the following interaction states:

| State | Description |
|-------|-------------|
| **Enabled** | Default state |
| **Hovered** | Mouse cursor over the card |
| **Focused** | Card has keyboard/input focus |
| **Pressed** | Card is being actively pressed |
| **Dragged** | Card is being dragged |
| **Disabled** | Card is non-interactive |

---

## Outlined Card

### Elements

1. **Container** – The card surface that contains content
2. **Outline** – The border around the container

### Color Tokens

**Surface Color:**
- Token: `Surface`
- Used for both light and dark themes

**Outline Color:**
- Token: `Outline Variant`
- Used for both light and dark themes
- Creates visual boundary around the card

### States

Outlined cards support the following interaction states:

| State | Description |
|-------|-------------|
| **Enabled** | Default state |
| **Hovered** | Mouse cursor over the card |
| **Focused** | Card has keyboard/input focus |
| **Pressed** | Card is being actively pressed |
| **Dragged** | Card is being dragged |
| **Disabled** | Card is non-interactive |

---

## Measurements

### Layout Specifications

| Attribute | Value | Description |
|-----------|-------|-------------|
| **Shape** | 12dp | Corner radius for all card types |
| **Left/Right Padding** | 16dp | Internal horizontal padding |
| **Padding Between Cards** | 8dp max | Spacing when cards are adjacent |
| **Label Text Alignment** | Start-aligned | Default text alignment within cards |

### Dimension Guidelines

```
┌─────────────────────────────────────┐
│  ← 16dp padding                     │
│                                     │
│  Card Content                       │
│                                     │
│                              16dp → │
└─────────────────────────────────────┘
      ↓ 8dp max spacing ↓
┌─────────────────────────────────────┐
│  Next Card                          │
└─────────────────────────────────────┘
```

### Shape

- **Corner Radius:** 12dp rounded corners on all card types
- Consistent across elevated, filled, and outlined variants

### Padding

- **Internal Padding:** 16dp on left and right sides
- **Card Spacing:** Maximum 8dp between adjacent cards
- Content should respect these padding values for visual consistency

### Typography

- **Text Alignment:** Start-aligned (left in LTR, right in RTL)
- Use appropriate text styles from Material Design 3 type scale

---

## Implementation Notes

### Jetpack Compose

```kotlin
// Elevated Card
ElevatedCard(
    modifier = Modifier.padding(8.dp)
) {
    // Content with 16dp padding
}

// Filled Card
Card(
    modifier = Modifier.padding(8.dp)
) {
    // Content with 16dp padding
}

// Outlined Card
OutlinedCard(
    modifier = Modifier.padding(8.dp)
) {
    // Content with 16dp padding
}
```

### Design Token Mapping

| Card Type | Surface Token | Additional Tokens |
|-----------|---------------|-------------------|
| Elevated | `surfaceContainerLow` | Elevation: level 1 |
| Filled | `surfaceContainerHighest` | No elevation |
| Outlined | `surface` | Outline: `outlineVariant` |

---

*Based on [Material Design 3 Card Specs](https://m3.material.io/components/cards/specs)*
