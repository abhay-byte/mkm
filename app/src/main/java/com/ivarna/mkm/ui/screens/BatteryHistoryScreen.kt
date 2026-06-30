package com.ivarna.mkm.ui.screens
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivarna.mkm.ui.components.ClearHistoryDialog
import com.ivarna.mkm.ui.components.SessionHistoryRow
import com.ivarna.mkm.ui.viewmodel.BatteryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHistoryScreen(
    viewModel: BatteryViewModel,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val records by viewModel.history.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var expandedRecordId by remember { mutableStateOf<Long?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        stringResource(com.ivarna.mkm.R.string.session_history),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(com.ivarna.mkm.R.string.back)
                        )
                    }
                },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(com.ivarna.mkm.R.string.clear_history),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (records.isEmpty()) {
            EmptyHistoryState(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                HistorySummary(records = records)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(records, key = { it.id }) { record ->
                SessionHistoryRow(
                    record = record,
                    isExpanded = expandedRecordId == record.id,
                    onToggleExpand = {
                        expandedRecordId = if (expandedRecordId == record.id) null else record.id
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }

    if (showClearDialog) {
        ClearHistoryDialog(
            count = records.size,
            onConfirm = {
                viewModel.clearHistory()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun HistorySummary(records: List<com.ivarna.mkm.data.model.BatterySessionRecord>) {
    val charging = records.count { it.sessionType == com.ivarna.mkm.data.model.SessionType.CHARGING }
    val discharging = records.size - charging
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(com.ivarna.mkm.R.string.sessions_saved_count, records.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(com.ivarna.mkm.R.string.charging_discharging_summary_format, charging, discharging),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = stringResource(com.ivarna.mkm.R.string.no_sessions_recorded),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(com.ivarna.mkm.R.string.sessions_saved_auto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
