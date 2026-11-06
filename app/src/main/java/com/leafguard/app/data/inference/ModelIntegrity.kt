package com.leafguard.app.data.inference

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Detects tampering of the bundled `plant_disease.tflite` by comparing its SHA-256 against the
 * digest captured at build time ([EXPECTED_SHA256]).
 *
 * The model is the trust root of the whole diagnosis: if the .tflite is swapped (rooted device,
 * MITM'd asset overlay, a corrupt OTA, or a debugging override of the assets directory) the
 * classifier will still happily produce a confident label — TFLite's [Interpreter][org.tensorflow.lite.Interpreter]
 * does not validate the model bytes — but that label is meaningless, or worse, adversarial.
 * [isIntact] lets the UI surface a "model integrity warning" banner so the user treats the
 * diagnosis as untrusted instead of authoritative, without hard-refusing to scan (the failure mode
 * is uncertain, so the correct action is to inform, not block).
 *
 * The digest is computed over the same [ByteBuffer] the Interpreter was built from, so it covers
 * exactly the bytes TFLite executes. [ByteBuffer.duplicate] is used so the read does not advance
 * the original buffer's position/limit — the Interpreter may re-read it, and Hilt reuses the single
 * singleton buffer for every integrity check.
 *
 * Testability: [expectedSha256] is a constructor parameter (defaulting to [EXPECTED_SHA256]) so a
 * unit test can construct a [ModelIntegrity] over a small known buffer and assert both the digest
 * math and the intact/tampered verdict without loading the 9 MB production model.
 */
class ModelIntegrity(
    private val modelBuffer: ByteBuffer,
    private val expectedSha256: String = EXPECTED_SHA256,
) {
    /** True iff the buffer's SHA-256 matches [expectedSha256] (case-insensitive hex compare). */
    fun isIntact(): Boolean = sha256().equals(expectedSha256, ignoreCase = true)

    /**
     * Lowercase hex SHA-256 of the current buffer contents. Reads via a [ByteBuffer.duplicate] so
     * the original buffer's position/limit are left untouched for the Interpreter. Bytes are masked
     * with `and 0xFF` before hex formatting because Kotlin's [Byte] is signed (`0xFF` is `-1`),
     * which would otherwise sign-extend to `ffffffff`.
     */
    fun sha256(): String {
        val dup = modelBuffer.duplicate()
        dup.position(0)
        dup.limit(dup.capacity())
        val md = MessageDigest.getInstance("SHA-256")
        val tmp = ByteArray(8 * 1024)
        while (dup.hasRemaining()) {
            val n = minOf(tmp.size, dup.remaining())
            dup.get(tmp, 0, n)
            md.update(tmp, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object {
        /**
         * SHA-256 of the shipped `app/src/main/assets/models/plant_disease.tflite` (the DINOv2
         * ViT-S/14 fine-tune v3.5.0.0: 38 PlantVillage classes, 86.7% field / 99.3% lab / 96.1%
         * mixed; 86,845,656 bytes). The model is a drop-in for the prior Swin-Tiny asset -- same
         * `[1,224,224,3]` float32 [0,1] input with ImageNet norm + softmax baked into the graph, same
         * 38-class output order as labels.txt -- verified bit-identical to the source ONNX in ORT
         * (argmax 3/3, max|diff|=1e-6). Update this constant after every model swap and regenerate
         * with `sha256sum app/src/main/assets/models/plant_disease.tflite`.
         */
        const val EXPECTED_SHA256 = "3660cfdbad212bcd5d307ee61e508382b8968b64d5e6f1feb63ba085267b69e7"
    }
}
