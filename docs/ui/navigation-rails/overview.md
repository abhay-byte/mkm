# Navigation Rails - Overview

Navigation rails let people switch between UI views on mid-sized devices. They provide access to primary destinations in apps when using tablet and desktop screens.

## What is a Navigation Rail?

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

## When to Use

- Medium to extra large window sizes (tablets and desktop)
- When you need persistent vertical navigation
- For 3–7 primary destinations
- When space allows for dedicated navigation area

## When Not to Use

- Compact windows (use navigation bar instead)
- When you have more than 7 destinations without secondary grouping
- Horizontal navigation needs (use navigation bar)

## Configurations

### Expanded Layout Options

**Standard (default)**
- Available as navigation drawer in Original M3
- Available in M3 Expressive

**Modal**
- Available as navigation drawer in Original M3
- Available in M3 Expressive

**Expanded Behavior**
- Hide when collapsed (M3 Expressive only)

---

*See also: [Anatomy](anatomy.md) | [Usage Guidelines](usage.md) | [Implementation](implementation.md)*
