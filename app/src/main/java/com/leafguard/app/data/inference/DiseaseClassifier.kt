package com.leafguard.app.data.inference

import android.graphics.Bitmap
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.DiseaseLabel
import org.tensorflow.lite.Interpreter

/**
 * Wraps the TFLite [Interpreter] for leaf-disease classification. Preprocesses the bitmap to the
 * model's expected [1, INPUT_SIZE, INPUT_SIZE, 3] float input in [0, 1] (the model contains its
 * own Rescaling to [-1, 1]); runs inference; returns the top-k labels with confidences.
 *
 * The shipped `plant_disease_mobilenetv2.tflite` is a MobileNetV2 backbone + 38-class head whose
 * head is UNTRAINED on PlantVillage, so its predictions are a pipeline demo, not reliable
 * diagnoses. The classifier code is production-shaped: swap the .tflite for a trained model and
 * the same path yields real results.
 */
class DiseaseClassifier(
    private val interpreter: Interpreter,
    private val labels: List<DiseaseLabel>,
    private val inputSize: Int = IMAGE_SIZE,
) {
    fun classify(bitmap: Bitmap): Diagnosis {
        val input = bitmapToInput(bitmap, inputSize)
        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, output)
        val probs = output[0]
        val ranked = labels.mapIndexed { i, label -> label to probs.getOrElse(i) { 0f } }
            .sortedByDescending { it.second }
            .take(TOP_K)
        return Diagnosis(
            topLabel = ranked.first().first,
            topConfidence = ranked.first().second,
            topK = ranked,
        )
    }

    /** Builds the [1][size][size][3] float input the interpreter expects, with pixels in [0, 1]. */
    fun bitmapToInput(bitmap: Bitmap, size: Int): Array<Array<Array<FloatArray>>> {
        val scaled = if (bitmap.width == size && bitmap.height == size) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val input = Array(1) { Array(size) { Array(size) { FloatArray(3) } } }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val rgb = pixelToNormalized(scaled.getPixel(x, y))
                input[0][y][x][0] = rgb[0]
                input[0][y][x][1] = rgb[1]
                input[0][y][x][2] = rgb[2]
            }
        }
        return input
    }

    companion object {
        const val IMAGE_SIZE = 224
        const val TOP_K = 3
    }
}

/**
 * Converts an Android ARGB pixel int to normalized [r, g, b] floats in [0, 1]. Pure (no Android
 * types beyond the int convention) so the bit-extraction math is unit-tested.
 */
fun pixelToNormalized(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return floatArrayOf(r, g, b)
}
