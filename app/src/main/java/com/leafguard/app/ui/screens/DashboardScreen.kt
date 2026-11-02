package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold

/**
 * Dashboard shell. The grid of quick-action cards is real and wired to navigation today;
 * the populated stats (scans this week, active plants, recent detections) are filled in
 * Phase 6 once the Room queries that back them exist.
 */
@Composable
fun DashboardScreen(
    onOpenPlants: () -> Unit,
    onOpenImages: () -> Unit,
    onScan: () -> Unit,
) {
    LeafScaffold(title = stringResource(R.string.nav_dashboard)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardQuickAction(
                icon = Icons.Outlined.CameraAlt,
                title = stringResource(R.string.scan_title),
                subtitle = "Scan a new leaf and diagnose instantly",
                onClick = onScan,
            )
            DashboardQuickAction(
                icon = Icons.Outlined.Spa,
                title = stringResource(R.string.nav_plants),
                subtitle = "Track a specific plant's history",
                onClick = onOpenPlants,
            )
            DashboardQuickAction(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.nav_images),
                subtitle = "Browse all captured scan images",
                onClick = onOpenImages,
            )
            Text(
                text = stringResource(R.string.placeholder_phase, "Dashboard", 6),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DashboardQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
