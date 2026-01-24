# Progress Indicators - Overview

Progress indicators inform users about the status of ongoing processes in real-time. They communicate that an app is busy, such as when loading content, submitting a form, or saving updates.

## Purpose

Progress indicators show the status of a process in real time, helping users understand:
- That the app is working on their request
- How long they might need to wait
- Whether progress is being made

## Types of Progress Indicators

Material 3 defines two primary types of progress indicators:

### 1. Linear Progress Indicators

Horizontal bars that show progress along a track.

**Best used for:**
- Page-level loading states
- Form submissions
- File uploads/downloads
- Processes that span the full width of a container

**Placement:**
- Positioned along the top edge of a page or the expanding edge of a container
- Should span the full width of its container (minimum width: 40dp)

### 2. Circular Progress Indicators

Circular tracks that animate clockwise.

**Best used for:**
- Button loading states
- Card content loading
- Centered screen loading
- Compact spaces

**Placement:**
- Centered in the container or empty space where new content will appear
- Sizes range from 24dp to 240dp

## When to Use Progress Indicators

Usage is determined by the expected wait time and the nature of the process:

| Wait Time | Recommendation |
|-----------|---------------|
| **Instant** (<200ms) | No indicator needed; display content immediately |
| **Short** (200ms – 5s) | Use a loading indicator (circular indicators are often preferred for short, indeterminate tasks) |
| **Long** (>5s) | Use a progress indicator (determinate preferred if the duration is known) |

### Grouped Items

When multiple items load together, use a single indicator for the group rather than individual indicators for each item.

## Determinate vs. Indeterminate

### Determinate Progress Indicators

Used when the progress and wait time are **known**.

**Characteristics:**
- The indicator fills from 0% to 100%
- Shows exact progress percentage
- Provides users with a sense of how much time remains

**Use when:**
- File upload/download progress is known
- Multi-step processes with defined steps
- Operations with calculable completion time

### Indeterminate Progress Indicators

Used when the progress is **unknown**.

**Characteristics:**
- The indicator moves along a fixed track
- Grows and shrinks in size continuously
- Provides feedback that work is happening

**Use when:**
- Loading time is unpredictable
- Waiting for server response
- Initial app loading
- Background processes

### Transitioning Between States

An indicator can change from **indeterminate to determinate** as more information becomes available (e.g., when total file size is determined during a download).

## Material 3 Expressive Styles

### Wavy Shape

A new expressive configuration for both linear and circular indicators that makes long processes feel less static and more engaging.

**Features:**
- Animated wave pattern
- Configurable amplitude and wavelength
- Reduces perceived wait time for long operations

### Variable Track Height

Linear indicators can have different track heights:
- **Standard:** 4dp (default)
- **Thick:** 8dp (for emphasis or better visibility)

## Anatomy

Progress indicators consist of the following elements:

1. **Active Indicator:** The colored part showing progress
2. **Track:** The path the indicator follows
3. **Stop Indicator:** A 4dp circle at the end of a linear determinate indicator (required when track has low contrast with background)

## Key Principles

### Consistency
Use the same type of indicator for a specific process throughout the product (e.g., always use circular for "refresh").

### Contrast & Accessibility
- Maintain a minimum **3:1 contrast ratio** between indicator and background
- If the track has low contrast with the background, the **stop indicator** is required for linear determinate bars
- Ensure indicators are visible to users with visual impairments

### Right-to-Left (RTL) Support
- Linear indicators should be **mirrored horizontally** in RTL languages
- Circular indicators do **not** require mirroring

## Related Components

- [Progress Indicators - Specifications](./specs.md)
- [Progress Indicators - Guidelines](./guidelines.md)
- [Progress Indicators - Implementation](./implementation.md)

## References

- [Material Design 3 - Progress Indicators](https://m3.material.io/components/progress-indicators/overview)
