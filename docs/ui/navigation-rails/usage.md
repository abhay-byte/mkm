# Navigation Rails - Usage Guidelines

This document provides guidance on when and how to use navigation rails effectively.

## Placement

### Window Edge
- Place outside any panes, along the leading edge of the window
- Don't place within body content
- Leading edge is left for LTR languages, right for RTL languages

### With Other Components
- Can be used alongside tabs for extra navigation layer
- Never use navigation rail and navigation bar simultaneously
- When hidden, body content can fill remaining space if menu icon stays accessible

## Adaptive Design

### Resizing Behavior

Navigation rails should adapt to different screen sizes:

#### Screen Size Transitions
- **Large → Small**: Navigation rail transforms into navigation bar
- Provides same quick access in easier-to-use configuration
- Never use both simultaneously

#### Window Size Guidelines

**Compact Windows**
- Don't use standard navigation rail
- Use navigation bar instead
- Space constraints make rail impractical

**Medium Windows**
- Use navigation rail, especially for persistent vertical navigation
- Prioritize vertical navigation over maximizing content space
- If fewer than 5 destinations, consider navigation bar

**Expanded to Extra-Large Windows**
- Use navigation rail (not navigation bar)
- Consider available horizontal space
- Choose between standard and modal based on destination count

### Presentation

#### Collapse to Expand Transition
- Page contents should adjust automatically
- FAB transitions to extended FAB
- Extra destinations can be revealed
- Smooth, animated transition

#### Standard Expanded Rail
- Use when there are secondary destinations or actions
- Lower priority items than main navigation
- Sufficient horizontal space available

## Common Layouts

### Basic Configurations

1. **Three navigation items**
   - Minimal navigation setup
   - Clean, focused interface

2. **Three navigation items with menu**
   - Enables expansion to reveal more destinations
   - Collapsible interface

3. **Three navigation items with FAB**
   - Primary action always accessible
   - Action-focused navigation

4. **Three navigation items with menu and FAB**
   - Full-featured navigation
   - Expandable with primary action

## Responsive Considerations

### Tablet Optimization
- Use center alignment for easier thumb reach
- Consider touch target sizes
- Optimize for both portrait and landscape orientations

### Desktop Optimization
- Can remain expanded by default
- Takes advantage of available horizontal space
- Supports mouse and keyboard interactions

### Horizontal Space Management
- Standard expanded rail for spacious layouts
- Modal expanded rail for information-dense layouts
- Consider content priority when choosing configuration

## Navigation Hierarchy

### Primary Navigation
- 3-7 main destinations in collapsed rail
- Always visible and accessible
- Represent top-level sections

### Secondary Navigation
- Revealed in expanded rail
- Lower priority than primary destinations
- Accessible through menu button

### Tertiary Navigation
- Use tabs or other components
- Don't overload navigation rail
- Maintain clear hierarchy

## Multi-Pane Layouts

### With Panes
- Place rail outside panes
- Rail controls which pane content is shown
- Maintains consistent position

### With Master-Detail
- Rail selects master category
- Detail pane shows selected content
- Works well on tablets and desktop

## Immersive Experiences

### Full-Screen Content
- Expanded navigation rail can be hidden entirely
- Appears only when menu icon is selected
- Use for:
  - Video players
  - Image galleries
  - Reading modes
  - Gaming interfaces

### Collapsed Rail Visibility
- Collapsed navigation rail should not be hidden
- Always maintain access to navigation
- Use with divider or elevation for distinction

## Integration with Other Components

### With Tabs
- Navigation rail for top-level navigation
- Tabs for secondary navigation within section
- Creates clear two-level hierarchy

### With Bottom Sheets
- Modal bottom sheets can appear over rail
- Rail remains visible beneath
- Maintains navigation context

### With App Bars
- Top app bar can contain contextual actions
- Rail provides navigation
- Clear separation of concerns

## Performance Considerations

### Loading States
- Show skeleton or placeholder during load
- Maintain layout stability
- Don't shift rail position when content loads

### Smooth Transitions
- Use appropriate animation durations
- Maintain 60fps during expand/collapse
- Test on target devices

---

*See also: [Overview](overview.md) | [Behavior](behavior.md) | [Implementation](implementation.md)*
