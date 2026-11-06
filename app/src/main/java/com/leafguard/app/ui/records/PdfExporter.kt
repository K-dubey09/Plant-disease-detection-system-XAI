package com.leafguard.app.ui.records

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.leafguard.app.data.local.ScanEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device PDF export of the per-plant disease log (Phase 6 Records). Renders an A4 document with
 * a title, a generation timestamp, and one row per scan (date, plant, condition, confidence),
 * paginating when the cursor runs off the page.
 *
 * [writeTo] streams the document into any [OutputStream] -- the canonical path used by the Records
 * screen, which writes to a user-picked location via the Storage Access Framework
 * (`ActivityResultContracts.CreateDocument`). [export] is retained as a cache-dir convenience that
 * returns a [File], and is now a thin wrapper over [writeTo].
 *
 * This uses the Android framework [PdfDocument] / [Paint], so [writeTo] / [export] are not exercisable
 * in a JVM unit test (no Android graphics on the host); they are compiled + lint-checked by CI and
 * exercised at runtime. The CSV layout -- the structured, parseable export -- is the unit-tested path
 * via [CsvGenerator]. [formatRecordLine] and [displayName] are pure and unit-tested.
 */
object PdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val LINE_H = 16f
    private const val BOTTOM_LIMIT = PAGE_H - MARGIN

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Streams the records PDF into [out]. The caller owns the stream lifecycle (e.g. `.use { ... }`). */
    fun writeTo(out: OutputStream, scans: List<ScanEntity>, plantNames: Map<Long, String>) {
        val doc = PdfDocument()
        try {
            val title = Paint().apply {
                textSize = 18f; setFakeBoldText(true); color = Color.parseColor("#3b3a36")
            }
            val meta = Paint().apply { textSize = 10f; color = Color.GRAY }
            val body = Paint().apply { textSize = 10f; color = Color.BLACK }
            val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
            var canvas = page.canvas
            var y = MARGIN + 18f

            canvas.drawText("LeafGuard Scan Records", MARGIN, y, title); y += 22f
            canvas.drawText("Generated ${DATE_FMT.format(Date())}  -  ${scans.size} scan(s)", MARGIN, y, meta)
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += LINE_H

            for (scan in scans) {
                if (y > BOTTOM_LIMIT) {
                    doc.finishPage(page)
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, page.info.pageNumber + 1).create())
                    canvas = page.canvas
                    y = MARGIN + LINE_H
                }
                canvas.drawText(formatRecordLine(scan, plantNames), MARGIN, y, body)
                y += LINE_H
            }
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Writes the records PDF to the app cache dir and returns the file. */
    fun export(context: Context, scans: List<ScanEntity>, plantNames: Map<Long, String>): File {
        val file = File(context.cacheDir, "leafguard_records_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { writeTo(it, scans, plantNames) }
        return file
    }

    /** The formatted single-line record for a scan (date, plant, condition, confidence %). */
    internal fun formatRecordLine(scan: ScanEntity, plantNames: Map<Long, String>): String =
        "${DATE_FMT.format(Date(scan.createdAt))}  ${displayName(scan, plantNames)}  " +
            "${scan.labelCondition}  ${(scan.confidence * 100).toInt()}%"

    internal fun displayName(scan: ScanEntity, plantNames: Map<Long, String>): String =
        scan.plantId?.let { plantNames[it] } ?: "Unattached"
}
