package com.leafguard.app.data.inference

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.DiseaseLabel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * v2.0.0.0 PRIMARY leaf classifier — runs the v3 fine-tuned Swin-Tiny ONNX model on-device via
 * ONNX Runtime. v3 was trained on an ImageNet-pretrained base (NOT the PlantVillage-overfit base
 * that capped v2.2 at 34% field) with CutMix/MixUp + field oversampling + layer-wise LR decay +
 * TTA, reaching 94.5% mixed / 67.3% field / 99.3% lab validation. See the project's
 * `download/swin_strategy/` for the full diagnosis + redesign.
 *
 * I/O contract (matches the v3 ONNX export wrapper): input float32[1,3,224,224] NCHW, raw [0,1]
 * pixels — the ImageNet mean/std normalization AND the final Softmax are baked into the ONNX
 * graph by the SwinExport wrapper at export time — so this code feeds raw [0,1] pixels and reads
 * real probabilities, with no app-side normalization or postprocessing (same contract as the
 * legacy TFLite [DiseaseClassifier], only the layout differs: NCHW here vs NHWC there).
 *
 * Why ORT instead of TFLite: Swin's shifted-window attention relies on Roll / Cast / Einsum ops
 * whose ONNX->TFLite conversion is fragile and historically breaks or silently degrades the
 * model. ONNX Runtime executes the exact graph that scored 94.5% / 67.3% with zero conversion
 * risk. The float16 weights (~54 MB, under GitHub's 100 MB file limit so the model is committable)
 * are upcast to float32 by the CPU execution provider for the actual compute, so storage as
 * float16 costs no accuracy vs the float32 checkpoint.
 *
 * Thread-safety: [OrtSession.run] is NOT thread-safe — this serializes the single forward pass
 * through `synchronized(session)` for the same reason [DiseaseClassifier] serializes the TFLite
 * [Interpreter]: a new scan's classify must not race the previous scan's in-flight 64-pass
 * occlusion heatmap on a different thread.
 */
class OnnxDiseaseClassifier(
    private val session: OrtSession,
    private val labels: List<DiseaseLabel>,
    private val inputSize: Int = IMAGE_SIZE,
) : LeafClassifier {

    override fun classify(bitmap: Bitmap): Diagnosis {
        val input = bitmapToNchw(bitmap, inputSize)
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        val tensor = OnnxTensor.createTensor(env, input, shape)
        val inputName = session.inputNames.first()
        // session.run returns an OrtSession.Result (AutoCloseable); .use closes it (and the owned
        // output tensor) after we extract the probabilities. The input tensor is not owned by the
        // Result, so it is closed separately in finally.
        val probs: FloatArray = synchronized(session) {
            try {
                session.run(mapOf(inputName to tensor)).use { result ->
                    val out = result.get(0) as OnnxTensor
                    when (val v = out.getValue()) {
                        is Array<*> -> v[0] as FloatArray
                        is FloatArray -> v
                        else -> error("unexpected ONNX output type: ${v?.javaClass}")
                    }
                }
            } finally {
                tensor.close()
            }
        }
        val ranked = labels.mapIndexed { i, label -> label to probs.getOrElse(i) { 0f } }
            .sortedByDescending { it.second }
            .take(TOP_K)
        return Diagnosis(
            topLabel = ranked.first().first,
            topConfidence = ranked.first().second,
            topK = ranked,
        )
    }

    /**
     * Builds the NCHW [1,3,size,size] float input the ONNX model expects, pixels in [0,1].
     * Center-crops to a square first ([centerCropSquare]) so a non-square camera/gallery bitmap
     * is not stretched to size x size (which would distort the leaf), then scales to [size].
     * Fills channel-first: all R values, then all G, then all B (NCHW, not NHWC).
     */
    fun bitmapToNchw(bitmap: Bitmap, size: Int): FloatBuffer {
        val square = centerCropSquare(bitmap)
        val scaled = if (square.width == size && square.height == size) {
            square
        } else {
            Bitmap.createScaledBitmap(square, size, size, true)
        }
        val capacity = 3 * size * size
        val buf = ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        // NCHW: channel plane R (shift 16), then G (shift 8), then B (shift 0).
        for (c in 0 until 3) {
            val shift = 16 - 8 * c
            for (p in pixels) {
                buf.put(((p shr shift) and 0xFF) / 255f)
            }
        }
        buf.rewind()
        return buf
    }

    companion object {
        const val IMAGE_SIZE = 224
        const val TOP_K = 3
    }
}
