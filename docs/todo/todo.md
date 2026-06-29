---
- id: T1
  title: Shizuku ADB commands failing across all app pages
  type: bug
  priority: critical
  difficulty: easy
  frequency: always
  expected: ADB commands issued through Shizuku execute correctly and app features work
  actual: All ADB commands through Shizuku fail; app features do not work
  reproduction: |
    1. Open any page in the app that uses Shizuku
    2. Trigger any ADB command through Shizuku
    3. Observe that the feature does not work
  impact: All Shizuku-based ADB functionality across the app
  images: null
  github_ref: null
  plan: null
---
