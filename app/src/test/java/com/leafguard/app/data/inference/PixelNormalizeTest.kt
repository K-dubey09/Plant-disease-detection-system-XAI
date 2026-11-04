package com.leafguard.app.data.inference

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PixelNormalizeTest {

    private fun approx(f: Float, v: Float) = assertThat(f).isWithin(1e-4f).of(v)

    @Test
    fun whitePixel_normalizesToOne() {
        val rgb = pixelToNormalized(0xFFFFFFFF.toInt())
        approx(rgb[0], 1f); approx(rgb[1], 1f); approx(rgb[2], 1f)
    }

    @Test
    fun blackPixel_normalizesToZero() {
        val rgb = pixelToNormalized(0xFF000000.toInt())
        approx(rgb[0], 0f); approx(rgb[1], 0f); approx(rgb[2], 0f)
    }

    @Test
    fun midPixel_extractsCorrectChannels() {
        // ARGB 0xFF800080 -> R=128, G=0, B=128 -> 128/255 ≈ 0.502
        val rgb = pixelToNormalized(0xFF800080.toInt())
        approx(rgb[0], 128f / 255f)
        approx(rgb[1], 0f)
        approx(rgb[2], 128f / 255f)
    }

    @Test
    fun channels_inUnitRange() {
        for (argb in intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF112233.toInt(), 0xFFABCDEF.toInt())) {
            val rgb = pixelToNormalized(argb)
            rgb.forEach { assertThat(it).isAtLeast(0f); assertThat(it).isAtMost(1f) }
        }
    }
}
