package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.dashboard.DashboardViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Dashboard (Phase 6). A stats summary built from the persisted scans + plants (total scans, plants
 * tracked, most-recent diagnosis with confidence + date), followed by the quick-action cards that
 * route to Scan / Plants / Images. The stats are live: saving a scan or plant anywhere in the app
 * updates this screen on return.
 */
@Composable
fun DashboardScreen(
    onOpenPlants: () -> Unit,
    onOpenImages: () -> Unit,
    onScan: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val recent = scans.firstOrNull()

    LeafScaffold(title = stringResource(R.string.nav_dashboard)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardStats(
                scanCount = scans.size,
                plantCount = plants.size,
                recentCondition = recent?.labelCondition,
                recentConfidence = recent?.confidence,
                recentDate = recent?.createdAt,
            )
            DashboardQuickAction(
                icon = Icons.Outlined.CameraAlt,
                title = stringResource(R.string.scan_title),
                subtitle = stringResource(R.string.dashboard_action_scan),
                onClick = onScan,
            )
            DashboardQuickAction(
                icon = Icons.Outlined.Spa,
                title = stringResource(R.string.nav_plants),
                subtitle = stringResource(R.string.dashboard_action_plants),
                onClick = onOpenPlants,
            )
            DashboardQuickAction(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.nav_images),
                subtitle = stringResource(R.string.dashboard_action_images),
                onClick = onOpenImages,
            )
            DashboardQuickAction(
                icon = Icons.Outlined.Info,
                title = "About LeafGuard",
                subtitle = "Version, model, and AI disclosure",
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
private fun DashboardStats(
    scanCount: Int,
    plantCount: Int,
    recentCondition: String?,
    recentConfidence: Float?,
    recentDate: Long?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatColumn(value = scanCount.toString(), label = stringResource(R.string.dashboard_stat_scans))
                StatColumn(value = plantCount.toString(), label = stringResource(R.string.dashboard_stat_plants))
                StatColumn(
                    value = if (recentConfidence != null) "${(recentConfidence * 100).toInt()}%" else "—",
                    label = stringResource(R.string.dashboard_stat_confidence),
                )
            }
            if (recentCondition != null) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_recent_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = recentCondition,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    recentDate?.let {
                        Text(
                            text = stringResource(R.string.dashboard_recent_date, formatEpoch(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.dashboard_no_scans),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardQuickAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatEpoch(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
