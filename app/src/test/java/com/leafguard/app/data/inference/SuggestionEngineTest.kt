package com.leafguard.app.data.inference

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.domain.model.DiseaseLabel
import com.leafguard.app.domain.model.Suggestion
import org.junit.Test

class SuggestionEngineTest {

    private val suggestions = mapOf(
        "Tomato___Late_blight" to Suggestion(
            name = "Tomato___Late_blight",
            plant = "Tomato",
            condition = "Late blight (Phytophthora infestans)",
            summary = "Oomycete disease.",
            treatments = listOf("Apply a late-blight fungicide.", "Destroy infected plants."),
            prevention = listOf("Use certified transplants."),
        ),
        "Tomato___healthy" to Suggestion(
            name = "Tomato___healthy",
            plant = "Tomato",
            condition = "Healthy",
            summary = "No disease symptoms detected.",
            treatments = emptyList(),
            prevention = listOf("Continue routine care."),
        ),
    )

    private val engine = SuggestionEngine(suggestions)

    @Test
    fun suggest_knownLabel_returnsMatch() {
        val label = DiseaseLabel(index = 21, name = "Tomato___Late_blight", plant = "Tomato", condition = "Late blight")
        val s = engine.suggest(label)
        assertThat(s).isNotNull()
        assertThat(s!!.condition).isEqualTo("Late blight (Phytophthora infestans)")
        assertThat(s.treatments).hasSize(2)
    }

    @Test
    fun suggest_unknownLabel_returnsNull() {
        val label = DiseaseLabel(index = 99, name = "Mango___Anthracnose", plant = "Mango", condition = "Anthracnose")
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
}
