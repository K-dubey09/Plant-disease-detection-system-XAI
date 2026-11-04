package com.leafguard.app.data.inference

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.domain.model.Severity
import java.io.File
import org.junit.Test

/**
 * Integrity tests for the production disease_suggestions.json. Runs [SuggestionParser] against the
 * real asset file on the JVM via the real org.json (added as a testImplementation so it shadows
 * Android's stubbed org.json on the unit-test classpath). This is the guard that every one of the
 * 38 model classes has authored Phase 7 content (severity + staged treatments), so a half-authored
 * or malformed JSON fails CI rather than shipping.
 *
 * The asset file is read by relative path (AGP unit tests run with the module dir as the working
 * directory); a fallback path covers a root-CWD run.
 */
class SuggestionParserTest {

    private val parsed: Map<String, com.leafguard.app.domain.model.Suggestion> =
        SuggestionParser.parse(assetText("models/disease_suggestions.json"))

    @Test
    fun parse_hasAll38Entries() {
        assertThat(parsed).hasSize(38)
    }

    @Test
    fun parse_everyLabelsTxtKeyPresent() {
        val labels = assetText("models/labels.txt").lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertThat(labels).hasSize(38)
        for (label in labels) {
            assertThat(parsed).containsKey(label)
        }
    }

    @Test
    fun parse_26DiseaseEntriesHaveThreeStagesWithSteps() {
        val diseases = parsed.values.filter { it.severity != Severity.HEALTHY }
        assertThat(diseases).hasSize(26)
        for (s in diseases) {
            assertThat(s.treatmentStages).hasSize(3)
            for (stage in s.treatmentStages) {
                assertThat(stage.stage).isNotEmpty()
                assertThat(stage.steps).isNotEmpty()
            }
        }
    }

    @Test
    fun parse_12HealthyEntriesAreHealthyWithEmptyStages() {
        val healthy = parsed.values.filter { it.severity == Severity.HEALTHY }
        assertThat(healthy).hasSize(12)
        for (s in healthy) {
            assertThat(s.treatmentStages).isEmpty()
        }
    }

    @Test
    fun parse_criticalEntriesAreTheThreeIncurableLethal() {
        val critical = parsed.filterValues { it.severity == Severity.CRITICAL }.keys
        assertThat(critical).containsExactly(
            "Orange___Haunglongbing_(Citrus_greening)",
            "Tomato___Tomato_Yellow_Leaf_Curl_Virus",
            "Tomato___Tomato_mosaic_virus",
        )
    }

    @Test
    fun parse_stageOrderIsImmediateShortLong() {
        val s = parsed["Tomato___Late_blight"]!!
        assertThat(s.treatmentStages.map { it.stage })
            .containsExactly("Immediate", "Short-term", "Long-term").inOrder()
    }

    @Test
    fun parse_preventionNonEmptyForEveryEntry() {
        for (s in parsed.values) {
            assertThat(s.prevention).isNotEmpty()
        }
    }

    @Test
    fun parse_conditionAndSummaryNonEmptyForEveryEntry() {
        for (s in parsed.values) {
            assertThat(s.condition).isNotEmpty()
            assertThat(s.summary).isNotEmpty()
        }
    }

    private fun assetText(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("asset not found: $name (tried ${candidates.map { it.absolutePath }})")
        return file.readText()
    }
}
