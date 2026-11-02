package com.leafguard.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.components.PhasePlaceholder

/** Gallery of all captured scan images (original + Grad-CAM overlay). Backed by Room in Phase 6. */
@Composable
fun ImagesScreen(modifier: Modifier = Modifier) {
    LeafScaffold(title = stringResource(R.string.nav_images)) { padding ->
        PhasePlaceholder(
            title = "Image gallery",
            phase = 6,
            summary = "Every captured leaf image and its Grad-CAM explanation overlay, grouped by plant and date.",
            modifier = Modifier.padding(padding),
        )
    }
}
