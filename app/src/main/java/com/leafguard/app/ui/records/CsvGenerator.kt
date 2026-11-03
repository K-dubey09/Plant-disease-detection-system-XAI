package com.leafguard.app.ui.records

import com.leafguard.app.data.local.ScanEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure CSV generator for the per-plant disease log (Phase 6 Records export). RFC 4180 quoting:
 * a field is wrapped in double quotes when it contains a comma, quote, or newline, and any
 * internal quote is doubled. Output is header + one row per scan in the order given (the DAO
 * returns DESC by createdAt, so the CSV reads newest-first). Kept dependency-free so it is
 * unit-testable on the JVM.
 */
object CsvGenerator {

    const val HEADER = "Date,Plant,Condition,Confidence"

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * @param scans the scans to export (newest-first; ordering is preserved).
     * @param plantNames id -> display name for attached plants; missing ids and null plantIds
     *   render as "Unattached".
     */
    fun generate(scans: List<ScanEntity>, plantNames: Map<Long, String>): String {
        val sb = StringBuilder(HEADER.length + scans.size * 64)
        sb.append(HEADER)
        for (scan in scans) {
            sb.append('\n')
            sb.append(field(formatDate(scan.createdAt))).append(',')
            sb.append(field(plantName(scan.plantId, plantNames))).append(',')
            sb.append(field(scan.labelCondition)).append(',')
            sb.append(field("${(scan.confidence * 100).toInt()}%"))
        }
        return sb.toString()
    }

    /** RFC 4180: quote a field iff it contains comma, quote, or newline; double internal quotes. */
    private fun field(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun plantName(plantId: Long?, plantNames: Map<Long, String>): String =
        plantId?.let { plantNames[it] } ?: "Unattached"

    private fun formatDate(epochMillis: Long): String = DATE_FMT.format(Date(epochMillis))
}
