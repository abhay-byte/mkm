---
- id: T2
  title: Add monochrome app icon layer for Android 13+ theming
  type: feature
  priority: low
  difficulty: easy
  why: From GH-4 — enables dynamic theming with system wallpaper on Android 13+
  really_needed: Nice-to-have, cosmetic
  impact: res/mip-anydpi-v33, adaptive icon XML
  followups: null
  images: null
  github_ref: GH-4
  plan: null
- id: T3
  title: Add scroll to menu drawer (landscape cut-off fix)
  type: feature
  priority: medium
  difficulty: easy
  why: Menu drawer items get cut off in landscape mode and cannot be scrolled to — blocks landscape UX
  really_needed: Workaround is rotating back to portrait; landscape is unusable otherwise
  impact: MainActivity drawer (likely ModalNavigationDrawer / DrawerContent composable) — wrap Column in verticalScroll
  followups: null
  images: null
  github_ref: null
  plan: null
---
