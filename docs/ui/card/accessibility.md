# Cards – Accessibility

Accessibility guidelines for implementing Material Design 3 cards.

## Use Cases

People should be able to do the following using assistive technology:

- Navigate to a card and the elements within a card
- Get appropriate feedback based on input type documented under Interaction & Style

---

## Interaction & Style

A card can be a **non-actionable container** that holds actions like buttons and links, or it can be **directly actionable** without any buttons or links. This is to avoid stacking actionable elements.

> [!IMPORTANT]
> An action shouldn't be placed on an actionable surface.

### Two Possible Card Interaction Behaviors

1. **Non-actionable card with buttons** – The card itself is not interactive, but contains actionable elements
2. **Directly actionable card with no buttons** – The entire card is a single interactive element

---

## Input Methods

### Touch

**Directly actionable cards:**
- When a user taps on a directly actionable card, a touch ripple appears across the card, indicating feedback

**Non-actionable cards:**
- Don't ripple when tapped
- Only the buttons or links within them provide touch feedback

### Dragging and Dismissing

> [!WARNING]
> To meet Material's accessibility standards, any dragging and swiping interactions need a single-pointer alternative, like selecting the same actions from a menu.

**Best practice:**
- Tapping a card, or pressing and holding, should open a menu to change its position in a list
- That menu could also contain an action to delete the card
- Use containers like bottom sheets or menus to show single-pointer options

> [!CAUTION]
> It isn't recommended to place menus on top of the card on the draggable state. If doing so is necessary, ensure that the menu doesn't cover the card and the interaction can be completed.

### Cursor (Mouse/Pointer)

**Directly actionable cards:**
- **Hover state** – Provides a visual cue that the element is interactive
- **Click** – A ripple appears, providing feedback

**Non-actionable cards:**
- Don't have a hover state
- Only the actionable elements within them show hover states

### Keyboard

A focus indicator appears around actionable elements when tabbing through cards. This provides a visual cue that the destination is now focused and an action can be taken.

**Navigation:**
- **Tab** – Navigate between actionable elements of the cards
- If the cards are non-actionable, Tab navigates directly to the actionable buttons or links within the cards
- If the cards are directly actionable, Tab navigates to the card container itself

**Interaction:**
- **Space or Enter** – Perform an action or open a secondary action (such as a menu)
- Within a menu:
  - **Arrow keys** – Navigate through menu items
  - **Space or Enter** – Select an item
  - **Tab** – Exit the menu

---

## Focus

All interactive elements of cards need a tab stop so they can be focused.

### Directly Actionable Cards

- The card itself is a tab stop
- Tab moves to the next card container

### Non-Actionable Cards

- The card itself is **not** a tab stop
- Every actionable element in the card is a tab stop
- All actionable elements are visited before focus navigates to the next card

> [!NOTE]
> Card layouts can change on different devices (e.g., list on mobile, gallery on tablet). Ensure focus order remains logical across all layouts.

---

## Keyboard Navigation

| Keys | Actions |
|------|---------|
| **Tab** | Move to the next actionable element<br>• Directly actionable cards: Move to next card container<br>• Non-actionable cards: Move to next actionable element |
| **Space or Enter** | Confirm action |
| **Arrow keys** | Navigate within menus or lists |

---

## Labeling Elements

### Screen Reader Support

The informative contents of a card are verbalized when navigating to them using a screen reader.

**Best practices:**
- If an image in a card is purely decorative, hide it from screen readers
- All actionable elements must receive both screen reader and keyboard focus

### Roles

**Directly actionable cards:**
- Can have the `button` or `link` role, depending on how they're used

**Non-actionable cards:**
- Are purely containers, so they don't need a role
- Only the actionable elements within them need roles

### Focus Order Example

For a non-actionable card, elements are navigable, focused in order, and verbalized when in focus:

1. **Heading** – Card title is announced
2. **Image** – Alt text is announced (if not decorative)
3. **Body text** – Supporting text is read
4. **Primary button** – Button label and role are announced
5. **Secondary button** – Button label and role are announced

---

## Implementation Checklist

### ✅ Keyboard Accessibility
- [ ] All interactive elements are keyboard accessible
- [ ] Focus order is logical and follows visual layout
- [ ] Focus indicators are clearly visible
- [ ] Space and Enter keys activate interactive elements

### ✅ Screen Reader Support
- [ ] Decorative images are hidden from screen readers
- [ ] All actionable elements have appropriate labels
- [ ] Card roles are correctly assigned (button/link for actionable cards)
- [ ] Content is announced in a logical order

### ✅ Touch & Pointer
- [ ] Touch targets meet minimum size requirements (48dp)
- [ ] Hover states are provided for directly actionable cards
- [ ] Ripple feedback is shown on interaction
- [ ] Drag/swipe actions have single-pointer alternatives

### ✅ Visual Feedback
- [ ] Focus states are clearly visible
- [ ] Hover states indicate interactivity
- [ ] Pressed states provide immediate feedback
- [ ] Disabled states are visually distinct

---

## Best Practices Summary

1. **Choose the right card type:**
   - Use directly actionable cards when the entire card triggers one action
   - Use non-actionable cards when multiple actions are needed

2. **Provide alternatives:**
   - Offer menu-based alternatives for drag and swipe gestures
   - Ensure all actions are accessible via keyboard

3. **Maintain focus order:**
   - Ensure logical tab order through card elements
   - Don't trap keyboard focus within cards

4. **Label appropriately:**
   - Provide meaningful labels for screen readers
   - Hide decorative content from assistive technology

5. **Test thoroughly:**
   - Test with keyboard-only navigation
   - Test with screen readers (TalkBack, VoiceOver, etc.)
   - Verify touch target sizes meet accessibility standards

---

*Based on [Material Design 3 Card Accessibility](https://m3.material.io/components/cards/accessibility)*
