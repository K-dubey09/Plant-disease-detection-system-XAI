package com.leafguard.app.data.inference

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.DiseaseLabel
import io.mockk.every
import io.mockk.mockk
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix 1: the occlusion heatmap's rendering quality. The original [SaliencyGenerator] upscaled the
 * raw 8x8 drop field straight through a two-stop blue->red ramp normalized by the single max drop,
 * which produced a blocky checkerboard whose mid-range regions collapsed into the cool floor. The
 * new pipeline smooths the field with a 3x3 Gaussian, soft-normalizes with sqrt, and colour-maps
 * through a perceptual blue->cyan->green->yellow->red ("jet") ramp.
 *
 * The mock classifier models a model whose decision is driven by the centre of the leaf: occluding
 * near the centre crashes confidence (big drop -> hot), occluding near a corner barely moves it
 * (small drop -> cool). A correct heatmap must therefore be warm at the centre and cool at the
 * corners, and must match the input dimensions (it is composited 1:1 over the source photo).
 *
 * Runs under Robolectric for a real [Bitmap]; [DiseaseClassifier] is mocked with MockK since it
 * wraps a TFLite Interpreter we cannot construct in a unit test. SDK 34 matches the other
 * Robolectric tests (Robolectric 4.13).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaliencyGeneratorTest {

    private val target = DiseaseLabel(0, "Tomato___Late_blight", "Tomato", "Late blight")
    private val size = 24

    @Test
    fun generate_producesInputSizedHeatmapThatIsHotAtTheDecisionCentre() {
        val classifier = mockk<DiseaseClassifier>()
        every { classifier.classify(any()) } answers { diagnoseForOcclusion(firstArg()) }
        val generator = SaliencyGenerator(classifier, gridSize = 8)

        val source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val heat = generator.generate(source)

        // The overlay must match the source dimensions so the result screen composites it 1:1.
        assertThat(heat.width).isEqualTo(size)
        assertThat(heat.height).isEqualTo(size)

        // The centre (where occluding crashed confidence) must be warmer than the corners (where
        // occluding barely moved it). "Warmer" on the jet ramp == a higher red channel.
        val centreRed = Color.red(heat.getPixel(size / 2, size / 2))
        val cornerRed = Color.red(heat.getPixel(0, 0))
        assertThat(centreRed).isGreaterThan(cornerRed)

        // And the map is not a flat fill: centre and corner genuinely differ.
        assertThat(heat.getPixel(size / 2, size / 2)).isNotEqualTo(heat.getPixel(0, 0))
    }

    @Test
    fun generate_isOpaqueAndNonBlankEvenForAUniformModel() {
        // A model whose confidence is identical for every occlusion: no spatial structure, every
        // cell records the same drop. Smoothing + the jet ramp must still yield a real (opaque,
        // non-transparent) overlay of the right size, not a blank/transparent bitmap.
        val classifier = mockk<DiseaseClassifier>()
        every { classifier.classify(any()) } returns Diagnosis(target, 0.5f, listOf(target to 0.5f))
        val generator = SaliencyGenerator(classifier, gridSize = 8)

        val source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val heat = generator.generate(source)

        assertThat(heat.width).isEqualTo(size)
        assertThat(heat.height).isEqualTo(size)
        // jetColor is fully opaque (alpha 255) for every value, so every pixel must be opaque.
        assertThat(Color.alpha(heat.getPixel(size / 2, size / 2))).isEqualTo(255)
    }

    /**
     * Baseline (un-occluded source, no grey pixels) -> 0.9 confidence. Occluded -> confidence falls
     * with how close the greyed block is to the bitmap centre: centre occlusion ~0.1 (big drop,
     * hot), corner occlusion ~0.8 (small drop, cool).
     */
    private fun diagnoseForOcclusion(bmp: Bitmap): Diagnosis {
        val grey = greyCentroid(bmp)
        val conf = if (grey == null) {
            0.9f
        } else {
            val cx = bmp.width / 2f
            val cy = bmp.height / 2f
            val maxDist = sqrt(cx * cx + cy * cy)
            val dist = sqrt((grey.first - cx).pow(2) + (grey.second - cy).pow(2)) / maxDist
            0.1f + 0.7f * dist
        }
        return Diagnosis(target, conf, listOf(target to conf))
    }

    /** Centroid of the (128,128,128) occlusion block, or null if the bitmap has none (the baseline). */
    private fun greyCentroid(bmp: Bitmap): Pair<Float, Float>? {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                val c = bmp.getPixel(x, y)
                if (Color.red(c) == 128 && Color.green(c) == 128 && Color.blue(c) == 128) {
                    sx += x
                    sy += y
                    n++
                }
            }
        }
        return if (n == 0) null else (sx / n) to (sy / n)
    }
}
