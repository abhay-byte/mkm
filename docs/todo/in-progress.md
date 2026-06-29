---
- id: T2
  title: Simplified Chinese Support
  type: feature
  priority: high
  difficulty: medium
  why: Make the app accessible to Simplified Chinese speaking users
  really_needed: unknown
  impact: UI strings, localization resources, possibly layouts
  followups: null
  images: null
  github_ref: GH-13
  plan: |
    Goal: Add Simplified Chinese (zh-rCN) localization.
    Files: MODIFY values/strings.xml (extract ~457 English strings); CREATE values-zh-rCN/strings.xml (Chinese translations); MODIFY 20 UI .kt files (replace hardcoded strings with stringResource(R.string.*))
    Approach: 1) Extract all visible UI strings from 20 Compose files 2) Write values/strings.xml with resource IDs 3) Write values-zh-rCN/strings.xml with translations 4) Refactor .kt files to use stringResource() 5) Verify build succeeds
    Edge cases: Formatted strings (%s/%d), contentDescription, dynamic concatenations, plurals
    Test plan: Build app, verify no compile errors, verify zh locale loads
---
