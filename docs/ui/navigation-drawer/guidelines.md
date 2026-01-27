# Navigation Drawer - Guidelines

This document provides design and usage guidelines for navigation drawers in Material Design 3.

## Usage Principles

Navigation drawers provide access to destinations and app functionality, such as switching accounts. One navigation destination is always active.

### ✅ Do

- Use a navigation drawer for **5 or more primary destinations**
- Use for apps with **multiple levels of navigation hierarchy**
- Keep permanently visible when users frequently switch destinations
- Use full-width dividers to separate groups of destinations
- Keep text labels concise
- Use recognizable icons when conventions exist

### ❌ Don't

- Don't use two navigation components on the same screen
- Don't use with other primary navigation components (like navigation bar) simultaneously
- Don't use dividers to separate individual destinations
- Don't wrap label text
- Don't shrink text size to fit labels on a single line
- Don't apply icons to some destinations and not others

## Component Selection by Screen Size

Choose navigation components based on window size class:

| Window Size | Recommended Component |
|-------------|----------------------|
| Compact | Navigation bars or modal drawer |
| Medium | Navigation rail + modal drawer |
| Expanded | Navigation rail or standard drawer |
| Large | Standard navigation drawer |
| Extra-large | Standard navigation drawer |

**Important:** On web, when the screen size is smaller than 320 CSS pixels, swap the navigation drawer for a navigation bar to ensure accessibility.

## Anatomy Details

### Sheet

The container that holds all navigation drawer elements. Side sheets are used for both standard and modal navigation drawers.

**Placement:**
- Always on the **start edge** of the screen
- Left for LTR languages
- Right for RTL languages

### Active Indicator

A background shape communicating which destination is currently displayed.

**Best practices:**
- Only one item should have an active indicator at a time
- The indicator should clearly distinguish the active item from others

### Label Text and Icons

Each destination takes the form of an actionable list item with:
- **Required:** Label text
- **Optional:** Icon

**Label text guidelines:**
- Clear and short
- Truncate if extending beyond container width
- Don't wrap to multiple lines
- Don't reduce font size

**Icon guidelines:**
- Use recognizable icons when conventions exist
- Place icons before text
- Apply icons consistently (all or none)
- Reference icons used elsewhere in the app

### Section Labels (Optional)

Short subhead labels can help group related destinations.

**Guidelines:**
- Keep subheads short and descriptive
- Use to organize longer lists
- Separate groups with dividers

### Divider (Optional)

Use dividers to separate groups of destinations within the navigation drawer.

**Guidelines:**
- Use full-width horizontal dividers
- Separate groups, not individual items
- Don't overuse

### Scrim (Modal Only)

Modal drawers use a scrim to block interaction with the rest of the app.

**Properties:**
- Placed directly behind the drawer's sheet
- Can be tapped/clicked to dismiss the drawer
- Semi-transparent overlay

## Responsive Layout

A product's navigation component should change to suit the window size class and form factor.

### Compact Window Size

- Use **modal navigation drawers**
- Can swap for navigation bar on very small screens
- Dismiss via selection, scrim tap, or swipe

### Medium & Expanded Window Sizes

- Use **modal drawer** alone or with **navigation rail**
- When used together, drawer can repeat rail destinations
- Ensure visual separation between navigation levels
- Standard drawer possible in expanded single-pane layouts

### Large and Extra-Large Window Sizes

- Use **standard navigation drawer**
- Can use navigation rail that transitions to modal drawer
- Drawer can be permanently visible or dismissible

### Transitions

Use smooth transitions when swapping components:
- Example: Navigation rail → Standard drawer when rotating to landscape
- Maintain user context during transitions

## Behavior

### Scrolling

- Drawers can be **vertically scrolled**
- Scrolling is **independent** of screen content
- Body content remains **stationary** when drawer scrolls
- Enable scrolling when destination list exceeds drawer height

### Visibility

#### Dismissible Standard Drawers

- Opened/closed via navigation menu icon
- Best for content-focused layouts (e.g., photo gallery)
- Best when users rarely switch destinations
- Remains open until explicitly closed

#### Permanently Visible Standard Drawers

- Always visible on screen
- Cannot be closed or dismissed
- Best for frequently switching between destinations
- Optimal for desktop/large screen experiences

### Opening Modal Drawers

Modal drawers are always opened by an **external action**:
- Tapping navigation menu icon in app bar
- Tapping navigation menu icon in navigation rail
- Swipe gesture from screen edge (if enabled)

### Dismissing Modal Drawers

Modal drawers can be dismissed by:
1. **Selecting a drawer item** - Navigates and closes drawer
2. **Tapping the scrim** - Closes without navigation
3. **Swiping toward anchoring edge** - Gesture dismiss
   - Right-to-left swipe for left-aligned drawer (LTR)
   - Left-to-right swipe for right-aligned drawer (RTL)

### Animations

Navigation drawers use enter and exit transition patterns:
- **Enter:** Slides in from the anchoring edge
- **Exit:** Slides out toward the anchoring edge
- Maintain smooth, consistent animation timing
- Coordinate with scrim fade in/out (modal)

## Best Practices

### Organization

- Group related destinations together
- Use section labels for logical groupings
- Separate groups with dividers
- Place most frequently used items at the top

### Content

- Limit the number of destinations (5-10 is optimal)
- Use clear, concise labels
- Maintain consistent icon style
- Include account switcher at top if applicable

### Interaction

- Provide clear visual feedback on interaction
- Maintain keyboard accessibility
- Support standard gestures (swipe to open/close)
- Close modal drawer after selection

### Context

- Show current location with active indicator
- Persist drawer state when appropriate
- Respect user's preference for drawer visibility
- Don't override user's dismiss actions

## Common Patterns

### Multi-level Navigation

When drawer contains multiple navigation levels:
1. Use section labels to distinguish levels
2. Use dividers to separate level groups
3. Consider indentation for hierarchy
4. Use different icon styles or sizes for levels

### Account Switching

When supporting multiple accounts:
1. Place account switcher at the top
2. Show current account clearly
3. Provide quick access to account management
4. Use avatar/profile picture when available

### Feature Discovery

For complex apps with many features:
1. Organize by user goals or workflows
2. Use clear, descriptive labels
3. Add badges for new features
4. Consider search functionality for many items

## Resources

- [Material Design Navigation Drawer](https://m3.material.io/components/navigation-drawer/overview)
- [Navigation Drawer Specs](./specs.md)
- [Navigation Drawer Implementation](./implementation.md)
