package com.leafguard.app.data.inference

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.DiseaseLabel
import org.tensorflow.lite.Interpreter

/**
 * YOLOv11 leaf-disease **detector** (HuggingFace `JK-TK/PlantDiseaseDetection`, 116 classes,
 * trained on lab + real-field images). Replaces the PlantVillage-only Swin classifier
 * [DiseaseClassifier] as the primary on-device model so the app generalizes to real camera/gallery
 * photos instead of collapsing on field input (the Swin model's "sweet potato -> tomato" and ~36%
 * confidence on diseased tomato were symptoms of PlantVillage-only lab training).
 *
 * I/O contract (verified against the source `.pt` by `scripts/validate_jktk_tflite.py`):
 *   Input : `[1, 640, 640, 3]` float32, pixels in `[0, 1]`, **letterboxed** (pad gray 114) -- not
 *           center-cropped, because cropping changes aspect ratio and corrupts the bounding boxes
 *           a detector relies on.
 *   Output: `[1, 120, 8400]` float32 = `[1, 4 + 116, 8400]`. Per anchor the first 4 channels are
 *           `[cx, cy, w, h]` in 640-px space; the remaining 116 are post-sigmoid class scores
 *           (YOLOv8/v11 has no separate objectness -- the class score IS the confidence).
 *
 * Decoding mirrors the validated Python: per anchor take the max class score, drop below
 * [CONF_THRESHOLD], NMS at IoU [IOU_THRESHOLD], report the top box. If no box survives the image is
 * out-of-distribution (e.g. a sweet-potato leaf, for which no class exists) and we return the
 * sentinel [UNKNOWN_LABEL] so the UI can say "I don't recognize this plant" instead of force-fitting
 * a confident wrong disease.
 *
 * Thread-safety: the native [Interpreter.run] is serialized through `synchronized(interpreter)`
 * for the same reason as [DiseaseClassifier] -- the 64-pass occlusion heatmap re-invokes `classify`
 * on an application-scoped coroutine concurrent with new scans, and TFLite's Interpreter is not
 * thread-safe.
 *
 * Cost note: a 640x640 YOLOv11x forward pass is heavier than the old 224x224 Swin pass, so the
 * best-effort occlusion heatmap ([SaliencyGenerator], 64 passes) takes longer to stream in. The
 * headline diagnosis still returns in one pass (~0.5-1 s on-device) and is shown immediately; the
 * heatmap is a non-blocking background refinement.
 */
class JktkDetector(
    private val interpreter: Interpreter,
    private val labels: List<DiseaseLabel>,
    private val inputSize: Int = IMAGE_SIZE,
) : LeafClassifier {

    override fun classify(bitmap: Bitmap): Diagnosis {
        val input = letterboxToInput(bitmap, inputSize)
        val nc = labels.size
        val outChannels = 4 + nc
        // [1, 120, 8400] -- indexed [channel][anchor] after dropping the batch dim.
        val output = Array(1) { Array(outChannels) { FloatArray(NUM_ANCHORS) } }
        synchronized(interpreter) {
            interpreter.run(input, output)
        }
        val dets = decode(output[0], nc)
        if (dets.isEmpty()) {
            return Diagnosis(
                topLabel = UNKNOWN_LABEL,
                topConfidence = 0f,
                topK = listOf(UNKNOWN_LABEL to 0f),
            )
        }
        val ranked = dets.take(TOP_K).map { labels[it.cls] to it.score }
        return Diagnosis(
            topLabel = ranked.first().first,
            topConfidence = ranked.first().second,
            topK = ranked,
        )
    }

    /**
     * Letterbox resize: scale the bitmap to fit [size]x[size] keeping aspect ratio, then pad the
     * remainder with gray ([PAD_COLOR], the standard YOLO pad value) so box geometry is preserved.
     * Renders via [Canvas] (one [Bitmap.drawBitmap] + one [Bitmap.getPixels]) rather than per-pixel
     * [Bitmap.setPixel] loops, which would be ~409k calls each for a 640x640 target.
     */
    private fun letterboxToInput(bitmap: Bitmap, size: Int): Array<Array<Array<FloatArray>>> {
        val r = minOf(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        val nw = maxOf((bitmap.width * r).toInt(), 1)
        val nh = maxOf((bitmap.height * r).toInt(), 1)
        val scaled = if (bitmap.width == nw && bitmap.height == nh) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        }
        val letterboxed = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxed)
        canvas.drawColor(Color.argb(255, PAD_COLOR, PAD_COLOR, PAD_COLOR))
        val offX = (size - nw) / 2
        val offY = (size - nh) / 2
        canvas.drawBitmap(scaled, null, Rect(offX, offY, offX + nw, offY + nh), null)

        val pixels = IntArray(size * size)
        letterboxed.getPixels(pixels, 0, size, 0, 0, size, size)
        val input = Array(1) { Array(size) { Array(size) { FloatArray(3) } } }
        for (i in pixels.indices) {
            val y = i / size
            val x = i % size
            val px = pixels[i]
            input[0][y][x][0] = ((px shr 16) and 0xFF) / 255f
            input[0][y][x][1] = ((px shr 8) and 0xFF) / 255f
            input[0][y][x][2] = (px and 0xFF) / 255f
        }
        return input
    }

    /**
     * Decode the [4+nc][8400] output into NMS-suppressed [Det]s sorted by score.
     * [channels[c]] is the c-th channel across all 8400 anchors, so channel 0..3 = cx,cy,w,h and
     * channel 4..(4+nc-1) = the class scores.
     */
    private fun decode(channels: Array<FloatArray>, nc: Int): List<Det> {
        val candidates = ArrayList<Det>(256)
        for (a in 0 until NUM_ANCHORS) {
            var bestCls = 0
            var bestScore = channels[4][a]
            for (c in 5 until 4 + nc) {
                val s = channels[c][a]
                if (s > bestScore) {
                    bestScore = s
                    bestCls = c - 4
                }
            }
            if (bestScore < CONF_THRESHOLD) continue
            val cx = channels[0][a]
            val cy = channels[1][a]
            val w = channels[2][a]
            val h = channels[3][a]
            candidates.add(Det(bestCls, bestScore, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f))
        }
        candidates.sortByDescending { it.score }
        // Greedy NMS, per-class (a tomato box should not suppress an overlapping apple box).
        val keep = ArrayList<Det>(candidates.size)
        for (d in candidates) {
            var suppressed = false
            for (k in keep) {
                if (k.cls != d.cls) continue
                if (iou(d, k) > IOU_THRESHOLD) {
                    suppressed = true
                    break
                }
            }
            if (!suppressed) keep.add(d)
        }
        return keep
    }

    private fun iou(a: Det, b: Det): Float {
        val ix1 = maxOf(a.x1, b.x1)
        val iy1 = maxOf(a.y1, b.y1)
        val ix2 = minOf(a.x2, b.x2)
        val iy2 = minOf(a.y2, b.y2)
        val inter = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
        val areaA = maxOf(0f, a.x2 - a.x1) * maxOf(0f, a.y2 - a.y1)
        val areaB = maxOf(0f, b.x2 - b.x1) * maxOf(0f, b.y2 - b.y1)
        return inter / (areaA + areaB - inter + 1e-9f)
    }

    /** A decoded detection: class index, confidence, and the box in 640-px letterbox space. */
    private data class Det(
        val cls: Int,
        val score: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    )

    companion object {
        const val IMAGE_SIZE = 640
        const val NUM_ANCHORS = 8400
        const val CONF_THRESHOLD = 0.25f
        const val IOU_THRESHOLD = 0.45f
        const val TOP_K = 3
        const val PAD_COLOR = 114

        val UNKNOWN_LABEL = DiseaseLabel(
            index = -1,
            name = "Unknown",
            plant = "Unknown",
            condition = "Unrecognized",
        )
    }
}
