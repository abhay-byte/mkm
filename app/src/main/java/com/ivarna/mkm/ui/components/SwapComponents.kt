package com.ivarna.mkm.ui.components
import androidx.compose.ui.res.stringResource

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
        title = { Text(stringResource(com.ivarna.mkm.R.string.configure_swap)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it },
                    label = { Text(stringResource(com.ivarna.mkm.R.string.size_mb)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text(stringResource(com.ivarna.mkm.R.string.path)) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Note removed as requested
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
                Text(stringResource(com.ivarna.mkm.R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.ivarna.mkm.R.string.cancel))
            }
        }
    )
}
