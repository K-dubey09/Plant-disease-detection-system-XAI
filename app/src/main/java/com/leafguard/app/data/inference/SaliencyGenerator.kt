package com.leafguard.app.data.inference

import android.graphics.Bitmap
import android.graphics.Color
import com.leafguard.app.domain.model.DiseaseLabel
import kotlin.math.sqrt

/**
 * Occlusion-sensitivity saliency: a model-agnostic explainability map that does NOT need access
 * to the model's internals (unlike true Grad-CAM, which requires the convolutional activations
 * and gradients TFLite's Interpreter does not expose). For each cell of a [gridSize]x[gridSize]
 * grid we grey out that region, re-run the classifier, and record how much the predicted class's
 * confidence drops. High-drop cells are the regions the model relied on -> the heatmap.
 *
 * Cost is [gridSize]^2 forward passes; we default to a coarse 8x8 grid (64 passes) so the
 * "Generating explanation…" step finishes in a few seconds on-device.
 *
 * Rendering: the coarse [gridSize]x[gridSize] drop field is smoothed with a separable 3x3 Gaussian
 * (clamp-to-edge) so the upscaled heatmap reads as a continuous field instead of a blocky 8x8
 * checkerboard, soft-normalized with sqrt so a single dominant peak no longer flattens every
 * mid-range cell into the cool floor, and colour-mapped through a perceptual
 * blue->cyan->green->yellow->red (matplotlib "jet"-style) ramp instead of the original's jumpy
 * two-stop blue->red, which routed through dark purple and hid the medium-importance regions the
 * clinician most needs to see.
 */
class SaliencyGenerator(
    private val classifier: LeafClassifier,
    private val gridSize: Int = DEFAULT_GRID,
) {
    fun generate(bitmap: Bitmap): Bitmap {
        val baseline = classifier.classifyForSaliency(bitmap)
        val target: DiseaseLabel = baseline.topLabel
        val baseConfidence = baseline.topConfidence

        val w = bitmap.width
        val h = bitmap.height
        val rects = occlusionRects(w, h, gridSize)
        val heat = FloatArray(gridSize * gridSize)
        for (rect in rects) {
            val occluded = occludeRegion(bitmap, rect)
            val after = classifier.classifyForSaliency(occluded)
            val afterConfidence = after.topK.firstOrNull { it.first == target }?.second ?: 0f
            val drop = (baseConfidence - afterConfidence).coerceAtLeast(0f)
            heat[rect.index] = drop
        }
        // renderHeatmap re-normalizes against the smoothed field, so a single hot cell cannot
        // saturate the colour ramp; the raw per-cell drops are all it needs.
        return renderHeatmap(heat, gridSize, w, h)
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
    ): Bitmap {
        val smoothed = smoothHeat(heat, grid)
        val norm = (smoothed.maxOrNull() ?: 0f).coerceAtLeast(1e-6f)
        val small = Bitmap.createBitmap(grid, grid, Bitmap.Config.ARGB_8888)
        for (i in smoothed.indices) {
            // sqrt soft-normalization widens the visible mid-range: a cell at 25% of the peak maps
            // to 50% on the ramp instead of 25%, so the regions the model leaned on moderately
            // still read as warm rather than vanishing into blue.
            val v = sqrt((smoothed[i] / norm).coerceIn(0f, 1f))
            small.setPixel(i % grid, i / grid, jetColor(v))
        }
        return Bitmap.createScaledBitmap(small, destW, destH, true)
    }

    /**
     * Separable [1,2,1]/4 Gaussian over the [grid]x[grid] field, clamp-to-edge at the borders.
     * Smoothing the raw per-cell drops before upscaling is what turns the 8x8 grid into a
     * continuous heatmap: without it, bilinear upscaling leaves visible cell seams and any single
     * noisy cell becomes a hard rectangle after the scale step.
     */
    private fun smoothHeat(heat: FloatArray, grid: Int): FloatArray {
        if (grid < 3) return heat.copyOf()
        val tmp = FloatArray(grid * grid)
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val l = heat[y * grid + (x - 1).coerceIn(0, grid - 1)]
                val c = heat[y * grid + x]
                val r = heat[y * grid + (x + 1).coerceIn(0, grid - 1)]
                tmp[y * grid + x] = (l + 2f * c + r) / 4f
            }
        }
        val out = FloatArray(grid * grid)
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val u = tmp[(y - 1).coerceIn(0, grid - 1) * grid + x]
                val c = tmp[y * grid + x]
                val d = tmp[(y + 1).coerceIn(0, grid - 1) * grid + x]
                out[y * grid + x] = (u + 2f * c + d) / 4f
            }
        }
        return out
    }

    /** Perceptual blue->cyan->green->yellow->red ramp (matplotlib "jet"-style), fully opaque. */
    private fun jetColor(v: Float): Int {
        val t = v.coerceIn(0f, 1f)
        val stops = arrayOf(
            floatArrayOf(0f, 0f, 255f),   // blue
            floatArrayOf(0f, 255f, 255f), // cyan
            floatArrayOf(0f, 255f, 0f),   // green
            floatArrayOf(255f, 255f, 0f), // yellow
            floatArrayOf(255f, 0f, 0f),   // red
        )
        val seg = (t * (stops.size - 1)).toInt().coerceIn(0, stops.size - 2)
        val f = t * (stops.size - 1) - seg
        val a = stops[seg]
        val b = stops[seg + 1]
        val r = (a[0] + (b[0] - a[0]) * f).toInt().coerceIn(0, 255)
        val g = (a[1] + (b[1] - a[1]) * f).toInt().coerceIn(0, 255)
        val bl = (a[2] + (b[2] - a[2]) * f).toInt().coerceIn(0, 255)
        return Color.argb(255, r, g, bl)
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
