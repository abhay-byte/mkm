package com.ivarna.mkm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.mkm.R
import com.ivarna.mkm.data.model.FpsSample
import com.ivarna.mkm.data.model.FpsSession
import com.ivarna.mkm.data.model.FpsSource

@Composable
fun FpsRecordGraph(
    session: FpsSession,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val allSamples = session.samples
    val activeSamples = allSamples.filter { !it.idle && it.fps > 0f }

    val currentSample = allSamples.lastOrNull()
    val currentFps = if (currentSample != null && !currentSample.idle) currentSample.fps else 0f
    val avgFps = if (activeSamples.isNotEmpty()) activeSamples.map { it.fps }.average().toFloat() else 0f
    val minFps = if (activeSamples.isNotEmpty()) activeSamples.minOf { it.fps } else 0f
    val maxFps = if (activeSamples.isNotEmpty()) activeSamples.maxOf { it.fps } else 0f

    val latestPkg = currentSample?.pkg ?: "—"
    val latestPid = currentSample?.pid ?: 0
    val latestSource = when (currentSample?.source) {
        FpsSource.ADRENO_INFLIGHT -> stringResource(R.string.fps_source_adreno)
        FpsSource.MALI_DMA_FENCE -> stringResource(R.string.fps_source_mali)
        FpsSource.FPS_MONITOR -> stringResource(R.string.fps_source_monitor)
        null -> "—"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Status indicator, Title, Target App
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(5.dp))
                        )
                    }
                    Text(
                        text = if (isRecording) stringResource(R.string.recording_in_progress)
                               else stringResource(R.string.fps_session_summary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = latestSource,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Target app info
            if (latestPkg.isNotBlank() && latestPkg != "—") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (latestPid > 0) "$latestPkg ($latestPid)" else latestPkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Metric Summary Row: Current, Avg, Min, Max, Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(
                    label = stringResource(R.string.current_fps),
                    value = if (currentFps > 0f) String.format("%.1f", currentFps) else "—",
                    unit = "FPS",
                    color = MaterialTheme.colorScheme.primary
                )
                MetricPill(
                    label = stringResource(R.string.avg_fps),
                    value = if (avgFps > 0f) String.format("%.1f", avgFps) else "—",
                    unit = "FPS",
                    color = MaterialTheme.colorScheme.secondary
                )
                MetricPill(
                    label = stringResource(R.string.min_fps),
                    value = if (minFps > 0f) String.format("%.1f", minFps) else "—",
                    unit = "FPS",
                    color = MaterialTheme.colorScheme.error
                )
                MetricPill(
                    label = stringResource(R.string.max_fps),
                    value = if (maxFps > 0f) String.format("%.1f", maxFps) else "—",
                    unit = "FPS",
                    color = Color(0xFF4CAF50)
                )
                MetricPill(
                    label = stringResource(R.string.samples_count),
                    value = "${activeSamples.size}/${allSamples.size}",
                    unit = "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // FPS Graph Canvas
            val primaryColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            val idleColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (allSamples.size < 2) return@Canvas

                    val maxY = maxOf(maxFps * 1.15f, 60f)
                    val width = size.width
                    val height = size.height

                    // Draw horizontal grid lines (30, 60, 90, 120 FPS as applicable)
                    val gridSteps = listOf(30f, 60f, 90f, 120f, 144f).filter { it < maxY }
                    for (step in gridSteps) {
                        val y = height - (step / maxY * height)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }

                    // Plot non-idle samples
                    val stepX = width / (allSamples.size - 1).coerceAtLeast(1)
                    val linePath = Path()
                    var pathStarted = false

                    allSamples.forEachIndexed { index, sample ->
                        val x = index * stepX
                        if (sample.idle || sample.fps <= 0f) {
                            // Draw idle indicator at bottom
                            drawCircle(
                                color = idleColor,
                                radius = 2.dp.toPx(),
                                center = Offset(x, height - 2.dp.toPx())
                            )
                        } else {
                            val y = (height - (sample.fps / maxY * height)).coerceIn(0f, height)
                            if (!pathStarted) {
                                linePath.moveTo(x, y)
                                pathStarted = true
                            } else {
                                linePath.lineTo(x, y)
                            }
                            // Draw data point
                            drawCircle(
                                color = primaryColor,
                                radius = 2.5.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }

                    if (pathStarted) {
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            if (allSamples.size >= 2) {
                val elapsedSec = ((allSamples.last().tMs - allSamples.first().tMs) / 1000f).coerceAtLeast(0f)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0s",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = String.format("%.0fs", elapsedSec),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (unit.isNotBlank()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
