# Cards – Material Design 3

Cards display content and actions about a single subject.

## Overview

Cards are versatile containers that group related elements and provide a clear visual boundary for content. They are one of the most commonly used components in Material Design, offering a flexible way to present information in a digestible format.

### Key Characteristics

- **Contain related elements** – Cards group content and actions about a single subject
- **Flexible content** – Can include images, headlines, supporting text, buttons, lists, and other components
- **Adaptive layout** – Have flexible layouts and dimensions based on their contents
- **Visual hierarchy** – Different card types provide varying levels of emphasis and separation

## Card Types

Material Design 3 provides three distinct card types, each offering different levels of visual emphasis:

### 1. Elevated Card

Elevated cards have a drop shadow, providing more separation from the background than filled cards, but less than outlined cards.

**Use when:**
- You need moderate emphasis
- The card should appear to float above the surface
- You want subtle depth without strong borders

### 2. Filled Card

Filled cards provide subtle separation from the background. This has less emphasis than elevated or outlined cards.

**Use when:**
- You need minimal visual separation
- The content should blend more naturally with the background
- You're working with dense layouts where strong shadows might be overwhelming

### 3. Outlined Card

Outlined cards have a visual boundary around their container. This can provide greater emphasis than the other types.

**Use when:**
- You need clear visual boundaries
- You want maximum separation without shadows
- You're designing for accessibility or high-contrast needs

## Content Flexibility

Cards can contain a wide variety of content types:

- **Images** – Hero images, thumbnails, or background imagery
- **Headlines** – Primary titles and subtitles
- **Supporting text** – Body copy, descriptions, metadata
- **Buttons** – Primary and secondary actions
- **Lists** – Continuous, vertical indexes of text and images
- **Other components** – Chips, icons, dividers, and more

The flexible nature of cards allows them to adapt to different content needs while maintaining consistent visual treatment.

## Availability & Resources

| Type | Resource | Status |
|------|----------|--------|
| **Design** | Design Kit (Figma) | ✅ Available |
| **Implementation** | Flutter | ✅ Available |
| | MDC-Android | ✅ Available |
| | Jetpack Compose | ✅ Available |
| | Web | ❌ Unavailable |

## Differences from Material 2

Material Design 3 cards have evolved from M2 with several key improvements:

### Color

- **New color mappings** – Updated color roles that better align with M3's color system
- **Dynamic color support** – Cards now work seamlessly with dynamic color, which takes a single color from a user's wallpaper or in-app content and creates an accessible color scheme

### Elevation

- **Lower elevation** – Default elevation values have been reduced for a more subtle appearance
- **No shadow by default** – Filled cards (the default type) don't have shadows, creating a cleaner look

### Types

- **Three official types** – M3 formalizes three distinct card types:
  - Elevated (with drop shadow)
  - Filled (subtle separation, default)
  - Outlined (with border)

These changes create cards that are more flexible, accessible, and aligned with modern design principles while maintaining the familiar card pattern from Material 2.

## Next Steps

- **[Specs](specs.md)** – Detailed specifications for implementing cards
- **[Guidelines](guidelines.md)** – Best practices and usage guidelines
- **[Examples](examples.md)** – Code examples and implementation patterns

---

*Based on [Material Design 3 Cards](https://m3.material.io/components/cards)*
