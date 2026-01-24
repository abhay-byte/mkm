package com.ivarna.mkm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PullToRefreshWrapper(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            // Custom indicator using Expressive LoadingIndicator
            Box(
                modifier = Modifier.align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                 if (isRefreshing || state.distanceFraction > 0f) {
                     // Container variant: Surface with shape and elevation
                     Surface(
                        modifier = Modifier
                            .padding(top = 16.dp) // Add some top margin
                            .size(48.dp) // Total size 48dp
                            .graphicsLayer {
                                // Scale the whole surface based on pull
                                val scale = if (isRefreshing) 1f else (state.distanceFraction).coerceIn(0f, 1f)
                                scaleX = scale
                                scaleY = scale
                                alpha = scale
                            },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp
                     ) {
                         Box(contentAlignment = Alignment.Center) {
                             val indicatorSize = 38.dp // Shape container 38dp
                             
                             // If refreshing, show indeterminate loading indicator
                             if (isRefreshing) {
                                 LoadingIndicator(
                                     modifier = Modifier.size(indicatorSize),
                                     color = MaterialTheme.colorScheme.primary
                                 )
                             } else {
                                 // progress based on pull
                                 val progress = (state.distanceFraction).coerceIn(0f, 1f)
                                 
                                 if (progress > 0.01f) {
                                     LoadingIndicator(
                                         progress = { progress },
                                         modifier = Modifier.size(indicatorSize),
                                         color = MaterialTheme.colorScheme.primary
                                     )
                                 }
                             }
                         }
                     }
                 }
            }
        }
    ) {
        content()
    }
}
