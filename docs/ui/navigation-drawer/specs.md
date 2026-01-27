# Navigation Drawer - Specifications

This document provides detailed specifications for implementing navigation drawers according to Material Design 3 guidelines.

## Anatomy

Navigation drawers are essentially a list contained within a side sheet. They can also include headers, subheads, and dividers to organize longer lists.

### Components

1. **Container** - The sheet that holds all navigation drawer elements
2. **Headline** - Optional title for the drawer
3. **Label text** - Text describing each destination
4. **Icon** - Optional visual indicator for destinations
5. **Active indicator** - Background shape showing current destination
6. **Badge label text** - Optional notification badges
7. **Scrim** - Semi-transparent overlay (modal drawer only)

## Design Tokens & Color

### Color Roles

Navigation drawer color roles used for light and dark schemes:

| Element | Color Role |
|---------|-----------|
| Container | Surface container low |
| Section label | On surface variant |
| Label (inactive) | On surface variant |
| Label (active) | On secondary container |
| Active indicator | Secondary container |
| Icon (active) | On secondary container |
| Icon (inactive) | On surface variant |
| Badge | On surface variant |
| Scrim | Scrim |

For divider color roles, refer to the divider specs.

## States

States are visual representations used to communicate the status of a component or interactive element.

Navigation drawer states:
- **Enabled** - Default state
- **Hovered** - Mouse over the item
- **Focused** - Keyboard or accessibility focus
- **Pressed** - Active press/tap

## Measurements

### Standard Navigation Drawer

#### Element Sizes

| Attribute | Value |
|-----------|-------|
| Container height | 100% |
| Container width | 360dp |
| Container shape | 0,16,16,0dp corner radii |
| Icon size | 24dp |
| Active indicator height | 56dp |
| Active indicator shape | 28dp corner radius |
| Active indicator width | 336dp |

#### Spacing

| Attribute | Value |
|-----------|-------|
| Horizontal label alignment | Start-aligned |
| Left padding | 28dp |
| Right padding | 28dp |
| Active indicator padding | 12dp |
| Padding between elements | 0dp |

### Modal Navigation Drawer

#### Element Sizes

| Attribute | Value |
|-----------|-------|
| Container height | 100% |
| Container width | 360dp |
| Icon size | 24dp |
| Active indicator height | 56dp |
| Active indicator shape | 28dp corner radius |
| Active indicator width | 336dp |

#### Spacing

| Attribute | Value |
|-----------|-------|
| Horizontal label alignment | Start-aligned |
| Left padding | 28dp |
| Right padding | 28dp |
| Active indicator padding | 12dp |
| Padding between elements | 0dp |

## Implementation Notes

- Navigation drawers should always open from the **start edge** of the screen
  - Left side for left-to-right (LTR) languages
  - Right side for right-to-left (RTL) languages
- Use full-width dividers to separate groups of destinations
- Don't use dividers to separate individual destinations

## Accessibility

- Ensure sufficient color contrast between text and background
- Provide meaningful content descriptions for icons
- Support keyboard navigation
- Maintain focus management when opening/closing drawer
