# Cards – Guidelines

Best practices and usage guidelines for implementing Material Design 3 cards.

## Usage

Use a card to display content and actions on a single topic.

Cards should be easy to scan for relevant and actionable information. Elements like text and images should be placed on cards in a way that clearly indicates hierarchy.

### When to Use Cards

✅ **Do:**
- Display content and actions on a single topic
- Serve as entry points into deeper levels of detail or navigation
- Display related information on a single subject
- Group cards together in a grid, vertical list, or carousel

❌ **Don't:**
- Force content into cards when spacing, headlines, or dividers would create a simpler visual hierarchy

### Card Types

There are three types of cards:

1. **Elevated** – Have a drop shadow, providing more separation from the background than filled cards, but less than outlined cards
2. **Filled** – Provide subtle separation from the background. This has less emphasis than elevated or outlined cards
3. **Outlined** – Have a visual boundary around their container. This can provide greater emphasis than the other types

Each provides the same legibility and functionality, so the type you use depends on style alone.

---

## Anatomy

The card container is the only required element in a card. Card layouts can vary to support the types of content they contain.

### Elements

1. **Container** (Required)
2. **Headline** (Optional)
3. **Subhead** (Optional)
4. **Supporting text** (Optional)
5. **Image** (Optional)
6. **Button** (Optional)

### Container

Card containers hold all card elements. Their size is determined by the space those elements occupy. Card elevation is expressed by the container.

> [!IMPORTANT]
> The card container is the only required element of a card. All other elements are optional.

### Content Blocks

Card contents are grouped into blocks. Content can have different levels of visual emphasis depending on importance. Card layouts vary to support the types of content they contain.

Cards can contain:
- Headline
- Subhead
- Supporting text
- Media
- Actions

### Dividers

Dividers can separate regions in cards or indicate areas of a card that can expand.

- **Full-width dividers** – Use for content that can be expanded
- **Inset dividers** – Don't run the full width of a card; use to separate related content

---

## Media

### Thumbnail
Cards can include thumbnails for an avatar or logo.

### Image
Cards can include photos, illustrations, and other graphics, such as weather icons.

### Video
Cards can include video content.

### Layering Text, Icons, and Images

> [!CAUTION]
> It isn't recommended to place text or icons on images. If it's necessary, ensure the background image provides sufficient contrast for the text to meet accessibility standards.

**Best practices:**
- Add a translucent scrim or bounding shape beneath the text or icon to help ensure proper contrast
- Ensure that text on images meets accessible contrast standards
- When placing text or icons on images, consider using a bounding shape to ensure proper contrast

---

## Text

### Headline
Headline text often communicates the subject of the card, such as the name of a photo album or article.

### Subhead
Subheads are smaller text elements, such as an article byline or a tagged location.

### Supporting Text
Supporting text includes body content, such as an article summary or a restaurant description.

---

## Actions

### Primary Action Area

Cards can be one large touch target triggering an expanded detail screen. The action area of a card contains rich media and supporting text.

### Buttons

Cards can include buttons for actions such as:
- Learn more
- Add to cart
- Share
- Other primary/secondary actions

### Icon Buttons

Cards can include icon buttons for actions such as:
- Save
- Heart/Favorite
- Star rating
- Share

### Selection Controls

Cards can also include:
- Chips
- Sliders
- Checkboxes
- Other selection controls

### Linked Text

There can be a link in the supporting text on a card.

### Overflow Menu

Overflow menus contain related actions. They are typically placed in the upper-right or lower-right corner of a card.

---

## Cards in a Collection

Multiple cards can be grouped together into collections displayed in a grid, list, or carousel.

By default, cards in a collection are coplanar. They share the same resting elevation unless they're picked up or dragged.

### Filtering and Sorting

Card collections can be filtered in a variety of ways, including by date or alphabetical order.

> [!NOTE]
> If a collection can be filtered, the filter must apply to each card in the collection. Filter or sorting options should be placed outside of the card collection.

### Layout Options

#### Grid
Cards can be displayed together in a grid. The default grid can be customized in code to show cards in:
- Standard grid
- Staggered grid
- Mosaic grid

#### Vertical List
Cards can be displayed together in a vertical list.

#### Carousel
Cards can be displayed together in a horizontal row or carousel.

---

## Adaptive Design

As cards scale to adapt to different window size classes, their position and alignment can also change. Cards and their elements can align left, right, or center as the layout scales.

### Ergonomics

Adjust the layout of cards to meet the ergonomic needs of large screens.

**Example:**
- A horizontally-oriented card in a compact window size (< 600dp) may become a larger, vertically-oriented card in an expanded window size (840dp - 1199dp)
- Larger screens provide more space for images and text

### Visual Presentation

To adjust the presentation of content-focused components, begin with spacing. Allow components like lists, cards, and images to optimize space while filling the region of a screen that suits a device breakpoint's ergonomic needs.

### Column-Based Layouts

**Mobile layouts:**
- Components such as lists or cards are stretched to fit the full width of the screen

**Large screens (expanded window size):**
- Use multiple columns to display content
- Avoid extending UI elements across the screen when possible
- Rearrange groups of related cards into horizontal rows or carousels for better content organization

### Small Screens

On smaller screens with the compact window size (< 600dp):
- Consider swapping cards for lists, which can display images and text in a more compact form
- Make sure that controls, actions, and other component-specific elements are maintained

---

## Behavior

### Expanding

Cards can use a container transform transition pattern to reveal additional content. Reserve this pattern for hero moments that are meant to be expressive.

✅ **Do:**
- Expand a card to reveal information

❌ **Don't:**
- Scroll within a card to reveal information

### Navigation

Cards can use a forward and backward transition pattern to navigate between screens at consecutive levels of hierarchy. This pattern has a simpler motion style compared to container transform, which makes it suitable for common navigation transitions.

---

## Gestures

### Swipe

A swipe gesture can be performed on a single card at a time, anywhere on that card.

**Use cases:**
- Dismiss a card
- Change the state of a card, such as flagging or archiving it

✅ **Do:**
- A card should only have one swipe action assigned to it

❌ **Don't:**
- Cards shouldn't contain content that can be swiped, such as an image carousel or pagination
- Swipe gestures shouldn't cause portions of cards to detach upon swiping

### Pick Up & Move

The pick-up-and-move gesture allows users to move and reorder cards in a collection.

✅ **Do:**
- When moving a card, increase its elevation

❌ **Don't:**
- Don't let cards bump other elements out of the way
- When a card is picked up, it appears in front of all elements, except app bars and navigation

### Scrolling

Card content that's taller than the maximum card height is truncated and doesn't scroll, but can be displayed by expanding the height of a card.

A card can expand beyond the maximum height of the screen, in which case the card scrolls within the screen.

#### Mobile Scrolling

✅ **Do:**
- Cards can expand to reveal more content, scrolling within the screen
- Content within cards doesn't scroll

❌ **Don't:**
- Cards can't internally scroll on mobile, as it could cause two scroll bars to be displayed

#### Desktop Scrolling

On a desktop device, card content can expand and scroll within a card.

---

## Best Practices Summary

### Content Organization
- Use cards for single-topic content and actions
- Maintain clear visual hierarchy with text and images
- Group related cards in collections (grid, list, or carousel)

### Accessibility
- Ensure sufficient contrast for text on images
- Use scrim or bounding shapes when layering text on images
- Maintain accessible touch targets for interactive elements

### Responsive Design
- Adapt card layout for different screen sizes
- Use column-based layouts on large screens
- Consider lists instead of cards on very small screens

### Interactions
- Use container transform for expressive expansions
- Implement single swipe actions per card
- Increase elevation when cards are picked up or dragged

---

*Based on [Material Design 3 Card Guidelines](https://m3.material.io/components/cards/guidelines)*
