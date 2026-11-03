package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.components.PhasePlaceholder

/** Chronological feed of every scan across all plants. Backed by Room in Phase 6. */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    LeafScaffold(title = stringResource(R.string.nav_history)) { padding ->
        PhasePlaceholder(
            title = "Scan history",
            phase = 6,
            summary = "A reverse-chronological feed of every diagnosis across all tracked plants, filterable by plant and by disease.",
            modifier = Modifier.padding(padding),
        )
    }
}
