package com.leafguard.app.data.inference

import com.leafguard.app.domain.model.DiseaseLabel

/**
 * Parses `labels_jktk.txt` -- the 116 free-form class names of the JK-TK YOLOv11 detector (e.g.
 * "corn rust", "Tomato healthy", "Cassava Bacterial Blight") -- into [DiseaseLabel]s in file
 * order, which is the model's output-column order. Unlike [LabelParser] (which splits PlantVillage
 * `Plant___Condition` on `___`), these names carry no separator, so the plant is the leading token
 * (title-cased) and the condition is the remainder. Pure (no Android types) so it is unit-tested.
 */
object JktkLabelParser {

    /** Two-word plant names that would otherwise be mis-split on the first space. */
    private val MULTI_WORD_PLANTS = listOf("bell pepper", "sweet potato")

    fun parse(text: String): List<DiseaseLabel> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line -> parseLine(index, line) }

    fun parseLine(index: Int, line: String): DiseaseLabel {
        val plant = plantOf(line)
        val rest = line.substring(plant.length).trim()
        val condition = rest.ifEmpty { "leaf" }
        return DiseaseLabel(index = index, name = line, plant = plant, condition = condition)
    }

    private fun plantOf(line: String): String {
        val low = line.lowercase()
        val multi = MULTI_WORD_PLANTS.firstOrNull { low.startsWith(it) }
        if (multi != null) {
            return multi.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercaseChar() } }
        }
        return line.split(" ").first().replaceFirstChar { c -> c.uppercaseChar() }
    }
}
