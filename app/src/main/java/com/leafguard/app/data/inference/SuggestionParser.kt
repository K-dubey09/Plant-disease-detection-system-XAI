package com.leafguard.app.data.inference

import com.leafguard.app.domain.model.Suggestion

/**
 * Parses disease_suggestions.json (a flat map of label-name -> suggestion object) into a
 * [String]-keyed [Suggestion] map. Uses org.json (Android runtime); the lookup logic itself is
 * exercised by [SuggestionEngineTest] against a hand-built map, so a parser regression only
 * affects runtime, not the unit suite.
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
                treatments = stringList(o, "treatments"),
                prevention = stringList(o, "prevention"),
            )
        }
        return out
    }

    private fun stringList(o: org.json.JSONObject, field: String): List<String> =
        if (o.has(field) && !o.isNull(field)) {
            val arr = o.getJSONArray(field)
            List(arr.length()) { arr.getString(it) }
        } else {
            emptyList()
        }
}
