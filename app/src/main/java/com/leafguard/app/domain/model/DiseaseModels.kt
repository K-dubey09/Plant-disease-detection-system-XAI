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
 * Treatment/prevention guidance mapped from a label name via disease_suggestions.json. Authored
 * from general IPM knowledge — advisory, not a prescription.
 */
data class Suggestion(
    val name: String,
    val plant: String,
    val condition: String,
    val summary: String,
    val treatments: List<String>,
    val prevention: List<String>,
)
