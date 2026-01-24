# Progress Indicators

Material 3 progress indicators documentation for the MKM project.

## Overview

Progress indicators inform users about the status of ongoing processes in real-time. They communicate that an app is busy, such as when loading content, submitting a form, or saving updates.

## Documentation Structure

This directory contains comprehensive documentation for implementing Material 3 progress indicators:

- **[Overview](./overview.md)** - Introduction, types, and when to use progress indicators
- **[Specifications](./specs.md)** - Detailed technical specifications, dimensions, colors, and animations
- **[Guidelines](./guidelines.md)** - Best practices, accessibility, and usage patterns
- **[Implementation](./implementation.md)** - Code examples and implementation guide for Jetpack Compose

## Quick Reference

### Types

- **Linear Progress Indicators** - Horizontal bars for page-level loading
- **Circular Progress Indicators** - Circular tracks for component-level loading
- **Wavy Variants** - Expressive animated wave patterns (Material 3 Expressive)

### States

- **Determinate** - Shows exact progress (0-100%)
- **Indeterminate** - Shows ongoing activity without specific progress

### When to Use

| Duration | Indicator |
|----------|-----------|
| <200ms | None |
| 200ms - 5s | Indeterminate |
| >5s | Determinate |

## Getting Started

1. Read the [Overview](./overview.md) to understand the basics
2. Check [Guidelines](./guidelines.md) for best practices
3. Review [Specifications](./specs.md) for technical details
4. Implement using examples from [Implementation](./implementation.md)

## Material Design Resources

- [Material 3 Progress Indicators](https://m3.material.io/components/progress-indicators/overview)
- [Jetpack Compose Material 3](https://developer.android.com/jetpack/compose/designsystems/material3)
