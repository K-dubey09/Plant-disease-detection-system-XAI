package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.records.CsvGenerator
import com.leafguard.app.ui.records.PdfExporter
import com.leafguard.app.ui.records.PlantRecordGroup
import com.leafguard.app.ui.records.RecordsViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 6 Records. A per-plant disease log grouped from the persisted scans, with CSV and PDF
 * export. Each card is one plant (or "Unattached") with its scan rows (condition, confidence,
 * date); the cards sort newest-scan-first. Export writes to the app cache dir and shows the file
 * name + size inline (LeafScaffold has no snackbar host, so the result is a status banner).
 */
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMsg by remember { mutableStateOf<String?>(null) }
    val plantNames = remember(plants) { plants.associate { it.id to it.name } }
    val exportFailedMsg = stringResource(R.string.records_export_failed)

    LeafScaffold(title = stringResource(R.string.nav_records)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            exportMsg = withContext(Dispatchers.IO) {
                                writeCsv(context, CsvGenerator.generate(scans, plantNames))
                            }
                        }
                    },
                    enabled = scans.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Text(stringResource(R.string.records_export_csv), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            exportMsg = withContext(Dispatchers.IO) {
                                val file = PdfExporter.export(context, scans, plantNames)
                                if (file.exists()) "Saved PDF: ${file.name} (${file.length()} bytes)"
                                else exportFailedMsg
                            }
                        }
                    },
                    enabled = scans.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Text(stringResource(R.string.records_export_pdf), modifier = Modifier.padding(start = 8.dp))
                }
            }
            exportMsg?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (groups.isEmpty()) {
                EmptyRecordsState(Modifier.fillMaxSize().padding(24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = groups, key = { it.plantId ?: -1L }) { group ->
                        PlantRecordCard(group = group)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantRecordCard(group: PlantRecordGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.plantName ?: stringResource(R.string.records_unattached),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.records_group_meta,
                            group.count,
                            group.mostRecentAt?.let { formatEpoch(it) } ?: "",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer8()
            group.scans.forEach { scan ->
                Text(
                    text = stringResource(
                        R.string.records_scan_row,
                        scan.labelCondition,
                        "${(scan.confidence * 100).toInt()}%",
                        formatEpoch(scan.createdAt),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EmptyRecordsState(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.records_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.records_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatEpoch(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

/** Writes the CSV string to the cache dir; returns a human-readable status line. */
private suspend fun writeCsv(context: android.content.Context, csv: String): String =
    withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "leafguard_records_${System.currentTimeMillis()}.csv")
        file.writeText(csv)
        "Saved CSV: ${file.name} (${file.length()} bytes)"
    }
