package com.leafguard.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.LeafGuardApplication
import com.leafguard.app.R
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.records.CsvGenerator
import com.leafguard.app.ui.records.PdfExporter
import com.leafguard.app.ui.records.PlantRecordGroup
import com.leafguard.app.ui.records.RecordsViewModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 6 Records. A per-plant disease log grouped from the persisted scans, with CSV and PDF
 * export. Each card is one plant (or "Unattached") with its scan rows (condition, confidence,
 * date); the cards sort newest-scan-first.
 *
 * v2.7.0.0 export: both CSV and PDF are written to a location the USER picks via the Storage
 * Access Framework (`ActivityResultContracts.CreateDocument`), so the file lands somewhere
 * reachable (e.g. Downloads) instead of the private cache dir. A system notification is posted on
 * completion (the `exports` channel, created in [LeafGuardApplication]); `POST_NOTIFICATIONS` is
 * requested on entry for Android 13+. An inline status banner still confirms the result in-app.
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
    val csvDoneMsg = stringResource(R.string.records_export_csv_done)
    val pdfDoneMsg = stringResource(R.string.records_export_pdf_done)

    // Holds the payload between the button tap and the SAF picker callback (the picker is async).
    var pending by remember { mutableStateOf<PendingExport?>(null) }

    // Best-effort POST_NOTIFICATIONS for Android 13+: if denied, notify() is a no-op (no crash).
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result ignored; notifications are best-effort */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        val req = pending
        pending = null
        if (uri != null && req is PendingExport.Csv) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writeToUri(context, uri, req.csv.toByteArray()) }
                exportMsg = if (ok) {
                    postExportNotification(context, csvDoneMsg)
                    csvDoneMsg
                } else exportFailedMsg
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri: Uri? ->
        val req = pending
        pending = null
        if (uri != null && req is PendingExport.Pdf) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    writePdfToUri(context, uri, req.scans, req.plantNames)
                }
                exportMsg = if (ok) {
                    postExportNotification(context, pdfDoneMsg)
                    pdfDoneMsg
                } else exportFailedMsg
            }
        }
    }

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
                        if (scans.isNotEmpty()) {
                            pending = PendingExport.Csv(CsvGenerator.generate(scans, plantNames))
                            csvLauncher.launch(exportFileName("csv"))
                        }
                    },
                    enabled = scans.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Text(stringResource(R.string.records_export_csv), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        if (scans.isNotEmpty()) {
                            pending = PendingExport.Pdf(scans, plantNames)
                            pdfLauncher.launch(exportFileName("pdf"))
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

/** Carries the export payload from the button tap to the async SAF picker callback. */
private sealed interface PendingExport {
    data class Csv(val csv: String) : PendingExport
    data class Pdf(
        val scans: List<ScanEntity>,
        val plantNames: Map<Long, String>,
    ) : PendingExport
}

private fun exportFileName(extension: String): String =
    "leafguard_records_${System.currentTimeMillis()}.$extension"

/** Writes [bytes] to the user-picked [uri] via ContentResolver. Returns false on any failure. */
private fun writeToUri(context: Context, uri: Uri, bytes: ByteArray): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
} catch (e: Exception) {
    false
}

/** Renders the records PDF straight into the user-picked [uri] via ContentResolver. */
private fun writePdfToUri(
    context: Context,
    uri: Uri,
    scans: List<ScanEntity>,
    plantNames: Map<Long, String>,
): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { PdfExporter.writeTo(it, scans, plantNames) } != null
} catch (e: Exception) {
    false
}

/** Posts a best-effort completion notification on the `exports` channel (created at app start). */
private fun postExportNotification(context: Context, message: String) {
    val mgr = NotificationManagerCompat.from(context)
    if (!mgr.areNotificationsEnabled()) return
    val notification = NotificationCompat.Builder(context, LeafGuardApplication.EXPORT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    mgr.notify(EXPORT_NOTIFICATION_ID, notification)
}

private const val EXPORT_NOTIFICATION_ID = 4270

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
