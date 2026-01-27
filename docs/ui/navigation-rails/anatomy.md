# Navigation Rails - Anatomy

This document details the structural elements of navigation rails.

## Navigation Rail Elements

A navigation rail consists of up to 9 elements:

1. **Container**
2. **Menu** (optional)
3. **FAB or Extended FAB** (optional)
4. **Icon**
5. **Active indicator**
6. **Label text**
7. **Large badge** (optional)
8. **Large badge label** (optional)
9. **Small badge** (optional)

## Container

The container is the foundational element of the navigation rail.

### Placement
- Should be placed on the leading edge of the window
- Left side for left-to-right languages
- Right side for right-to-left languages

### Styling
- Container fill can be turned off for direct surface appearance
- When disabled, ensure minimum 3:1 color contrast for all items
- Always runs vertically along the side of a layout
- Never make it horizontal (use navigation bar instead)

### Alignment
- Items can be aligned as a group to **top** or **center**
- Use center alignment on tablets for easier reach
- Menu icon and FAB should always be top-aligned

## Menu (Optional)

The menu button controls the expansion state of the navigation rail.

### Functionality
- Transitions between collapsed and expanded navigation rails
- Can reveal secondary destinations when expanded
- Icon should change to represent collapse capability when expanded

### States
- **Collapsed**: Shows menu/expand icon
- **Expanded**: Shows close/collapse icon

## Floating Action Button (Optional)

The FAB provides quick access to the app's primary action.

### Placement
- Should be anchored to the top of the screen
- Places app's key action above navigation destinations
- Must be top-aligned
- Avoid placing below navigation items

### Behavior
- When nested within navigation rail, resting elevation should be level 0
- Transitions to extended FAB when rail expands

### Alternative Uses
- Top of rail can also hold a logo
- Avoid logos that could be mistaken as buttons
- Don't use logo as menu button to expand navigation rail

## Active Indicator

The active indicator shows which page is currently being displayed.

### Appearance
- Shows only for the current open page
- Fills and changes color when destination is selected
- Hugs the label text in expanded nav rail
- Target area always spans the full width

### Customization
- Default: Hugs contents in expanded nav rail
- Override to fill container to resemble deprecated navigation drawer

## Icons

Icons must symbolize the content of their destination page.

### Requirements
- Must use icons that clearly represent page content
- Browse popular icons on [Google Fonts](https://fonts.google.com/icons)
- Should be clear and meaningful representations

### States
- **Unselected**: Outlined icon style
- **Selected**: Filled icon style with color change
- Active indicator appears behind selected icons

## Label Text

Label text provides a textual description of each destination.

### Requirements
- All navigation items require label text
- Should be short, meaningful descriptions
- Ideally one word per label

### Guidelines
- Write clear and concise labels
- Avoid wrapping long labels when possible
- If necessary, create line break between words or hyphenate
- Don't truncate or use ellipsis
- Don't reduce type size to fit more characters

### Examples

**Good:**
- "Home"
- "Search"
- "Settings"
- "Favorites"

**Avoid:**
- "Go to Home Screen" (too long)
- "Sett..." (truncated)
- Tiny text to fit long phrases

## Badges

Badges communicate dynamic information about destinations.

### Types
1. **Small badge**: Simple notification indicator
2. **Large badge with number**: Shows count (e.g., "5")
3. **Large badge with maximum character count**: Shows "99+" for large numbers

### Placement
- **Collapsed nav rails**: Upper right corner of icon
- **Expanded nav rails**: Next to label text

### Use Cases
- Unread message counts
- Notification counts
- Status indicators
- Dynamic updates

## Divider (Optional)

A vertical divider can separate the rail from app content.

### Placement
- Positioned on the edge of rail container adjacent to app content
- Helps distinguish navigation rail from other on-screen content

### When to Use
- When rail container needs visual separation
- To improve clarity of navigation boundaries
- When rail and content have similar colors

## Color Scheme

Navigation rail uses the following Material Design 3 color roles:

### Light and Dark Schemes
- Surface container (optional)
- On secondary container
- Secondary container
- Secondary
- On surface variant
- Error
- On error

## States

Navigation rails support four interaction states:

1. **Enabled** - Default state, ready for interaction
2. **Hovered** - Mouse cursor over item
3. **Focused** - Keyboard focus on item
4. **Pressed** - Active touch/click

**Important**: The navigation item's target area always spans the full width of the nav rail, even if the item container hugs its contents.

---

*See also: [Overview](overview.md) | [Usage Guidelines](usage.md) | [Best Practices](best-practices.md)*
