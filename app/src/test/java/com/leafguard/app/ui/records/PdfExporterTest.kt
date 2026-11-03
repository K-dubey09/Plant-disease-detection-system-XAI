package com.leafguard.app.ui.records

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.local.ScanEntity
import org.junit.Test

/**
 * Phase 8: JVM unit tests for [PdfExporter]'s pure formatting helpers - the record-line layout +
 * plant-name resolution that the PDF/CSV exports share. The actual [PdfExporter.export] uses
 * android.graphics.pdf.PdfDocument (not available on the host JVM), so it stays instrumented-only;
 * these tests cover the logic that decides what each row says. Confidences 0.5f / 0.25f are
 * exactly representable in float, so `(confidence * 100).toInt()` is deterministic (50 / 25).
 */
class PdfExporterTest {

    private val plantNames = mapOf(1L to "Balcony Tomato")

    @Test
    fun formatRecordLine_includesDatePlantConditionConfidence() {
        val scan = ScanEntity(
            id = 1,
            plantId = 1L,
            labelName = "Tomato___Late_blight",
            labelCondition = "Late blight",
            confidence = 0.5f,
            createdAt = 1_700_000_000_000L,
        )
        val line = PdfExporter.formatRecordLine(scan, plantNames)
        assertThat(line).contains("Balcony Tomato")
        assertThat(line).contains("Late blight")
        assertThat(line).contains("50%")
        assertThat(line).contains("2023")
    }

    @Test
    fun formatRecordLine_unattachedScanShowsUnattached() {
        val scan = ScanEntity(
            id = 2,
            plantId = null,
            labelName = "Apple___Apple_scab",
            labelCondition = "Apple scab",
            confidence = 0.25f,
            createdAt = 1_700_000_000_000L,
        )
        val line = PdfExporter.formatRecordLine(scan, emptyMap())
        assertThat(line).contains("Unattached")
        assertThat(line).contains("Apple scab")
        assertThat(line).contains("25%")
    }

    @Test
    fun displayName_resolvesPlantName() {
        val scan = ScanEntity(id = 1, plantId = 5L, labelName = "x", labelCondition = "y", confidence = 0.5f)
        assertThat(PdfExporter.displayName(scan, mapOf(5L to "P"))).isEqualTo("P")
    }

    @Test
    fun displayName_nullPlantIdReturnsUnattached() {
        val scan = ScanEntity(id = 1, plantId = null, labelName = "x", labelCondition = "y", confidence = 0.5f)
        assertThat(PdfExporter.displayName(scan, emptyMap())).isEqualTo("Unattached")
    }

    @Test
    fun displayName_unknownPlantIdReturnsUnattached() {
        val scan = ScanEntity(id = 1, plantId = 99L, labelName = "x", labelCondition = "y", confidence = 0.5f)
        assertThat(PdfExporter.displayName(scan, mapOf(1L to "A"))).isEqualTo("Unattached")
    }
}
