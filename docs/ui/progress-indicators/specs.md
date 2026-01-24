# Progress Indicators - Specifications

This document provides detailed technical specifications for Material 3 progress indicators.

## Linear Progress Indicator

### Dimensions and Sizing

| Property | Value |
|----------|-------|
| **Height (Baseline)** | 4dp |
| **Track Height** | 4dp |
| **Active Indicator Height** | 4dp |
| **Minimum Width** | 40dp |
| **Container Inset** | 4dp from edge |

### Configurations

Configurations of the linear determinate progress indicator:

- **Behavior:** Determinate and indeterminate
- **Thickness:** Default (4dp) and variable
- **Shape:** Flat and wavy

### Wavy Variant Dimensions

| Variant | Total Height | Active Indicator Height | Wave Amplitude |
|---------|--------------|------------------------|----------------|
| **Low Amplitude** | 10dp | 4dp | 3dp |
| **High Amplitude** | 14dp | 4dp | 5dp |

### Stop Indicator

| Property | Value |
|----------|-------|
| **Size** | 4dp × 4dp |
| **Shape** | Square with rounded corners |
| **Trailing Space** | 0dp |

### Technical Specifications (Baseline Tokens)

| Token | Value |
|-------|-------|
| **Wave Amplitude** | 3dp |
| **Active Indicator Wavelength (Determinate)** | 40dp |
| **Active Indicator Wavelength (Indeterminate)** | 20dp |
| **Stop Indicator Trailing Space** | 0dp |

### Color Specifications

| Element | Token | Default Value |
|---------|-------|---------------|
| **Active Indicator** | `md.sys.color.primary` | Primary color |
| **Track** | `md.sys.color.secondary-container` | Secondary container |
| **Stop Indicator** | `md.sys.color.primary` | Primary color |

---

## Circular Progress Indicator

### Dimensions and Sizing

| Property | Value |
|----------|-------|
| **Size (Baseline)** | 40dp diameter |
| **Size (Wavy)** | 48dp diameter |
| **Stroke Width** | 4dp |
| **Minimum Size** | 24dp |
| **Maximum Size** | 240dp |

### Technical Specifications (Baseline Tokens)

| Token | Value |
|-------|-------|
| **Active Indicator Wave Amplitude** | 1.6dp |
| **Active Indicator Wavelength** | 15dp |
| **Track - Active Indicator Space** | 4dp |
| **Stroke Width (Active)** | 4dp |
| **Stroke Width (Track)** | 4dp |

### Color Specifications

| Element | Token | Default Value |
|---------|-------|---------------|
| **Active Indicator** | `md.sys.color.primary` | Primary color |
| **Track** | `md.sys.color.secondary-container` | Secondary container |

---

## Animation and Motion

### General Animation Principles

- **Easing:** Standard easing curve for smooth transitions
- **Responsiveness:** Indicators should respond immediately to data updates

### Determinate State

| Property | Specification |
|----------|--------------|
| **Transition Duration** | ~400ms for value changes |
| **Easing** | Standard easing |
| **Behavior** | Smooth transition when progress updates |

### Indeterminate State

#### Linear Indeterminate

- **Animation:** Continuous stretching and contracting along the track
- **Movement:** Left to right (RTL: right to left)
- **Cycle:** Continuous loop

#### Circular Indeterminate

- **Animation:** Rotating path that expands and contracts
- **Rotation:** Clockwise
- **Cycle:** Continuous loop

### Wavy Variants

| Property | Specification |
|----------|--------------|
| **Wave Flow** | Constant speed through active indicator |
| **Direction** | Follows progress direction |
| **Purpose** | Reduces perceived wait time for long operations |

---

## Anatomy

Progress indicators consist of the following elements:

### 1. Active Indicator

The colored part that shows progress made.

**Characteristics:**
- Fills from 0% to 100% (determinate)
- Moves along track (indeterminate)
- Uses primary color by default

### 2. Track

The fixed background path of the indicator.

**Characteristics:**
- Shows the full range of possible progress
- Uses secondary container color by default
- Always visible (provides context)

### 3. Stop Indicator (Linear Only)

A fixed mark at the end of a linear progress indicator's track.

**Characteristics:**
- 4dp × 4dp size
- Required when track has low contrast with background (<3:1)
- Indicates the end of the operation
- Uses primary color

---

## Accessibility Requirements

### Contrast

| Requirement | Specification |
|-------------|--------------|
| **Minimum Contrast Ratio** | 3:1 between indicator and background |
| **Stop Indicator** | Required if track contrast < 3:1 with background |

### Screen Reader Support

- Progress indicators should have appropriate ARIA labels
- Determinate indicators should announce percentage
- Indeterminate indicators should announce loading state

### Motion Sensitivity

- Respect user's motion preferences
- Reduce animation for users with motion sensitivity
- Consider providing static alternatives

---

## Responsive Behavior

### Linear Indicators

| Breakpoint | Behavior |
|------------|----------|
| **Mobile** | Full width of container |
| **Tablet** | Full width of container |
| **Desktop** | Full width of container |

### Circular Indicators

| Context | Recommended Size |
|---------|-----------------|
| **Button** | 24dp - 32dp |
| **Card** | 40dp - 48dp |
| **Full Screen** | 48dp - 64dp |
| **Large Display** | Up to 240dp |

---

## States and Variants

### Determinate Linear

- Shows exact progress (0-100%)
- Includes stop indicator
- Smooth transitions between values

### Indeterminate Linear

- Continuous animation
- No stop indicator
- Indicates ongoing process

### Determinate Circular

- Shows exact progress (0-100%)
- Fills clockwise
- Can include percentage text

### Indeterminate Circular

- Rotating animation
- Continuous loop
- No percentage display

### Wavy Linear (Expressive)

- Animated wave pattern
- Available for both determinate and indeterminate
- Configurable amplitude and wavelength

### Wavy Circular (Expressive)

- Animated wave pattern on circular path
- Available for both determinate and indeterminate
- Slightly larger size (48dp vs 40dp)

---

## Platform-Specific Considerations

### Android (Jetpack Compose)

- Use `LinearProgressIndicator` and `CircularProgressIndicator`
- Wavy variants available in Material 3 Expressive API
- See [implementation.md](./implementation.md) for code examples

### Web

- Use CSS animations for smooth performance
- Consider using SVG for circular indicators
- Ensure proper ARIA attributes

### iOS

- Follow platform conventions while maintaining Material Design principles
- Use native progress views when appropriate

---

## Related Documentation

- [Progress Indicators - Overview](./overview.md)
- [Progress Indicators - Guidelines](./guidelines.md)
- [Progress Indicators - Implementation](./implementation.md)

## References

- [Material Design 3 - Progress Indicators Specs](https://m3.material.io/components/progress-indicators/specs)
- [Material Design Tokens](https://m3.material.io/foundations/design-tokens/overview)
