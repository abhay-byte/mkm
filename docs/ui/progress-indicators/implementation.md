# Progress Indicators - Implementation

This document provides code examples and implementation guidance for Material 3 progress indicators in Jetpack Compose.

## Jetpack Compose Implementation

### Dependencies

Add Material 3 dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.3.0")
    
    // For expressive wavy indicators
    implementation("androidx.compose.material3:material3-expressive:1.5.0-alpha12")
}
```

---

## Linear Progress Indicators

### Basic Linear Indeterminate

```kotlin
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BasicLinearProgress() {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Linear Determinate with Progress

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun DeterminateLinearProgress(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress"
    )
    
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Custom Colors

```kotlin
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

@Composable
fun CustomColorLinearProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiary,
        trackColor = MaterialTheme.colorScheme.tertiaryContainer
    )
}
```

---

## Circular Progress Indicators

### Basic Circular Indeterminate

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BasicCircularProgress() {
    CircularProgressIndicator(
        modifier = Modifier.size(40.dp)
    )
}
```

### Circular Determinate with Progress

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeterminateCircularProgress(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress"
    )
    
    CircularProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.size(48.dp)
    )
}
```

### Circular with Custom Stroke Width

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun ThickCircularProgress(progress: Float) {
    CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.size(64.dp),
        strokeWidth = 8.dp,
        strokeCap = StrokeCap.Round
    )
}
```

---

## Wavy Progress Indicators (Expressive)

### Linear Wavy Indeterminate

```kotlin
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LinearWavyProgress() {
    LinearWavyProgressIndicator(
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Linear Wavy Determinate

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeterminateLinearWavyProgress(currentProgress: Float) {
    var progress by remember { mutableFloatStateOf(0.1f) }
    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress"
    )
    
    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Thick Linear Wavy with Custom Amplitude

```kotlin
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThickLinearWavyProgress(progress: Float) {
    val thickStrokeWidth = with(LocalDensity.current) { 8.dp.toPx() }
    val thickStroke = remember(thickStrokeWidth) { 
        Stroke(width = thickStrokeWidth, cap = StrokeCap.Round) 
    }
    
    LinearWavyProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        stroke = thickStroke,
        trackStroke = thickStroke,
        amplitude = 1.0f, // Full amplitude
        wavelength = 40.dp
    )
}
```

### Circular Wavy Progress

```kotlin
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircularWavyProgress() {
    CircularWavyProgressIndicator(
        modifier = Modifier.size(48.dp)
    )
}
```

---

## Complete Examples

### File Upload with Progress

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FileUploadProgress(
    fileName: String,
    uploadProgress: Float,
    isUploading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Uploading: $fileName",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val animatedProgress by animateFloatAsState(
            targetValue = uploadProgress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "upload progress"
        )
        
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### Loading Button

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoadingButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = if (isLoading) "Loading..." else text)
    }
}

// Usage
@Composable
fun LoadingButtonExample() {
    var isLoading by remember { mutableStateOf(false) }
    
    LoadingButton(
        text = "Submit",
        isLoading = isLoading,
        onClick = {
            isLoading = true
            // Simulate async operation
            // isLoading = false when done
        }
    )
}
```

### Page Loading Overlay

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "Loading...",
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
```

### Pull to Refresh Pattern

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshExample() {
    var isRefreshing by remember { mutableStateOf(false) }
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            // Simulate refresh
            // isRefreshing = false when done
        }
    ) {
        // Your content here
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Pull down to refresh")
        }
    }
}
```

### Multi-Step Process

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MultiStepProgress(
    currentStep: Int,
    totalSteps: Int,
    stepDescription: String
) {
    val progress = currentStep.toFloat() / totalSteps.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "step progress"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Step $currentStep of $totalSteps",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = stepDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

---

## Accessibility Implementation

### Adding Semantic Properties

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*

@Composable
fun AccessibleProgress(
    progress: Float,
    description: String
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress,
                    range = 0f..1f
                )
                contentDescription = description
            }
    )
}

// Usage
@Composable
fun AccessibleProgressExample() {
    AccessibleProgress(
        progress = 0.65f,
        description = "Uploading photo: 65% complete"
    )
}
```

---

## Testing

### Preview Examples

```kotlin
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
fun LinearProgressPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Indeterminate
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            
            // Determinate at 25%
            LinearProgressIndicator(
                progress = { 0.25f },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Determinate at 75%
            LinearProgressIndicator(
                progress = { 0.75f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CircularProgressPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Indeterminate
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
            
            // Determinate at 50%
            CircularProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
```

---

## Performance Considerations

### Avoid Frequent Updates

```kotlin
// ❌ Bad: Updates too frequently
LaunchedEffect(Unit) {
    while (true) {
        progress += 0.01f
        delay(10) // Updates every 10ms
    }
}

// ✅ Good: Reasonable update frequency
LaunchedEffect(Unit) {
    while (true) {
        progress += 0.05f
        delay(100) // Updates every 100ms
    }
}
```

### Use Remember for Static Values

```kotlin
// ✅ Good: Stroke is created once
val thickStroke = remember { 
    Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round) 
}
```

---

## Related Documentation

- [Progress Indicators - Overview](./overview.md)
- [Progress Indicators - Specifications](./specs.md)
- [Progress Indicators - Guidelines](./guidelines.md)

## References

- [Jetpack Compose Material 3 Documentation](https://developer.android.com/jetpack/compose/designsystems/material3)
- [Material 3 Progress Indicators](https://m3.material.io/components/progress-indicators/overview)
- [Compose API Reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary)
