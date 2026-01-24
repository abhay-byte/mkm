# Progress Indicators - Guidelines

This document provides best practices and guidelines for implementing Material 3 progress indicators effectively.

## Usage Patterns

### Wait Time Considerations

The choice of progress indicator depends primarily on the expected duration of the task:

| Duration | Recommendation | Indicator Type |
|----------|---------------|----------------|
| **Instant** (<200ms) | Do not show any progress indicator | None |
| **Short** (200ms - 5s) | Use an indeterminate indicator to signal that a process is happening | Circular (preferred) or Linear |
| **Long** (>5s) | Use a determinate indicator to show specific progress and reduce user uncertainty | Linear (preferred) or Circular |

### Consolidation Principle

For a group of activities (e.g., loading multiple images in a gallery), use a **single overall indicator** rather than individual indicators for each item.

**Why?**
- Reduces visual noise
- Provides clearer feedback
- Improves performance
- Simplifies user mental model

### Transitioning States

It is a best practice to start with an **indeterminate** indicator if the total time is unknown and transition to a **determinate** one once the progress can be measured.

**Example:** File download
1. Start with indeterminate (waiting for server response)
2. Transition to determinate once file size is known
3. Show percentage as download progresses

---

## When to Use: Linear vs. Circular

### Linear Indicators

**Best for:**
- Progress that spans a larger area
- Page-level loading states
- Large file transfers
- Multi-step processes

**Ideal placement:**
- Just below a top app bar
- At the top edge of a container
- At the bottom of a header
- Full-width of the screen

**Use when:**
- The process affects the entire page or large section
- You want to show progress without blocking content
- Space allows for a horizontal indicator

### Circular Indicators

**Best for:**
- Smaller, contained spaces
- Short-term indeterminate tasks
- Button loading states
- Card content loading

**Ideal placement:**
- Centered within a view
- Inside buttons
- On cards
- In empty states

**Use when:**
- Space is limited
- The process is localized to a specific component
- You need a compact indicator
- The action is quick (e.g., "refreshing" or "saving")

---

## Do's and Don'ts

### ✅ Do's

- **Do** indicate the status of an overall process when multiple sub-tasks are running
- **Do** use a stop indicator (a small dot at the end of a linear track) for determinate progress if the track contrast is low
- **Do** provide clear context about what is loading
- **Do** use consistent indicator types for the same action throughout your app
- **Do** respect user motion preferences
- **Do** ensure proper contrast ratios (minimum 3:1)
- **Do** provide accessibility labels
- **Do** transition from indeterminate to determinate when possible

### ❌ Don'ts

- **Don't** show multiple indicators simultaneously for related tasks (creates visual "noise")
- **Don't** use progress indicators for tasks that are expected to be nearly instantaneous (<200ms)
- **Don't** use generic labels like "Loading" without context
- **Don't** forget the stop indicator on linear tracks when track contrast is low
- **Don't** block user interaction unnecessarily
- **Don't** use progress indicators as decorative elements
- **Don't** mix indicator types for the same action

---

## Anatomy & Placement

### Components

1. **Active Indicator**: The moving element that represents progress
2. **Track**: The fixed path the indicator moves along
3. **Stop Indicator**: A visual mark at the end of a linear determinate track to signal the "goal" or 100% mark

### Placement Guidelines

#### Linear Indicators

- **Alignment**: Aligned with the edge of the container they relate to
- **Width**: Should span the full width of the container
- **Position**: Typically at the top edge (below app bar) or bottom edge
- **Spacing**: Inset 4dp from the edge of the screen or container

#### Circular Indicators

- **Alignment**: Centered within the content area they represent
- **Size**: Choose based on context (24dp for buttons, 40-48dp for cards, up to 240dp for large displays)
- **Position**: Center of the loading area or empty state

---

## Accessibility Guidelines

### Contrast Requirements

- The active indicator and stop indicator must maintain at least a **3:1 contrast ratio** against the track or background
- If the track has low contrast with the background, the stop indicator is **required** for linear determinate indicators

### Roles & Labels

#### ARIA Attributes

```html
<!-- Determinate -->
<div role="progressbar" 
     aria-valuenow="45" 
     aria-valuemin="0" 
     aria-valuemax="100"
     aria-label="Loading news article">
</div>

<!-- Indeterminate -->
<div role="progressbar" 
     aria-label="Updating profile"
     aria-busy="true">
</div>
```

#### Accessibility Labels

Provide a clear accessibility label that describes:
- **What** is being loaded/processed
- **Why** the user is waiting

**Good examples:**
- "Loading news article"
- "Uploading photo"
- "Saving profile changes"
- "Downloading document"

**Bad examples:**
- "Loading" (too vague)
- "Please wait" (doesn't explain what's happening)
- "Processing" (not specific enough)

### Screen Reader Support

- Ensure the indicator is discoverable by assistive technology
- Allow users to check progress status manually
- Announce progress updates at reasonable intervals (not too frequent)
- Announce completion when process finishes

### Button Integration

When placing an indicator inside a button:
- The indicator should match the color of the button's text or icon
- The track should be hidden to maintain clarity
- The button should be disabled during the loading state
- Provide clear feedback when the action completes

---

## Common Mistakes to Avoid

### 1. Low Contrast

**Problem:** Using a track color that is too similar to the active indicator, making it hard to judge progress.

**Solution:** Ensure minimum 3:1 contrast ratio. Use design tokens for consistent colors.

### 2. Missing Stop Indicators

**Problem:** Forgetting the stop indicator on linear tracks when the track itself is faint.

**Solution:** Always include a stop indicator when track contrast is below 3:1 with the background.

### 3. Vague Labeling

**Problem:** Using generic labels like "Loading" without specifying what is being loaded.

**Solution:** Be specific: "Loading video" instead of just "Loading".

### 4. Multiple Indicators for Related Tasks

**Problem:** Showing individual progress indicators for each item in a group.

**Solution:** Use a single indicator for the overall group progress.

### 5. Blocking Unnecessary Content

**Problem:** Showing a full-screen loading indicator when only a small section is loading.

**Solution:** Scope the indicator to the affected area. Use skeleton screens for partial content loading.

### 6. Inconsistent Indicator Types

**Problem:** Using different indicator types for the same action in different parts of the app.

**Solution:** Establish patterns and use them consistently (e.g., always use circular for "refresh").

---

## Best Practices by Use Case

### Page Loading

**Recommended:** Linear indeterminate at top of page
- Spans full width
- Doesn't block content
- Clear visual feedback

### Form Submission

**Recommended:** Circular indeterminate in submit button
- Localized to the action
- Button is disabled during submission
- Clear that action is processing

### File Upload/Download

**Recommended:** Linear determinate with percentage
- Shows exact progress
- Includes stop indicator
- Updates smoothly as progress changes

### Content Refresh

**Recommended:** Circular indeterminate, pull-to-refresh pattern
- Familiar interaction pattern
- Compact and unobtrusive
- Quick feedback

### Background Sync

**Recommended:** Small circular indeterminate in status area
- Subtle, doesn't interrupt workflow
- Indicates ongoing background activity
- Can be dismissed if needed

---

## Responsive Behavior

### Mobile

- Use full-width linear indicators for page-level loading
- Prefer circular indicators for component-level loading
- Ensure touch targets remain accessible during loading

### Tablet

- Consider using linear indicators for split-screen scenarios
- Scope indicators to the affected pane
- Maintain consistent sizing across orientations

### Desktop

- Linear indicators can span full width or be scoped to containers
- Consider using smaller circular indicators for quick actions
- Ensure indicators are visible on large screens

---

## Motion & Animation

### Respect User Preferences

- Check for `prefers-reduced-motion` media query
- Provide static alternatives for users with motion sensitivity
- Reduce animation speed or use simpler animations when requested

### Animation Guidelines

- Use standard easing curves for smooth transitions
- Avoid jarring or sudden movements
- Keep animations purposeful, not decorative
- Ensure animations don't cause performance issues

---

## Related Documentation

- [Progress Indicators - Overview](./overview.md)
- [Progress Indicators - Specifications](./specs.md)
- [Progress Indicators - Implementation](./implementation.md)

## References

- [Material Design 3 - Progress Indicators Guidelines](https://m3.material.io/components/progress-indicators/guidelines)
- [WCAG 2.1 - Understanding Success Criterion 1.4.3: Contrast](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html)
