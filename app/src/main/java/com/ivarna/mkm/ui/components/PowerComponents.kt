package com.ivarna.mkm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivarna.mkm.data.model.CpuEfficiencyResult
import com.ivarna.mkm.data.model.PowerStatus

@Composable
fun PowerMonitorCard(
    status: PowerStatus,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Power Monitor",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            // Big Watts Display
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "%.2f".format(status.powerW),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface 
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "W",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = "Total System Consumption",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PowerDetailItem("Voltage", "${status.voltageUv / 1000} mV")
                PowerDetailItem("Current", "${status.currentUa / 1000} mA")
            }
        }
    }
}

@Composable
fun PowerDetailItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EfficiencyGraph(
    dataPoints: List<Pair<Float, Float>>, // X: Power, Y: Score
    xLabel: String, // e.g., "Power (W)"
    yLabel: String, // e.g., "Score"
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (dataPoints.isEmpty()) {
        Box(modifier = modifier.height(200.dp), contentAlignment = Alignment.Center) {
            Text("No Data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxX = dataPoints.maxOf { it.first }
    val minX = dataPoints.minOf { it.first }
    val maxY = dataPoints.maxOf { it.second }
    val minY = dataPoints.minOf { it.second }

    Column(modifier = modifier) {
        // Graph Area with Y-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Y-Axis Labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "%.0f".format(maxY),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = yLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.rotate(-90f) 
                )
                Text(
                    text = "%.0f".format(minY),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // The Graph
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val width = size.width
                val height = size.height
                val rangeX = if (maxX == minX) 1f else maxX - minX
                val rangeY = if (maxY == minY) 1f else maxY - minY

                // Background Grid
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(0f, height),
                    end = Offset(width, height),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(0f, 0f),
                    end = Offset(0f, height),
                    strokeWidth = 2f
                )

                // Plot Line
                val path = Path()
                dataPoints.sortedBy { it.first }.forEachIndexed { index, point ->
                    val x = ((point.first - minX) / rangeX) * width
                    val y = height - (((point.second - minY) / rangeY) * height)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }

                    drawCircle(color = lineColor, radius = 6f, center = Offset(x, y))
                }

                drawPath(
                    path,
                    color = lineColor,
                    style = Stroke(
                        width = 6f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp), // Offset for Y-axis labels width approx
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "%.1f".format(minX),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = xLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "%.1f".format(maxX),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Legend
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(lineColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$yLabel vs $xLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun BenchmarkResultsTable(results: List<CpuEfficiencyResult>, modifier: Modifier = Modifier) {
    if (results.isEmpty()) return
    
    // Sort results by frequency (or policy/percent, which correlates) ascending
    val sortedResults = results.sortedBy { it.frequencyKHz }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Detailed Results",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Cluster Freqs (MHz)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("Power (W)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Score", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            
            // Rows
            val sortedResults = results.sortedBy { it.frequencyKHz }
            sortedResults.forEach { result ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val freqText = if (result.clusterFrequencies.isNotEmpty()) {
                        result.clusterFrequencies.toSortedMap().values.joinToString(" | ") { "${it / 1000}" }
                    } else {
                        "${result.frequencyKHz / 1000}"
                    }
                    
                    Text(
                        text = freqText, 
                        style = MaterialTheme.typography.bodyMedium, 
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "%.2f".format(result.powerW),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.0f".format(result.score),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

fun Modifier.rotate(degrees: Float) = this.then(
    Modifier.graphicsLayer(rotationZ = degrees)
)
