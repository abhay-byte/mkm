# Navigation Drawer - Overview

Navigation drawers let people switch between UI views on larger devices.

## Important Note

⚠️ **Deprecation Notice**: The navigation drawer is being deprecated in the Material 3 expressive update. For those who have updated, use an expanded navigation rail, which has mostly the same functionality of the navigation drawer and adapts better across window size classes.

## What is a Navigation Drawer?

The [navigation drawer](https://material.io/components/navigation-drawer) component is a slide-in menu that lets users navigate to various sections of your app. Users can activate it by swiping from the side or tapping a menu icon.

![Navigation drawer example](https://developer.android.com/static/develop/ui/compose/images/layouts/material/m3-navigation-drawer.png)

## Use Cases

Consider these three use cases for implementing a Navigation Drawer:

- **Content organization:** Enable users to switch between different categories, such as in news or blogging apps.
- **Account management:** Provide quick links to account settings and profile sections in apps with user accounts.
- **Feature discovery:** Organize multiple features and settings in a single menu to facilitate user discovery and access in complex apps.

## Types of Navigation Drawers

In Material Design, there are two types of navigation drawers:

### 1. Standard Navigation Drawer

Standard navigation drawers share space within a screen with other content. They provide access to drawer destinations and app content for layouts in expanded, large, and extra-large window sizes.

Standard drawers can be:
- **Permanently visible** (best for frequently switching destinations)
- **Dismissible** (best for focusing more on screen content, opened/closed by tapping a menu icon)

### 2. Modal Navigation Drawer

Modal navigation drawers appear over the top of other content within a screen. They use a scrim to block interaction with the rest of an app's content, and don't affect the screen's layout grid.

Modal navigation drawers can be used in any window size, but are primarily used in compact and medium sizes where space is limited or prioritized for app content.

## When to Use Navigation Drawers

Navigation drawers are recommended for:

- Apps with **5 or more top-level destinations**
- Apps with **2 or more levels of navigation hierarchy**
- **Quick navigation between unrelated destinations**
- **Replacing the navigation rail or navigation bar on large screens**

## Related Components

- [Navigation Rails](../navigation-rails/overview.md) - For medium and expanded window sizes
- Navigation Bars - For compact window sizes

## Resources

- [Material Design 3 Guidelines](https://m3.material.io/components/navigation-drawer/overview)
- [Android Developer Documentation](https://developer.android.com/develop/ui/compose/components/drawer)
