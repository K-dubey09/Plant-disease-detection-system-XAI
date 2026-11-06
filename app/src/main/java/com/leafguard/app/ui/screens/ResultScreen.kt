package com.leafguard.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.R
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.local.Plant
import com.leafguard.app.domain.model.Severity
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.scan.ResultViewModel

/** Confidence fraction below which the result screen shows a "low confidence" advisory banner. */
private const val LOW_CONFIDENCE_THRESHOLD = 0.5f

/**
 * Shows the latest scan's prediction, confidence, an occlusion-sensitivity heatmap explaining
 * which image regions drove the prediction, and advisory treatment/prevention. Falls back to a
 * "no scan yet" state if the holder is empty (e.g. process death).
 *
 * Phase 9: the heatmap is streamed in after the diagnosis is shown, so while
 * [ScanResult.saliency] is null the heatmap slot renders a "Generating explanation…" placeholder
 * instead of blocking the whole screen. The "Add to plant" action lets the user link an unattached
 * scan to any plant (or create a new plant from it) so it joins that plant's per-plant Records
 * growth timeline.
 */
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onScanAnother: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val attachedPlantId by viewModel.attachedPlantId.collectAsStateWithLifecycle()

    LeafScaffold(title = stringResource(R.string.result_title), onBack = onBack) { padding ->
        when (val r = result) {
            null -> NoScanState(Modifier.fillMaxSize().padding(padding))
            else -> ResultContent(
                result = r,
                plants = plants,
                attachedPlantId = attachedPlantId,
                modelTampered = viewModel.modelTampered,
                onAttachToPlant = viewModel::attachToPlant,
                onCreatePlantAndAttach = viewModel::createPlantAndAttach,
                onScanAnother = onScanAnother,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun ResultContent(
    result: ScanResult,
    plants: List<Plant>,
    attachedPlantId: Long?,
    modelTampered: Boolean,
    onAttachToPlant: (Long?) -> Unit,
    onCreatePlantAndAttach: (String, String) -> Unit,
    onScanAnother: () -> Unit,
    modifier: Modifier,
) {
    var showAttachDialog by remember { mutableStateOf(false) }
    val attachedPlantName = remember(plants, attachedPlantId) {
        attachedPlantId?.let { id -> plants.firstOrNull { it.id == id }?.name }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Model integrity warning: the bundled .tflite's SHA-256 no longer matches the build-time
        // digest (modified after signing). Surfaced BEFORE the diagnosis so a tampered-model result
        // is read as unverified, without hiding the rest of the screen.
        if (modelTampered) {
            ModelIntegrityBanner()
        }

        // Compact model-transparency card: tells the user which model + that TTA was used. Replaces
        // the v1 demo banner (which advertised the retired Swin-Tiny + PlantVillage-only accuracy).
        ModelInfoCard()

        // Headline prediction + confidence.
        val top = result.diagnosis
        Text(
            text = result.suggestion?.condition ?: top.topLabel.condition,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        ConfidenceDisplay(
            plant = top.topLabel.plant,
            confidence = top.topConfidence,
        )

        // Low-confidence banner: the model is uncertain on this photo. When the top probability is
        // below the threshold, surface that honestly and suggest framing that matches the training
        // distribution (single leaf, plain background, fills the frame).
        if (top.topConfidence < LOW_CONFIDENCE_THRESHOLD) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = stringResource(R.string.result_low_confidence_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Source + saliency side by side. The heatmap slot streams in after the diagnosis: while
        // saliency is null (the 64-pass SaliencyGenerator still running in the background) we show a
        // placeholder so the user already has their diagnosis + advisory instead of a stuck spinner.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledImage(
                label = stringResource(R.string.result_image_label),
                bitmap = result.source,
                modifier = Modifier.weight(1f),
            )
            SaliencySlot(
                saliency = result.saliency,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.result_heatmap_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Alternatives: each runner-up gets its own label + mini confidence bar so the user can see
        // how much less likely each is relative to the headline, not just a bullet.
        Text(stringResource(R.string.result_alternatives), style = MaterialTheme.typography.titleMedium)
        top.topK.drop(1).forEach { (label, conf) ->
            Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label.condition, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        percent(conf),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { conf.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        }

        // Advisory.
        val suggestion = result.suggestion
        if (suggestion != null) {
            SeverityBadge(suggestion.severity)
            Text(stringResource(R.string.result_summary), style = MaterialTheme.typography.titleMedium)
            Text(suggestion.summary, style = MaterialTheme.typography.bodyMedium)
            if (suggestion.severity == Severity.HEALTHY) {
                Text(
                    text = stringResource(R.string.result_healthy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                suggestion.treatmentStages.forEach { stage ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.result_stage_label, stage.stage),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    stage.steps.forEach { step ->
                        Text("• $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (suggestion.prevention.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.result_prevention),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
                suggestion.prevention.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        // Phase 9 growth tracking: link this scan to a plant so it joins that plant's Records timeline.
        AttachToPlantSection(
            attachedPlantName = attachedPlantName,
            onOpenDialog = { showAttachDialog = true },
        )

        Button(
            onClick = onScanAnother,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text(stringResource(R.string.result_scan_another), modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showAttachDialog) {
        AttachToPlantDialog(
            plants = plants,
            onDismiss = { showAttachDialog = false },
            onAttachExisting = { plantId ->
                onAttachToPlant(plantId)
                showAttachDialog = false
            },
            onCreateNew = { name, species ->
                onCreatePlantAndAttach(name, species)
                showAttachDialog = false
            },
        )
    }
}

/**
 * The heatmap half of the source/saliency pair. While [ScanResult.saliency] is null the slow
 * occlusion-sensitivity pass is still running in the background; we show a placeholder card with a
 * spinner so the diagnosis above is already useful instead of the whole screen waiting on the
 * heatmap. Once [ScanResultHolder.updateSaliency] streams the bitmap in, the slot swaps to it.
 */
@Composable
private fun SaliencySlot(saliency: android.graphics.Bitmap?, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(8.dp).height(180.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (saliency != null) {
                Image(
                    bitmap = saliency.asImageBitmap(),
                    contentDescription = stringResource(R.string.result_heatmap_label),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = 160.dp),
                )
            } else {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.result_heatmap_loading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            stringResource(R.string.result_heatmap_label),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LabeledImage(label: String, bitmap: android.graphics.Bitmap, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = 180.dp),
            )
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * The "Add to plant" trigger. When the scan is already linked, shows the plant name + a "Change
 * plant" button; otherwise shows a "Save to plant" button that opens the picker dialog.
 */
@Composable
private fun AttachToPlantSection(
    attachedPlantName: String?,
    onOpenDialog: () -> Unit,
) {
    if (attachedPlantName != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.result_attached_to, attachedPlantName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.result_attach_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onOpenDialog) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                    Text(stringResource(R.string.result_change_plant), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    } else {
        Button(
            onClick = onOpenDialog,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(stringResource(R.string.result_save_to_plant), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * Picker: choose an existing plant, or create a new plant (name + species) and attach the scan to
 * it as its first recorded state. Confirm is disabled until a valid selection exists (an existing
 * plant id, or a non-blank name for the create-new option).
 */
@Composable
private fun AttachToPlantDialog(
    plants: List<Plant>,
    onDismiss: () -> Unit,
    onAttachExisting: (Long) -> Unit,
    onCreateNew: (String, String) -> Unit,
) {
    var createNew by remember { mutableStateOf(plants.isEmpty()) }
    var selectedPlantId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("") }

    val canConfirm = if (createNew) name.isNotBlank() else selectedPlantId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.result_attach_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.result_attach_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (plants.isEmpty()) {
                    Text(
                        stringResource(R.string.result_attach_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    plants.forEach { plant ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = !createNew && selectedPlantId == plant.id,
                                onClick = { createNew = false; selectedPlantId = plant.id },
                            ).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = !createNew && selectedPlantId == plant.id, onClick = null)
                            Text(plant.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = createNew,
                        onClick = { createNew = true; selectedPlantId = null },
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = createNew, onClick = null)
                    Text(stringResource(R.string.result_save_new), modifier = Modifier.padding(start = 8.dp))
                }
                if (createNew) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.result_attach_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = species,
                        onValueChange = { species = it },
                        label = { Text(stringResource(R.string.result_attach_species)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    if (createNew) onCreateNew(name.trim(), species.trim())
                    else selectedPlantId?.let(onAttachExisting)
                },
            ) { Text(stringResource(R.string.result_attach_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.result_attach_cancel)) }
        },
    )
}

@Composable
private fun NoScanState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.result_none),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SeverityBadge(severity: Severity) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = when (severity) {
        Severity.HEALTHY -> scheme.tertiaryContainer
        Severity.LOW -> scheme.primaryContainer
        Severity.MEDIUM -> scheme.secondaryContainer
        Severity.HIGH -> scheme.errorContainer
        Severity.CRITICAL -> scheme.error
    }
    val contentColor = when (severity) {
        Severity.CRITICAL -> scheme.onError
        else -> scheme.onSurfaceVariant
    }
    val tier = stringResource(severityLabelRes(severity))
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(R.string.result_severity_label, tier),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private fun severityLabelRes(severity: Severity): Int = when (severity) {
    Severity.HEALTHY -> R.string.severity_healthy
    Severity.LOW -> R.string.severity_low
    Severity.MEDIUM -> R.string.severity_medium
    Severity.HIGH -> R.string.severity_high
    Severity.CRITICAL -> R.string.severity_critical
}

private fun percent(f: Float): String = "${(f.coerceIn(0f, 1f) * 100).toInt()}%"

/**
 * v2.5.1.0: model-transparency card. Replaces the v1 "demo" banner (which advertised the retired
 * Swin-Tiny + PlantVillage-only accuracy) with the actual model in use + a verified-integrity chip,
 * so the user knows what produced their diagnosis and that it has not been tampered with.
 */
@Composable
private fun ModelInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.result_model_info),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.result_model_ok),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * v2.5.1.0: integrity-warning banner shown when [ResultViewModel.modelTampered] is true (the
 * bundled .tflite SHA-256 no longer matches the build-time digest). Placed above the diagnosis so a
 * tampered-model result is read as unverified; the rest of the screen stays usable.
 */
@Composable
private fun ModelIntegrityBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    text = stringResource(R.string.result_model_tampered_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.result_model_tampered_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/**
 * v2.5.1.0: richer confidence display. A prominent percent + a qualitative tier (High / Moderate /
 * Low) sits above the linear bar, so the user gets an at-a-glance read of how much to trust the
 * headline instead of parsing a bare number. Tier bands match [LOW_CONFIDENCE_THRESHOLD].
 */
@Composable
private fun ConfidenceDisplay(plant: String, confidence: Float) {
    val scheme = MaterialTheme.colorScheme
    val tierRes = when {
        confidence >= 0.7f -> R.string.result_confidence_tier_high
        confidence >= LOW_CONFIDENCE_THRESHOLD -> R.string.result_confidence_tier_moderate
        else -> R.string.result_confidence_tier_low
    }
    val tierColor = when {
        confidence >= 0.7f -> scheme.primary
        confidence >= LOW_CONFIDENCE_THRESHOLD -> scheme.tertiary
        else -> scheme.error
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.result_confidence_value, plant, percent(confidence)),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Surface(shape = RoundedCornerShape(50), color = tierColor.copy(alpha = 0.12f)) {
                Text(
                    text = stringResource(tierRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tierColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        LinearProgressIndicator(
            progress = { confidence.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = tierColor,
        )
    }
}
