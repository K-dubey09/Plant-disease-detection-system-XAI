package com.leafguard.app.domain.model

/**
 * One class the model can output. [name] is the raw PlantVillage label (e.g. "Apple___Apple_scab",
 * matching labels.txt and the model's output index); [plant] and [condition] are the human-readable
 * split used in the UI. [index] is the model output column this label maps to.
 */
data class DiseaseLabel(
    val index: Int,
    val name: String,
    val plant: String,
    val condition: String,
)

/**
 * A completed classification. [topLabel]/[topConfidence] is the headline; [topK] holds the next
 * most likely labels so the result screen can show alternatives.
 */
data class Diagnosis(
    val topLabel: DiseaseLabel,
    val topConfidence: Float,
    val topK: List<Pair<DiseaseLabel, Float>>,
)

/**
 * Severity tier for a diagnosed condition (Phase 7). [HEALTHY] is the benign baseline for the
 * `___healthy` classes; the rest rise in urgency. The result screen colors the severity badge by
 * this value (green / blue / amber / orange / red), and the Records export carries it as a column.
 */
enum class Severity {
    HEALTHY, LOW, MEDIUM, HIGH, CRITICAL;

    companion object {
        /** Case-insensitive parse with a fallback so a misspelled/missing JSON value degrades to MEDIUM. */
        fun fromString(value: String?): Severity =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: MEDIUM
    }
}

/**
 * One stage of stage-aware treatment advice (Phase 7): a label ("Immediate", "Short-term",
 * "Long-term") and its ordered steps. A disease's [Suggestion.treatmentStages] lists these in the
 * order the user should act; a healthy leaf has an empty list.
 */
data class TreatmentStage(
    val stage: String,
    val steps: List<String>,
)

/**
 * Treatment/prevention guidance mapped from a label name via disease_suggestions.json. Authored
 * from general IPM knowledge - advisory, not a prescription. Phase 7 adds a [severity] tier and
 * stage-aware [treatmentStages] (Immediate / Short-term / Long-term) in place of the prior flat
 * treatments list; [prevention] remains the forward-looking checklist.
 */
data class Suggestion(
    val name: String,
    val plant: String,
    val condition: String,
    val summary: String,
    val severity: Severity,
    val treatmentStages: List<TreatmentStage>,
    val prevention: List<String>,
)
