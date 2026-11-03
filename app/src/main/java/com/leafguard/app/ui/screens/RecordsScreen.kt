package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.components.PhasePlaceholder

/** Exportable structured records (per-plant disease log, CSV/PDF export). Backed by Room in Phase 6. */
@Composable
fun RecordsScreen(modifier: Modifier = Modifier) {
    LeafScaffold(title = stringResource(R.string.nav_records)) { padding ->
        PhasePlaceholder(
            title = "Records & export",
            phase = 6,
            summary = "Structured per-plant disease log with CSV and PDF export for agronomy reporting and compliance.",
            modifier = Modifier.padding(padding),
        )
    }
}
