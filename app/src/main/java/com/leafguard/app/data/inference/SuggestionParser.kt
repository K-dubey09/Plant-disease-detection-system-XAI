package com.leafguard.app.data.inference

import com.leafguard.app.domain.model.Severity
import com.leafguard.app.domain.model.Suggestion
import com.leafguard.app.domain.model.TreatmentStage

/**
 * Parses disease_suggestions.json (a flat map of label-name -> suggestion object) into a
 * [String]-keyed [Suggestion] map. Phase 7 reads a `severity` tier and stage-aware
 * `treatmentStages` (each {stage, steps}); for backward compatibility, a legacy flat `treatments`
 * array (no `treatmentStages`) is wrapped as a single "Immediate" stage, and a missing `severity`
 * degrades to MEDIUM. Uses org.json (Android runtime); the lookup + parse logic is exercised by
 * [SuggestionEngineTest] and [SuggestionParserTest] (the latter via a real org.json on the JVM test
 * classpath, so the production JSON's structure is covered by the unit suite).
 */
object SuggestionParser {

    fun parse(text: String): Map<String, Suggestion> {
        val root = org.json.JSONObject(text)
        val out = LinkedHashMap<String, Suggestion>(root.length())
        for (key in root.keys()) {
            val o = root.getJSONObject(key)
            out[key] = Suggestion(
                name = o.optString("name", key),
                plant = o.optString("plant", ""),
                condition = o.optString("condition", ""),
                summary = o.optString("summary", ""),
                severity = Severity.fromString(if (o.has("severity") && !o.isNull("severity")) o.getString("severity") else null),
                treatmentStages = treatmentStages(o),
                prevention = stringList(o, "prevention"),
            )
        }
        return out
    }

    private fun treatmentStages(o: org.json.JSONObject): List<TreatmentStage> {
        if (o.has("treatmentStages") && !o.isNull("treatmentStages")) {
            val arr = o.getJSONArray("treatmentStages")
            return List(arr.length()) { i ->
                val s = arr.getJSONObject(i)
                TreatmentStage(stage = s.optString("stage", ""), steps = stringList(s, "steps"))
            }
        }
        // Backward compat: a legacy flat `treatments` array becomes one Immediate stage.
        val flat = stringList(o, "treatments")
        return if (flat.isEmpty()) emptyList() else listOf(TreatmentStage("Immediate", flat))
    }

    private fun stringList(o: org.json.JSONObject, field: String): List<String> =
        if (o.has(field) && !o.isNull(field)) {
            val arr = o.getJSONArray(field)
            List(arr.length()) { arr.getString(it) }
        } else {
            emptyList()
        }
}
