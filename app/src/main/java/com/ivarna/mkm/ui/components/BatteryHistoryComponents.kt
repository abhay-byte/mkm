package com.ivarna.mkm.ui.components
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivarna.mkm.data.model.BatterySessionRecord
import com.ivarna.mkm.data.model.SessionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val historyDateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

fun formatHistoryTimestamp(timeMs: Long): String =
    historyDateFormat.format(Date(timeMs))

fun formatHistoryDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

@Composable
fun SessionHistoryRow(
    record: BatterySessionRecord,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCharging = record.sessionType == SessionType.CHARGING
    val accent = if (isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    val signedChange = if (isCharging) "+${record.percentChange}" else "−${record.percentChange.coerceAtLeast(0)}"
    val changeColor = if (isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isCharging) Icons.Default.Power else Icons.Default.BatteryFull,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = accent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isCharging) "Charging" else "Discharging",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Text(
                        text = "${formatHistoryDuration(record.totalDurationMs)} · ${formatHistoryTimestamp(record.endTimeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = signedChange + "%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                    Text(
                        text = "${record.startPercent}% → ${record.endPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else stringResource(com.ivarna.mkm.R.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                if (isCharging) {
                    // Charging session metrics
                    if (record.avgWattageW > 0f) {
                        HistoryDetailRow(
                            label = stringResource(com.ivarna.mkm.R.string.avg_power),
                            value = "+${"%.2f".format(record.avgWattageW)} W"
                        )
                    }
                    if (record.avgCurrentMa > 0) {
                        HistoryDetailRow(
                            label = stringResource(com.ivarna.mkm.R.string.avg_current),
                            value = "${record.avgCurrentMa} mA"
                        )
                    }
                    HistoryDetailRow(
                        label = stringResource(com.ivarna.mkm.R.string.avg_temperature),
                        value = "${"%.1f".format(record.avgTemperatureC)}°C"
                    )
                    if (record.screenOnTimeMs > 0) {
                        HistoryDetailRow(
                            label = stringResource(com.ivarna.mkm.R.string.screen_on),
                            value = formatHistoryDuration(record.screenOnTimeMs)
                        )
                    }
                    if (record.screenOffTimeMs > 0) {
                        HistoryDetailRow(
                            label = stringResource(com.ivarna.mkm.R.string.screen_off),
                            value = formatHistoryDuration(record.screenOffTimeMs)
                        )
                    }
                } else {
                    // Discharging session metrics
                    HistoryDetailRow(
                        label = "Active drain",
                        value = "${"%.2f".format(record.activeDrainPerHr)}%/hr"
                    )
                    HistoryDetailRow(
                        label = "Idle drain",
                        value = "${"%.2f".format(record.idleDrainPerHr)}%/hr"
                    )
                    HistoryDetailRow(
                        label = "Avg current",
                        value = "${record.avgCurrentMa} mA"
                    )
                    HistoryDetailRow(
                        label = "Avg power",
                        value = "${"%.2f".format(record.avgWattageW)} W"
                    )
                    HistoryDetailRow(
                        label = "Avg temperature",
                        value = "${"%.1f".format(record.avgTemperatureC)}°C"
                    )
                    // Absolute battery % drained per state
                    val capacityMah = if (record.estimatedCapacityMah > 0) record.estimatedCapacityMah
                                      else record.ratedCapacityMah
                    fun pctToMah(pct: Float): String {
                        if (capacityMah <= 0 || pct <= 0f) return ""
                        return " · ~${(pct / 100f * capacityMah).toInt()} mAh"
                    }
                    HistoryDetailRow(
                        label = "Screen on",
                        value = formatHistoryDuration(record.screenOnTimeMs) +
                                if (record.screenOnDrainPercent > 0f)
                                    " · −${"%.1f".format(record.screenOnDrainPercent)}%${pctToMah(record.screenOnDrainPercent)}" else ""
                    )
                    HistoryDetailRow(
                        label = "Screen off",
                        value = formatHistoryDuration(record.screenOffTimeMs) +
                                if (record.screenOffDrainPercent > 0f)
                                    " · −${"%.1f".format(record.screenOffDrainPercent)}%${pctToMah(record.screenOffDrainPercent)}" else ""
                    )
                    HistoryDetailRow(
                        label = stringResource(com.ivarna.mkm.R.string.deep_sleep),
                        value = formatHistoryDuration(record.deepSleepTimeMs) +
                                if (record.deepSleepDrainPercent > 0f)
                                    " · −${"%.2f".format(record.deepSleepDrainPercent)}%${pctToMah(record.deepSleepDrainPercent)}" else ""
                    )
                    if (record.awakeTimeMs > 0) {
                        HistoryDetailRow(
                            label = stringResource(com.ivarna.mkm.R.string.awake),
                            value = formatHistoryDuration(record.awakeTimeMs) +
                                    if (record.awakeDrainPercent > 0f)
                                        " · −${"%.2f".format(record.awakeDrainPercent)}%${pctToMah(record.awakeDrainPercent)}" else ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ClearHistoryDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(com.ivarna.mkm.R.string.clear_session_history)) },
        text = {
            Text(
                "This will permanently remove all $count saved session${if (count == 1) "" else "s"}. " +
                    "This action cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(com.ivarna.mkm.R.string.clear), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.ivarna.mkm.R.string.cancel))
            }
        }
    )
}
