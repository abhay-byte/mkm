# Navigation & UX Progress

> Last updated: 2026-05-03

## Status: Functional

### Completed

- [x] Navigation drawer with 9 items (Home, RAM, CPU, GPU, Storage, Power, Battery, Overlay, Settings)
- [x] Compose Navigation setup with `NavHost`
- [x] Route-based destination handling
- [x] Drawer state management (open/close)
- [x] Selected item highlight
- [x] Icons for all destinations (filled + outlined variants)

### Access Control

Some features require elevated privileges (Shizuku or Root). The drawer items are gated:

**Always enabled (no root/Shizuku needed):**
- Home
- Settings
- Overlay
- **Battery** *(added 2026-05-03)*

**Requires `isAccessGranted`:**
- RAM
- CPU
- GPU
- Storage
- Power

### Recent Fixes

| Date | Fix | File |
|------|-----|------|
| 2026-05-03 | Battery drawer item now navigates when clicked | `MainActivity.kt:117` |

### Known Issues

- Items that require access appear visually identical to enabled items but are silently non-clickable. Consider adding `enabled = isEnabled` to `NavigationDrawerItem` for visual feedback.

### Files

| File | Purpose |
|------|---------|
| `navigation/Screen.kt` | Screen routes, labels, icons |
| `MainActivity.kt` | Drawer + NavHost setup |
