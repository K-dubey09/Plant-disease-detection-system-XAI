package com.leafguard.app.data.inference

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.domain.model.DiseaseLabel
import com.leafguard.app.domain.model.Severity
import com.leafguard.app.domain.model.Suggestion
import com.leafguard.app.domain.model.TreatmentStage
import org.junit.Test

class SuggestionEngineTest {

    private val suggestions = mapOf(
        "Tomato___Late_blight" to Suggestion(
            name = "Tomato___Late_blight",
            plant = "Tomato",
            condition = "Late blight (Phytophthora infestans)",
            summary = "Oomycete disease.",
            severity = Severity.HIGH,
            treatmentStages = listOf(
                TreatmentStage("Immediate", listOf("Pull and destroy infected plants.", "Apply a late-blight fungicide.")),
                TreatmentStage("Short-term", listOf("Continue sprays every 5-7 days.")),
                TreatmentStage("Long-term", listOf("Plant resistant varieties.")),
            ),
            prevention = listOf("Use certified transplants."),
        ),
        "Tomato___healthy" to Suggestion(
            name = "Tomato___healthy",
            plant = "Tomato",
            condition = "Healthy",
            summary = "No disease symptoms detected.",
            severity = Severity.HEALTHY,
            treatmentStages = emptyList(),
            prevention = listOf("Continue routine care."),
        ),
    )

    private val engine = SuggestionEngine(suggestions)

    @Test
    fun suggest_knownLabel_returnsMatchWithSeverityAndStages() {
        val s = engine.suggest(DiseaseLabel(21, "Tomato___Late_blight", "Tomato", "Late blight"))!!
        assertThat(s.condition).isEqualTo("Late blight (Phytophthora infestans)")
        assertThat(s.severity).isEqualTo(Severity.HIGH)
        assertThat(s.treatmentStages).hasSize(3)
        assertThat(s.treatmentStages[0].stage).isEqualTo("Immediate")
        assertThat(s.treatmentStages[0].steps).hasSize(2)
    }

    @Test
    fun suggest_unknownLabel_returnsNull() {
        val label = DiseaseLabel(99, "Mango___Anthracnose", "Mango", "Anthracnose")
        assertThat(engine.suggest(label)).isNull()
    }

    @Test
    fun knows_reflectsMembership() {
        assertThat(engine.knows(DiseaseLabel(0, "Tomato___healthy", "Tomato", "healthy"))).isTrue()
        assertThat(engine.knows(DiseaseLabel(0, "Mango___Anthracnose", "Mango", "Anthracnose"))).isFalse()
    }

    @Test
    fun size_countsEntries() {
        assertThat(engine.size).isEqualTo(2)
    }

    @Test
    fun severityFor_known_returnsTier() {
        assertThat(engine.severityFor(DiseaseLabel(21, "Tomato___Late_blight", "Tomato", "Late blight")))
            .isEqualTo(Severity.HIGH)
    }

    @Test
    fun severityFor_unknown_returnsNull() {
        assertThat(engine.severityFor(DiseaseLabel(99, "Mango___Anthracnose", "Mango", "Anthracnose"))).isNull()
    }

    @Test
    fun suggest_healthyLabel_returnsHealthySeverityAndEmptyStages() {
        val s = engine.suggest(DiseaseLabel(0, "Tomato___healthy", "Tomato", "healthy"))!!
        assertThat(s.severity).isEqualTo(Severity.HEALTHY)
        assertThat(s.treatmentStages).isEmpty()
    }
}
