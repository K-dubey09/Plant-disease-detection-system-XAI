package com.leafguard.app.data.inference

import android.graphics.Bitmap
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.DiseaseLabel
import org.tensorflow.lite.Interpreter

/**
 * Wraps the TFLite [Interpreter] for leaf-disease classification. Preprocesses the bitmap to the
 * model's expected [1, INPUT_SIZE, INPUT_SIZE, 3] float input in [0, 1] (the model contains its
 * own ImageNet normalization baked into the graph); runs inference; returns the top-k labels with
 * confidences.
 *
 * The shipped `plant_disease.tflite` is a Swin-Tiny transformer + 38-class head fine-tuned on
 * PlantVillage to 98.59% validation accuracy (source: surgeonwz/plant-village on HuggingFace).
 * The [0,1]->ImageNet-normalized rescaling and the final Softmax are baked into the .tflite graph
 * itself, so this code feeds raw [0,1] pixels and reads real probabilities -- no app-side
 * preprocessing or postprocessing. See models/conversion/ for the PyTorch->ONNX->TFLite pipeline.
 *
 * Trust: TFLite's [Interpreter] does not validate the model bytes, so a swapped or corrupt .tflite
 * still produces confident labels. Runtime integrity is verified separately by [ModelIntegrity]
 * (SHA-256 of this model's buffer against a build-time digest); a mismatch surfaces a warning
 * banner on the scan/result screens, since a tampered model's diagnoses are untrustworthy however
 * confident the softmax reads.
 *
 * Thread-safety: TFLite's [Interpreter] is NOT thread-safe -- two concurrent [Interpreter.run]
 * calls crash the native runtime. The regression this fixed: a user starts a scan, leaves it
 * (the fast-path classify completes, Done is emitted, and the 64-pass occlusion heatmap keeps
 * running on an application-scoped coroutine), then starts a SECOND scan from the scan page; the
 * second scan's fast-path classify ran `interpreter.run` on a different IO thread while the first
 * scan's heatmap was still mid-`run`, and the two collided -> native crash. [classify] now
 * serializes the single forward pass through `synchronized(interpreter)`. The lock is held only
 * for the duration of one `run` (not across the heatmap's 64 passes), so a waiting classify
 * interleaves between the heatmap's forward passes and waits at most ~one pass (~50-100 ms)
 * instead of crashing or blocking for the whole heatmap.
 */
class DiseaseClassifier(
    private val interpreter: Interpreter,
    private val labels: List<DiseaseLabel>,
    private val inputSize: Int = IMAGE_SIZE,
) : LeafClassifier {
    override fun classify(bitmap: Bitmap): Diagnosis {
        val input = bitmapToInput(bitmap, inputSize)
        val output = Array(1) { FloatArray(labels.size) }
        // TFLite's Interpreter is not thread-safe; serialize the native forward pass so a new
        // scan's classify cannot race the previous scan's in-flight occlusion heatmap.
        synchronized(interpreter) {
            interpreter.run(input, output)
        }
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

    /** Builds the [1][size][size][3] float input the interpreter expects, with pixels in [0, 1].
     *  Center-crops to a square first (see [centerCropSquare]) so a non-square camera/gallery
     *  bitmap is not stretched to size x size, which would distort the leaf. */
    fun bitmapToInput(bitmap: Bitmap, size: Int): Array<Array<Array<FloatArray>>> {
        val square = centerCropSquare(bitmap)
        val scaled = if (square.width == size && square.height == size) {
            square
        } else {
            Bitmap.createScaledBitmap(square, size, size, true)
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

/**
 * Center-crops [bitmap] to a square (the smaller of width/height), returning the original bitmap
 * unchanged if it is already square. The model was trained on square PlantVillage images, but
 * CameraX captures and gallery imports are typically non-square (4:3 / 16:9); scaling a non-square
 * bitmap directly to 224x224 stretches the leaf and distorts the features the classifier relies
 * on -- a plausible contributor to real-world misclassification. Cropping to the central square
 * first (the margins are usually background) preserves aspect ratio before the scale. The math is
 * unit-tested under Robolectric.
 */
fun centerCropSquare(bitmap: Bitmap): Bitmap {
    if (bitmap.width == bitmap.height) return bitmap
    val side = minOf(bitmap.width, bitmap.height)
    val x0 = (bitmap.width - side) / 2
    val y0 = (bitmap.height - side) / 2
    return Bitmap.createBitmap(bitmap, x0, y0, side, side)
}
