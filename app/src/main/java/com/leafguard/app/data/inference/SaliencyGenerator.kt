package com.leafguard.app.data.inference

import android.graphics.Bitmap
import android.graphics.Color
import com.leafguard.app.domain.model.DiseaseLabel

/**
 * Occlusion-sensitivity saliency: a model-agnostic explainability map that does NOT need access
 * to the model's internals (unlike true Grad-CAM, which requires the convolutional activations
 * and gradients TFLite's Interpreter does not expose). For each cell of a [gridSize]x[gridSize]
 * grid we grey out that region, re-run the classifier, and record how much the predicted class's
 * confidence drops. High-drop cells are the regions the model relied on -> the heatmap.
 *
 * Cost is [gridSize]^2 forward passes; we default to a coarse 8x8 grid (64 passes) so the
 * "Generating explanation…" step finishes in a few seconds on-device.
 */
class SaliencyGenerator(
    private val classifier: DiseaseClassifier,
    private val gridSize: Int = DEFAULT_GRID,
) {
    fun generate(bitmap: Bitmap): Bitmap {
        val baseline = classifier.classify(bitmap)
        val target: DiseaseLabel = baseline.topLabel
        val baseConfidence = baseline.topConfidence

        val w = bitmap.width
        val h = bitmap.height
        val rects = occlusionRects(w, h, gridSize)
        val heat = FloatArray(gridSize * gridSize)
        var maxDrop = 0f
        for (rect in rects) {
            val occluded = occludeRegion(bitmap, rect)
            val after = classifier.classify(occluded)
            val afterConfidence = after.topK.firstOrNull { it.first == target }?.second ?: 0f
            val drop = (baseConfidence - afterConfidence).coerceAtLeast(0f)
            heat[rect.index] = drop
            if (drop > maxDrop) maxDrop = drop
        }
        return renderHeatmap(heat, gridSize, w, h, maxDrop)
    }

    private fun occludeRegion(src: Bitmap, rect: OcclusionRect): Bitmap {
        val copy = src.copy(Bitmap.Config.ARGB_8888, true)
        for (y in rect.y0 until rect.y1) {
            for (x in rect.x0 until rect.x1) {
                copy.setPixel(x, y, OCCLUSION_COLOR)
            }
        }
        return copy
    }

    private fun renderHeatmap(
        heat: FloatArray,
        grid: Int,
        destW: Int,
        destH: Int,
        maxDrop: Float,
    ): Bitmap {
        val small = Bitmap.createBitmap(grid, grid, Bitmap.Config.ARGB_8888)
        val norm = maxDrop.coerceAtLeast(1e-6f)
        for (i in heat.indices) {
            val v = (heat[i] / norm).coerceIn(0f, 1f)
            small.setPixel(i % grid, i / grid, heatColor(v))
        }
        return Bitmap.createScaledBitmap(small, destW, destH, true)
    }

    /** Blue (cool/unimportant) -> red (hot/important) ramp, fully opaque. */
    private fun heatColor(v: Float): Int {
        val r = (255f * v).toInt().coerceIn(0, 255)
        val b = (255f * (1f - v)).toInt().coerceIn(0, 255)
        return Color.argb(255, r, 0, b)
    }

    companion object {
        const val DEFAULT_GRID = 8
        private val OCCLUSION_COLOR = Color.argb(255, 128, 128, 128)
    }
}

/** One cell of the occlusion grid: [index] in a row-major [gridSize]x[gridSize] layout + pixel bounds. */
data class OcclusionRect(
    val index: Int,
    val gx: Int,
    val gy: Int,
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
)

/**
 * Tiles a [width]x[height] image into a [gridSize]x[gridSize] grid of [OcclusionRect]s in row-major
 * order. Pure (no Android types) so the tiling math is unit-tested: count == gridSize^2, cells are
 * non-overlapping, and their union covers the whole image.
 */
fun occlusionRects(width: Int, height: Int, gridSize: Int): List<OcclusionRect> {
    require(width > 0 && height > 0 && gridSize > 0)
    val out = ArrayList<OcclusionRect>(gridSize * gridSize)
    for (gy in 0 until gridSize) {
        for (gx in 0 until gridSize) {
            val x0 = (gx * width) / gridSize
            val x1 = ((gx + 1) * width) / gridSize
            val y0 = (gy * height) / gridSize
            val y1 = ((gy + 1) * height) / gridSize
            out.add(OcclusionRect(index = gy * gridSize + gx, gx = gx, gy = gy, x0 = x0, y0 = y0, x1 = x1, y1 = y1))
        }
    }
    return out
}
