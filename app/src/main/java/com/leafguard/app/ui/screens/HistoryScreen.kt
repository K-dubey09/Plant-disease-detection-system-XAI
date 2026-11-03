package com.leafguard.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.history.HistoryItem
import com.leafguard.app.ui.history.HistoryViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Phase 6 scan history. A reverse-chronological feed (the DAO returns DESC by createdAt) of every
 * persisted diagnosis, with per-plant filter chips. Tapping a row opens [Routes.RESULT] for that
 * scan id. The plant name is resolved in [HistoryViewModel] from the plants list (the scan row
 * stores only the plant id), so a scan whose plant was deleted shows as "Unattached".
 */
@Composable
fun HistoryScreen(
    onOpenScan: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val selectedPlantId by viewModel.selectedPlantId.collectAsStateWithLifecycle()

    LeafScaffold(title = stringResource(R.string.nav_history)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HistoryFilterRow(
                plants = plants,
                selectedPlantId = selectedPlantId,
                onSelect = viewModel::setFilter,
            )
            if (items.isEmpty()) {
                EmptyHistoryState(Modifier.fillMaxSize().padding(24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = items, key = { it.scan.id }) { item ->
                        HistoryRow(item = item, onOpen = { onOpenScan(item.scan.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterRow(
    plants: List<com.leafguard.app.data.local.Plant>,
    selectedPlantId: Long?,
    onSelect: (Long?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedPlantId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.history_filter_all)) },
            )
        }
        items(items = plants, key = { it.id }) { plant ->
            FilterChip(
                selected = selectedPlantId == plant.id,
                onClick = { onSelect(plant.id) },
                label = { Text(plant.name) },
            )
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onOpen: () -> Unit) {
    val unattachedLabel = stringResource(R.string.history_unattached)
    val plantDisplay = item.plantName ?: unattachedLabel
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.scan.labelCondition,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.history_row_subtitle,
                        "${(item.scan.confidence * 100).toInt()}%",
                        plantDisplay,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.history_row_date, formatEpoch(item.scan.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatEpoch(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
