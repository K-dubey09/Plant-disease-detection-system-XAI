package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.components.PhasePlaceholder

/**
 * Diagnosis result screen: top-1 class + confidence, the Grad-CAM explanation overlay, and
 * disease-specific treatment suggestions. Save action attaches the scan to a plant. The
 * layout is finalised in Phase 4 (inference + XAI) and Phase 7 (suggestions engine).
 */
@Composable
fun ResultScreen(
    scanId: Long,
    modifier: Modifier = Modifier,
) {
    LeafScaffold(title = "Diagnosis") { padding ->
        PhasePlaceholder(
            title = "Diagnosis & explanation",
            phase = 4,
            summary = "Predicted disease, confidence, a Grad-CAM heatmap showing which leaf region drove the prediction, and actionable treatment suggestions for scan #$scanId.",
            modifier = Modifier.padding(padding),
        )
    }
}
