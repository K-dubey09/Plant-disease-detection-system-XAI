package com.leafguard.app.ui.records

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.local.ScanEntity
import org.junit.Test

class CsvGeneratorTest {

    private fun scan(
        id: Long,
        plantId: Long? = null,
        condition: String = "Early blight",
        confidence: Float = 0.92f,
        createdAt: Long = 1_000L,
    ) = ScanEntity(
        id = id, plantId = plantId, labelName = "Tomato___Early_blight",
        labelCondition = condition, confidence = confidence, createdAt = createdAt,
    )

    private val header = CsvGenerator.HEADER

    @Test
    fun generate_empty_isHeaderOnly() {
        assertThat(CsvGenerator.generate(emptyList(), emptyMap())).isEqualTo(header)
    }

    @Test
    fun generate_oneRow_resolvesPlantName() {
        val csv = CsvGenerator.generate(listOf(scan(1, plantId = 7L)), mapOf(7L to "Balcony Tomato"))
        val lines = csv.split('\n')
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo(header)
        assertThat(lines[1]).contains("Balcony Tomato")
        assertThat(lines[1]).contains("Early blight")
        assertThat(lines[1]).endsWith("92%")
    }

    @Test
    fun generate_unattachedPlant_rendersAsUnattached() {
        val csv = CsvGenerator.generate(listOf(scan(1, plantId = null)), emptyMap())
        assertThat(csv.split('\n')[1]).contains("Unattached")
    }

    @Test
    fun generate_unknownPlantId_rendersAsUnattached() {
        // A plant id not in the map (e.g. its plant was deleted) reads as Unattached.
        val csv = CsvGenerator.generate(listOf(scan(1, plantId = 99L)), mapOf(1L to "Other"))
        assertThat(csv.split('\n')[1]).contains("Unattached")
    }

    @Test
    fun generate_escapesCommaInCondition() {
        val csv = CsvGenerator.generate(
            listOf(scan(1, condition = "Early, blight")),
            emptyMap(),
        )
        // RFC 4180: a field containing a comma is wrapped in double quotes.
        assertThat(csv).contains("\"Early, blight\"")
    }

    @Test
    fun generate_escapesQuoteInCondition() {
        val csv = CsvGenerator.generate(
            listOf(scan(1, condition = "Early \"spot\" blight")),
            emptyMap(),
        )
        // Internal quotes are doubled inside a quoted field.
        assertThat(csv).contains("\"Early \"\"spot\"\" blight\"")
    }

    @Test
    fun generate_confidenceAsIntegerPercent() {
        val csv = CsvGenerator.generate(listOf(scan(1, confidence = 0.9859f)), emptyMap())
        assertThat(csv).contains("98%")
        assertThat(csv).doesNotContain("98.5")
    }

    @Test
    fun generate_preservesInputOrder() {
        val csv = CsvGenerator.generate(
            listOf(
                scan(1, condition = "First", createdAt = 9_000L),
                scan(2, condition = "Second", createdAt = 1_000L),
            ),
            emptyMap(),
        )
        val rows = csv.split('\n').drop(1)
        assertThat(rows[0]).contains("First")
        assertThat(rows[1]).contains("Second")
    }

    @Test
    fun generate_dateFormattedIsoLike() {
        // Noon UTC on 1970-01-01 stays on Jan 1 across any +/-12h timezone offset.
        val csv = CsvGenerator.generate(listOf(scan(1, createdAt = 43_200_000L)), emptyMap())
        assertThat(csv).contains("1970-01-01")
    }
}
