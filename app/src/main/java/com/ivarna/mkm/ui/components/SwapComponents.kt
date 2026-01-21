package com.ivarna.mkm.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SwapConfigDialog(
    initialSize: Int = 1024,
    initialPath: String = "/data/local/tmp/swapfile",
    onDismiss: () -> Unit,
    onConfirm: (path: String, size: Int) -> Unit
) {
    var sizeText by remember { mutableStateOf(initialSize.toString()) }
    var pathText by remember { mutableStateOf(initialPath) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Swap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it },
                    label = { Text("Size (MB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Path") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Note: Shizuku or Root is required to apply these changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val size = sizeText.toIntOrNull() ?: 1024
                    onConfirm(pathText, size)
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
