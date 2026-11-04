package com.leafguard.app.data.inference

import com.leafguard.app.domain.model.DiseaseLabel

/**
 * Parses labels.txt (one PlantVillage class per line, e.g. "Apple___Apple_scab") into
 * [DiseaseLabel]s in file order — that order is the model's output-column order. Pure (no Android
 * types) so it is unit-tested directly.
 */
object LabelParser {

    fun parse(text: String): List<DiseaseLabel> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line -> parseLine(index, line) }

    fun parseLine(index: Int, line: String): DiseaseLabel {
        val name = line.trim()
        val parts = name.split(SEPARATOR)
        val plant = if (parts.size >= 2) parts[0].trim() else name
        val rawCondition = if (parts.size >= 2) parts[1] else parts.getOrElse(0) { name }
        val condition = rawCondition.replace('_', ' ').replace("  ", " ").trim()
        return DiseaseLabel(index = index, name = name, plant = plant, condition = condition)
    }

    private const val SEPARATOR = "___"
}
