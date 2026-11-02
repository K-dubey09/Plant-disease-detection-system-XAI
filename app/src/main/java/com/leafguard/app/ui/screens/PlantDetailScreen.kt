package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.components.PhasePlaceholder

/**
 * Per-plant detail: metadata, scan timeline, and the "scan this plant" entry point that
 * attaches the next diagnosis to this plant's history. Real content (Room queries, timeline
 * list, image gallery) arrives in Phase 3 and Phase 6.
 */
@Composable
fun PlantDetailScreen(
    plantId: Long,
    onScanForPlant: (Long) -> Unit,
) {
    LeafScaffold(title = stringResource(R.string.nav_plants)) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PhasePlaceholder(
                title = "Plant detail",
                phase = 3,
                summary = "Scan timeline, notes, and metadata for plant #$plantId. " +
                    "The action below runs a live scan and attaches the result to this plant.",
            )
            ExtendedFloatingActionButton(
                onClick = { onScanForPlant(plantId) },
                icon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null) },
                text = { Text(stringResource(R.string.scan_capture)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }
}

