package com.leafguard.app.data.inference

import com.leafguard.app.domain.model.DiseaseLabel
import com.leafguard.app.domain.model.Severity
import com.leafguard.app.domain.model.Suggestion

/**
 * Maps a predicted [DiseaseLabel] to advisory [Suggestion]s. Constructed with the parsed
 * suggestions map (so the lookup is pure and unit-testable without org.json). Phase 7 adds
 * [severityFor] so callers (e.g. a future Records severity column or a list badge) can read the
 * tier without pulling the whole [Suggestion].
 */
class SuggestionEngine(private val suggestions: Map<String, Suggestion>) {

    fun suggest(label: DiseaseLabel): Suggestion? = suggestions[label.name]

    /** The severity tier for a label, or null for an unknown label. */
    fun severityFor(label: DiseaseLabel): Severity? = suggestions[label.name]?.severity

    /** True when a label name exists in the suggestions map (handy for diagnostics/tests). */
    fun knows(label: DiseaseLabel): Boolean = label.name in suggestions

    val size: Int get() = suggestions.size
}
