package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.images.ImageGroup
import com.leafguard.app.ui.images.ImagesViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Phase 6 Images gallery. Groups persisted scans that have a stored source image by plant; each
 * group is a header (plant name + count) over a horizontally-scrolling row of cards, each card
 * showing the captured leaf ([ScanEntity.sourceImagePath]) above its occlusion-sensitivity overlay
 * ([ScanEntity.saliencyImagePath]) with the condition + date. Coil loads the image files off the
 * main thread. Scans from before Phase 6 (no stored image) do not appear.
 */
@Composable
fun ImagesScreen(
    viewModel: ImagesViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    LeafScaffold(title = stringResource(R.string.nav_images)) { padding ->
        if (groups.isEmpty()) {
            EmptyImagesState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items = groups, key = { it.plantId ?: -1L }) { group ->
                    ImageGroupSection(group = group)
                }
            }
        }
    }
}

@Composable
private fun ImageGroupSection(group: ImageGroup) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.images_group_header,
                group.plantName ?: stringResource(R.string.images_unattached),
                group.count,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(items = group.scans, key = { it.id }) { scan ->
                ImageCard(
                    sourcePath = scan.sourceImagePath,
                    saliencyPath = scan.saliencyImagePath,
                    condition = scan.labelCondition,
                    confidence = scan.confidence,
                    createdAt = scan.createdAt,
                )
            }
        }
    }
}

@Composable
private fun ImageCard(
    sourcePath: String?,
    saliencyPath: String?,
    condition: String,
    confidence: Float,
    createdAt: Long,
) {
    Card(
        modifier = Modifier.size(width = 160.dp, height = 340.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScanImage(
                path = sourcePath,
                contentDescription = stringResource(R.string.images_source_desc),
                label = stringResource(R.string.images_source_label),
            )
            ScanImage(
                path = saliencyPath,
                contentDescription = stringResource(R.string.images_overlay_desc),
                label = stringResource(R.string.images_overlay_label),
            )
            Text(
                text = stringResource(R.string.images_card_meta, condition, "${(confidence * 100).toInt()}%"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatEpoch(createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanImage(path: String?, contentDescription: String, label: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(
            model = path?.let { File(it) },
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyImagesState(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.images_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.images_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatEpoch(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
