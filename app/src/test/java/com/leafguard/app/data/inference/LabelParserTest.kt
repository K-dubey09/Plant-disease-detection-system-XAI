package com.leafguard.app.data.inference

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LabelParserTest {

    @Test
    fun parse_skipsBlankLines_andPreservesOrder() {
        val labels = LabelParser.parse("\n  Apple___Apple_scab \n\nTomato___healthy\n")
        assertThat(labels).hasSize(2)
        assertThat(labels.map { it.index }).containsExactly(0, 1).inOrder()
        assertThat(labels[0].name).isEqualTo("Apple___Apple_scab")
        assertThat(labels[1].name).isEqualTo("Tomato___healthy")
    }

    @Test
    fun parseLine_splitsPlantAndCondition() {
        val label = LabelParser.parseLine(3, "Tomato___Late_blight")
        assertThat(label.index).isEqualTo(3)
        assertThat(label.name).isEqualTo("Tomato___Late_blight")
        assertThat(label.plant).isEqualTo("Tomato")
        assertThat(label.condition).isEqualTo("Late blight")
    }

    @Test
    fun parseLine_noSeparator_fallsBackToName() {
        val label = LabelParser.parseLine(0, "Healthy")
        assertThat(label.plant).isEqualTo("Healthy")
        assertThat(label.condition).isEqualTo("Healthy")
        assertThat(label.name).isEqualTo("Healthy")
    }

    @Test
    fun parseLine_underscoresBecomeSpaces() {
        val label = LabelParser.parseLine(0, "Corn___Northern_Leaf_Blight")
        assertThat(label.condition).isEqualTo("Northern Leaf Blight")
    }
}
