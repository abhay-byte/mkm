# Cards – Implementation

Implementation guide for Material Design 3 cards in Jetpack Compose.

## Overview

The [`Card`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#Card(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Shape,androidx.compose.material3.CardColors,androidx.compose.material3.CardElevation,androidx.compose.foundation.BorderStroke,kotlin.Function1)) composable acts as a Material Design container for your UI. Cards typically present a single coherent piece of content.

### Common Use Cases

- A product in a shopping app
- A news story in a news app
- A message in a communications app

It is the focus on portraying a single piece of content that distinguishes `Card` from other containers. For example, `Scaffold` provides general structure to a whole screen. Card is generally a smaller UI element inside a larger layout, whereas a layout component such as `Column` or `Row` provides a simpler and more generic API.

---

## Basic Implementation

`Card` behaves much like other containers in Compose. You declare its content by calling other composables within it.

### Minimal Example

```kotlin
@Composable
fun CardMinimalExample() {
    Card() {
        Text(text = "Hello, world!")
    }
}
```

> [!NOTE]
> By default, a `Card` wraps its content in a `Column` composable, placing each item inside the card below one another.

---

## Advanced Implementations

See the [reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#Card(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Shape,androidx.compose.material3.CardColors,androidx.compose.material3.CardElevation,androidx.compose.foundation.BorderStroke,kotlin.Function1)) for the API definition of `Card`. It defines several parameters that allow you to customize the appearance and behavior of the component.

### Key Parameters

| Parameter | Description |
|-----------|-------------|
| **`elevation`** | Adds a shadow to the component that makes it appear elevated above the background |
| **`colors`** | Uses the `CardColors` type to set the default color of both the container and any children |
| **`enabled`** | If you pass `false`, the card appears as disabled and does not respond to user input |
| **`onClick`** | Makes the card respond to presses from the user (experimental overload) |
| **`shape`** | Defines the shape of the card (default is 12dp rounded corners) |
| **`modifier`** | Standard Compose modifier for size, padding, etc. |

---

## Card Types

### Filled Card

The filled card is the default card type. Use the `colors` property to change the filled color.

```kotlin
@Composable
fun FilledCardExample() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .size(width = 240.dp, height = 100.dp)
    ) {
        Text(
            text = "Filled",
            modifier = Modifier
                .padding(16.dp),
            textAlign = TextAlign.Center,
        )
    }
}
```

**Color Token:** `surfaceVariant` or `surfaceContainerHighest`

---

### Elevated Card

Use the dedicated [`ElevatedCard`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#ElevatedCard(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Shape,androidx.compose.material3.CardColors,androidx.compose.material3.CardElevation,kotlin.Function1)) composable for elevated cards.

```kotlin
@Composable
fun ElevatedCardExample() {
    ElevatedCard(
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        ),
        modifier = Modifier
            .size(width = 240.dp, height = 100.dp)
    ) {
        Text(
            text = "Elevated",
            modifier = Modifier
                .padding(16.dp),
            textAlign = TextAlign.Center,
        )
    }
}
```

**Key Features:**
- Uses `elevation` property to control shadow appearance
- Default elevation is typically 1-6dp
- Color Token: `surfaceContainerLow`

---

### Outlined Card

Use the dedicated [`OutlinedCard`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#OutlinedCard(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Shape,androidx.compose.material3.CardColors,androidx.compose.material3.CardElevation,androidx.compose.foundation.BorderStroke,kotlin.Function1)) composable for outlined cards.

```kotlin
@Composable
fun OutlinedCardExample() {
    OutlinedCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, Color.Black),
        modifier = Modifier
            .size(width = 240.dp, height = 100.dp)
    ) {
        Text(
            text = "Outlined",
            modifier = Modifier
                .padding(16.dp),
            textAlign = TextAlign.Center,
        )
    }
}
```

**Key Features:**
- Uses `border` property to define outline
- Color Tokens: `surface` (container), `outlineVariant` (border)

---

## Complete Card Example

Here's a more comprehensive example showing a card with multiple content elements:

```kotlin
@Composable
fun CompleteCardExample() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Text(
                text = "Card Title",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subheader
            Text(
                text = "Subheading",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Supporting text
            Text(
                text = "This is supporting text that provides additional details about the card content.",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { /* Handle action */ }) {
                    Text("Action 1")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { /* Handle action */ }) {
                    Text("Action 2")
                }
            }
        }
    }
}
```

---

## Clickable Cards

To make a card respond to clicks, use the `Card` overload with the `onClick` parameter:

> [!WARNING]
> The `Card` overload that defines the `onClick` parameter is experimental.

```kotlin
@Composable
fun ClickableCardExample() {
    Card(
        onClick = { 
            // Handle card click
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Click me!",
            modifier = Modifier.padding(16.dp)
        )
    }
}
```

---

## Card with Image

Example of a card containing an image and text:

```kotlin
@Composable
fun CardWithImageExample() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column {
            // Image
            Image(
                painter = painterResource(id = R.drawable.sample_image),
                contentDescription = "Sample image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
            
            // Content
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Image Card",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This card contains an image at the top.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

---

## Limitations

Cards don't come with inherent scroll or dismiss actions, but can integrate into composables offering these features.

### Swipe to Dismiss

To implement swipe to dismiss on a card, integrate it with the [`SwipeToDismiss`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#SwipeToDismiss(androidx.compose.material3.DismissState,kotlin.Function1,kotlin.Function1,androidx.compose.ui.Modifier,kotlin.collections.Set)) composable:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissCardExample() {
    val dismissState = rememberDismissState()
    
    SwipeToDismiss(
        state = dismissState,
        background = { /* Background content */ },
        dismissContent = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Swipe to dismiss",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    )
}
```

### Scrollable Content

To integrate with scroll, use scroll modifiers such as [`verticalScroll`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/package-summary#(androidx.compose.ui.Modifier).verticalScroll(androidx.compose.foundation.ScrollState,kotlin.Boolean,androidx.compose.foundation.gestures.FlingBehavior,kotlin.Boolean)):

```kotlin
@Composable
fun ScrollableCardExample() {
    val scrollState = rememberScrollState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            repeat(20) { index ->
                Text(text = "Item $index")
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
```

See the [Scroll documentation](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/scroll) for more information.

---

## Best Practices

### Layout Guidelines

- Use **16dp** padding inside cards for content
- Use **8dp** spacing between cards in a collection
- Cards have **12dp** corner radius by default

### Color Usage

```kotlin
// Use theme colors for consistency
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
) {
    // Content
}
```

### Accessibility

- Ensure clickable cards have appropriate content descriptions
- Use semantic properties for screen readers
- Maintain minimum touch target sizes (48dp)

---

## Additional Resources

- [Material Design 3 Cards Overview](https://m3.material.io/components/cards/overview)
- [Android Compose Card Reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#Card(androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Shape,androidx.compose.material3.CardColors,androidx.compose.material3.CardElevation,androidx.compose.foundation.BorderStroke,kotlin.Function1))
- [Scroll Documentation](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/scroll)

---

*Based on [Android Developers Card Documentation](https://developer.android.com/develop/ui/compose/components/card)*
