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
 * The shipped `plant_disease.tflite` is the DINOv2 ViT-S/14 fine-tune v3.5.0.0 (38 PlantVillage
 * classes; 86.7% field / 99.3% lab / 96.1% mixed, verified bit-identical to the source ONNX in ONNX
 * Runtime -- argmax 3/3, max|diff|=1e-6). It is a drop-in for the prior Swin-Tiny asset: same
 * [1,224,224,3] float32 [0,1] input with ImageNet normalization and the final Softmax baked into the
 * graph, same 38-class output order as labels.txt, so this code feeds raw [0,1] pixels and reads real
 * probabilities -- no app-side preprocessing or postprocessing. See models/conversion/ for the
 * PyTorch->ONNX->TFLite pipeline.
 *
 * Test-time augmentation: [classify] runs the v3.5 eval TTA recipe verbatim -- three views (orig,
 * horizontal flip, scale0.875) whose softmaxes are averaged -- so on-device field accuracy tracks the
 * validated 86.7% rather than the single-crop ~84%. Each view is one `interpreter.run`; all three
 * share the [synchronized] lock (below). [classifyForSaliency] overrides the interface default with a
 * single center-crop pass: the 64-cell occlusion heatmap re-invokes the classifier per cell, and TTA
 * on every occluded patch would triple its cost for no explainability gain (the per-cell confidence
 * drop is the signal, not per-view confidence).
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
    /**
     * Headline classification with 3-view test-time augmentation (orig + hflip + scale0.875), the
     * exact recipe the v3.5 model was validated with at 86.7% field accuracy. The three softmaxes are
     * averaged before ranking so a flip- or scale-ambiguous leaf does not collapse onto a single
     * view's confident-but-wrong label. Each view is one `interpreter.run` under the shared
     * [synchronized] lock so an in-flight occlusion heatmap interleaves between views, not after all
     * three (worst-case wait ~one pass, as before).
     */
    override fun classify(bitmap: Bitmap): Diagnosis {
        val views = listOf(
            bitmapToInput(bitmap, inputSize),
            flipInputH(bitmapToInput(bitmap, inputSize)),
            bitmapToInput(scaleCropSquare(bitmap, SCALE_0_875), inputSize),
        )
        val acc = FloatArray(labels.size)
        val output = Array(1) { FloatArray(labels.size) }
        for (view in views) {
            synchronized(interpreter) {
                interpreter.run(view, output)
            }
            for (i in acc.indices) acc[i] += output[0][i]
        }
        val n = views.size.toFloat()
        for (i in acc.indices) acc[i] /= n
        return rankedDiagnosis(acc)
    }

    /** Single center-crop pass for the occlusion-saliency loop (see [LeafClassifier.classifyForSaliency]). */
    override fun classifyForSaliency(bitmap: Bitmap): Diagnosis {
        val input = bitmapToInput(bitmap, inputSize)
        val output = Array(1) { FloatArray(labels.size) }
        synchronized(interpreter) {
            interpreter.run(input, output)
        }
        return rankedDiagnosis(output[0])
    }

    private fun rankedDiagnosis(probs: FloatArray): Diagnosis {
        val ranked = labels.mapIndexed { i, label -> label to probs.getOrElse(i) { 0f } }
            .sortedByDescending { it.second }
            .take(TOP_K)
        return Diagnosis(
            topLabel = ranked.first().first,
            topConfidence = ranked.first().second,
            topK = ranked,
        )
    }

    /** Horizontally mirrors a [1][h][w][3] NHWC input along the width axis (the `hflip` TTA view). */
    private fun flipInputH(input: Array<Array<Array<FloatArray>>>): Array<Array<Array<FloatArray>>> {
        val h = input[0].size
        val w = input[0][0].size
        val c = input[0][0][0].size
        val out = Array(1) { Array(h) { Array(w) { FloatArray(c) } } }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val src = input[0][y][w - 1 - x]
                val dst = out[0][y][x]
                for (ch in 0 until c) dst[ch] = src[ch]
            }
        }
        return out
    }

    /**
     * Center-crops [bitmap] to a square, then takes the central [scale] fraction of that square and
     * returns it (a slight zoom on the leaf centre). This is the `scale0.875` view from the v3.5 eval
     * TTA: resizing the square to 256 and center-cropping 224 is equivalent to cropping the central
     * 224/256 = 0.875 of the square, which [bitmapToInput] then scales back to 224.
     */
    private fun scaleCropSquare(bitmap: Bitmap, scale: Float): Bitmap {
        val square = centerCropSquare(bitmap)
        val side = square.width
        val crop = (side * scale).toInt().coerceIn(1, side)
        if (crop == side) return square
        val off = (side - crop) / 2
        return Bitmap.createBitmap(square, off, off, crop, crop)
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
        /** Central-crop fraction for the `scale0.875` TTA view (matches the v3.5 eval recipe). */
        const val SCALE_0_875 = 0.875f
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
