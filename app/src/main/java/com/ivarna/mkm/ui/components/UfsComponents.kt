package com.ivarna.mkm.ui.components
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.mkm.data.model.UfsStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UfsTuningCard(
    ufs: UfsStatus,
    onGovernorSelected: (String, String) -> Unit,
    onMinFreqSelected: (String, String) -> Unit,
    onMaxFreqSelected: (String, String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Column(modifier = Modifier.weight(1f)) {
                    val title = if (ufs.isSupported) stringResource(com.ivarna.mkm.R.string.ufs_controller) + ": " + ufs.controllerPath.substringAfterLast("/") else stringResource(com.ivarna.mkm.R.string.ufs_controller)
                    val subtitle = if (ufs.isSupported) ufs.controllerPath else stringResource(com.ivarna.mkm.R.string.not_detected_or_supported)
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                     Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (ufs.isSupported) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(com.ivarna.mkm.R.string.frequency_governor),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        value = ufs.currentGovernor,
                        onValueChange = {},
                        label = { Text(stringResource(com.ivarna.mkm.R.string.select_mode)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        ufs.availableGovernors.forEach { gov ->
                            DropdownMenuItem(
                                text = { Text(gov) },
                                onClick = {
                                    onGovernorSelected(ufs.controllerPath, gov)
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                if (ufs.availableFrequencies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(com.ivarna.mkm.R.string.frequencies),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Current: ${ufs.currentFreq}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(com.ivarna.mkm.R.string.freq_instability_warning),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Min Freq Dropdown
                     var minExpanded by remember { mutableStateOf(false) }
                     ExposedDropdownMenuBox(
                        expanded = minExpanded,
                        onExpandedChange = { minExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = ufs.minFreq,
                            onValueChange = {},
                            label = { Text(stringResource(com.ivarna.mkm.R.string.min_frequency)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = minExpanded,
                            onDismissRequest = { minExpanded = false },
                        ) {
                            ufs.availableFrequencies.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq) },
                                    onClick = {
                                        onMinFreqSelected(ufs.controllerPath, freq)
                                        minExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Max Freq Dropdown
                    var maxExpanded by remember { mutableStateOf(false) }
                     ExposedDropdownMenuBox(
                        expanded = maxExpanded,
                        onExpandedChange = { maxExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = ufs.maxFreq,
                            onValueChange = {},
                            label = { Text(stringResource(com.ivarna.mkm.R.string.max_frequency)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = maxExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = maxExpanded,
                            onDismissRequest = { maxExpanded = false },
                        ) {
                            ufs.availableFrequencies.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq) },
                                    onClick = {
                                        onMaxFreqSelected(ufs.controllerPath, freq)
                                        maxExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                }

                if (ufs.availableGovernors.isEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(com.ivarna.mkm.R.string.no_governors_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    if (ufs.debugInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = stringResource(com.ivarna.mkm.R.string.debug_info),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = ufs.debugInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(com.ivarna.mkm.R.string.no_ufs_controller),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}