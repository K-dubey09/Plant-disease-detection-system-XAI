package com.leafguard.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.components.PhasePlaceholder

/**
 * Live leaf scanning via CameraX with on-device TFLite inference and Grad-CAM explanation.
 * Captures a frame, runs inference, and navigates to [ResultScreen]. Shipped in Phase 5
 * (camera pipeline) building on the Phase 4 inference + XAI engine.
 */
@Composable
fun ScanScreen(
    onResult: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LeafScaffold(title = stringResource(R.string.scan_title)) { padding ->
        PhasePlaceholder(
            title = "Scan a leaf",
            phase = 5,
            summary = "Live CameraX preview with capture. A captured frame is classified on-device and a Grad-CAM heatmap is generated, then the result is saved and opened.",
            modifier = Modifier.padding(padding),
        )
    }
}
