package com.ivarna.mkm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.R
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostState
import com.ivarna.mkm.ui.viewmodel.GameBoostViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoostScreen(viewModel: GameBoostViewModel = viewModel(), onOpenDrawer: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val message by viewModel.message.collectAsState()
    var showDisclosure by remember { mutableStateOf(false) }
    val owns = state !is GameBoostState.Off
    val transitioning = state is GameBoostState.Enabling || state is GameBoostState.Disabling

    Scaffold(
        topBar = {
            MediumTopAppBar(
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, stringResource(R.string.menu)) } },
                title = { Text(stringResource(R.string.game_boost), fontWeight = FontWeight.Black) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.game_boost), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.game_boost_desc), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.game_boost_toggle), fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = owns,
                                enabled = !transitioning && state !is GameBoostState.RecoveryRequired,
                                onCheckedChange = { enabled ->
                                    if (enabled && !viewModel.disclosureAcknowledged) showDisclosure = true
                                    else viewModel.toggle(enabled)
                                }
                            )
                        }
                        if (transitioning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            item {
                StatusCard(state, capabilities)
            }
            message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            if (state is GameBoostState.RecoveryRequired) {
                item { Button(onClick = viewModel::retryRecovery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.game_boost_retry_recovery)) } }
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.game_boost_disclosure_title)) },
            text = { Text(stringResource(R.string.game_boost_disclosure)) },
            confirmButton = { TextButton(onClick = { showDisclosure = false; viewModel.acknowledgeDisclosure(); viewModel.toggle(true) }) { Text(stringResource(R.string.continue_text)) } },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun StatusCard(state: GameBoostState, capabilities: com.ivarna.mkm.data.model.GameBoostCapabilities?) {
    val applied = when (state) {
        is GameBoostState.Active -> state.applied
        is GameBoostState.ThermalLimited -> state.stillApplied
        else -> emptySet()
    }
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.game_boost_status), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            listOf(
                GameBoostComponent.CPU_GOVERNOR to R.string.game_boost_cpu_governor,
                GameBoostComponent.CPU_MAX_LOCK to R.string.game_boost_cpu_max,
                GameBoostComponent.GPU_GOVERNOR to R.string.game_boost_gpu_governor,
                GameBoostComponent.GPU_MAX_LOCK to R.string.game_boost_gpu_max
            ).forEach { (component, label) ->
                val capability = capabilities?.components?.get(component)
                val value = when {
                    component in applied -> stringResource(R.string.game_boost_applied)
                    capability?.supported == false -> stringResource(R.string.game_boost_unsupported)
                    else -> stringResource(R.string.game_boost_not_applied)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(label)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (state) {
                is GameBoostState.Enabling -> Text(stringResource(R.string.game_boost_enabling, state.step))
                is GameBoostState.ThermalLimited -> Text(stringResource(R.string.game_boost_thermal_limited))
                is GameBoostState.RecoveryRequired -> Text(stringResource(R.string.game_boost_recovery_required), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }
}
