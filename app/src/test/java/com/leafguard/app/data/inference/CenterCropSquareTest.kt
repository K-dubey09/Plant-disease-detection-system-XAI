package com.leafguard.app.data.inference

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [centerCropSquare]: CameraX captures and gallery imports are typically non-square
 * (4:3 / 16:9), but the model was trained on square PlantVillage images, so [DiseaseClassifier]
 * center-crops to the smaller side before scaling to 224x224 (stretching a non-square bitmap to
 * 224x224 would distort the leaf). Runs under Robolectric for a real [Bitmap].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CenterCropSquareTest {

    @Test
    fun landscapeInput_cropsToSquareUsingTheSmallerSide() {
        val src = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val out = centerCropSquare(src)
        assertThat(out.width).isEqualTo(480)
        assertThat(out.height).isEqualTo(480)
    }

    @Test
    fun portraitInput_cropsToSquareUsingTheSmallerSide() {
        val src = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val out = centerCropSquare(src)
        assertThat(out.width).isEqualTo(480)
        assertThat(out.height).isEqualTo(480)
    }

    @Test
    fun alreadySquare_returnsOriginalUnchanged() {
        val src = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val out = centerCropSquare(src)
        assertThat(out.width).isEqualTo(224)
        assertThat(out.height).isEqualTo(224)
        // Already square -> returned as-is (same instance, no copy).
        assertThat(out).isSameInstanceAs(src)
    }

    @Test
    fun keepsTheCentreAndDropsTheMargins() {
        // A 5x3 bitmap; landscape -> 3x3 centred on the middle column. x0=(5-3)/2=1, y0=0,
        // so the crop covers columns 1..3 and the source's centre column (index 2) maps to the
        // crop's column 1. A red pixel placed at the source centre must survive the crop.
        val src = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.WHITE)
        src.setPixel(2, 1, Color.RED)
        val out = centerCropSquare(src)
        assertThat(out.width).isEqualTo(3)
        assertThat(out.height).isEqualTo(3)
        assertThat(out.getPixel(1, 1)).isEqualTo(Color.RED)
    }
}
