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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.leafguard.app.domain.model.Severity
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.scan.ResultViewModel

/**
 * Shows the latest scan's prediction, confidence, an occlusion-sensitivity heatmap explaining
 * which image regions drove the prediction, and advisory treatment/prevention. Falls back to a
 * "no scan yet" state if the holder is empty (e.g. process death).
 */
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onScanAnother: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()

    LeafScaffold(title = stringResource(R.string.result_title), onBack = onBack) { padding ->
        when (val r = result) {
            null -> NoScanState(Modifier.fillMaxSize().padding(padding))
            else -> ResultContent(r, Modifier.fillMaxSize().padding(padding), onScanAnother)
        }
    }
}

@Composable
private fun ResultContent(
    result: ScanResult,
    modifier: Modifier,
    onScanAnother: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Text(
                text = stringResource(R.string.scan_demo_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }

        // Headline prediction + confidence.
        val top = result.diagnosis
        Text(
            text = result.suggestion?.condition ?: top.topLabel.condition,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.result_confidence_value, top.topLabel.plant, percent(top.topConfidence)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { top.topConfidence.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )

        // Source + saliency side by side.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledImage(
                label = stringResource(R.string.result_image_label),
                bitmap = result.source,
                modifier = Modifier.weight(1f),
            )
            LabeledImage(
                label = stringResource(R.string.result_heatmap_label),
                bitmap = result.saliency,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.result_heatmap_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Alternatives.
        Text(stringResource(R.string.result_alternatives), style = MaterialTheme.typography.titleMedium)
        top.topK.drop(1).forEach { (label, conf) ->
            Text(
                "• ${label.condition} — ${percent(conf)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
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

        Button(
            onClick = onScanAnother,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            androidx.compose.material3.Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text(stringResource(R.string.result_scan_another), modifier = Modifier.padding(start = 8.dp))
        }
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
