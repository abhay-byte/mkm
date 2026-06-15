---
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
  plan: |
    Goal: Wrap the nav items inside ModalDrawerSheet in a verticalScroll
    Column so the menu is scrollable in landscape mode. Logo + divider
    stay pinned at the top.

    Change (in app/src/main/java/com/ivarna/mkm/MainActivity.kt):
    - ModalDrawerSheet (line 129)
      - Logo Row (line 131) — stays fixed
      - HorizontalDivider (line 147) — stays fixed
      - NEW: Column(Modifier.verticalScroll(rememberScrollState()))
        - navItems.forEach { ... NavigationDrawerItem(...) } (line 148-191)

    Imports to add:
    - androidx.compose.foundation.verticalScroll
    - androidx.compose.foundation.rememberScrollState

    Test plan:
    - Manual A: portrait mode — drawer opens, all items visible, no scroll needed
    - Manual B: landscape mode — open drawer, drag items, all become reachable
    - Manual C: scroll smoothness — no jank
    - Manual D: header pinned — logo + divider stay visible while items scroll
---
