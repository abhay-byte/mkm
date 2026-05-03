# MKM Project Progress

> Last updated: 2026-05-03

## Overview

MKM (Mobile Kernel Manager) is an Android system tuning and monitoring app built with Jetpack Compose. This document tracks the implementation status of all major features.

## Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Home / Dashboard | | Overview with Shizuku/Root status, quick stats |
| RAM | | Memory info, swap settings |
| CPU | | Frequency control, core status |
| GPU | | Basic GPU info |
| Storage | | Storage usage display |
| Power | | Power-related settings |
| **Battery** | **In Progress** | Stats page done, notification system done |
| Overlay | | Floating system monitor overlay |
| Settings | | App preferences |

## Recently Completed

- Battery stats screen with hero card, drain rates, session breakdown
- Battery notification toggle with persistent notification support
- Navigation drawer with all screen destinations

## Recently Fixed

- **Battery drawer navigation** (2026-05-03): Battery menu item was not clickable because it was gated behind `isAccessGranted` check. Added `Screen.Battery` to the always-enabled list in `MainActivity.kt`.

## In Progress / Planned

- Battery notification rich content (match Franco Kernel Manager style with deep sleep %, awake time, etc.)
- CPU/GPU tuning with Shizuku/Root privilege escalation
- Thermal tuning
- Devfreq tuning

## Architecture

- **UI**: Jetpack Compose with Material 3
- **Navigation**: Compose Navigation with Navigation Drawer
- **Privilege**: Shizuku + Root fallback
- **State**: ViewModel + StateFlow
