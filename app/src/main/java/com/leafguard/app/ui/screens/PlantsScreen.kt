package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.components.PhasePlaceholder

/**
 * List of plants the user is actively tracking. Each row opens [PlantDetailScreen].
 * Backed by Room in Phase 3; the empty state and row adapter are scaffolded then.
 */
@Composable
fun PlantsScreen(
    onOpenPlant: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LeafScaffold(title = stringResource(R.string.nav_plants)) { padding ->
        PhasePlaceholder(
            title = "My plants",
            phase = 3,
            summary = "A list of the plants you are tracking. Tap one to view its scan history and scan a new leaf for it.",
            modifier = Modifier.padding(padding),
        )
    }
}
