package com.leafguard.app.data.inference

import android.graphics.Bitmap
import com.leafguard.app.domain.model.Diagnosis

/**
 * Abstraction over the on-device leaf model so the scan pipeline ([ScanViewModel],
 * [SaliencyGenerator], [SaliencyRunner]) is decoupled from the concrete inference engine.
 *
 * The original Swin-Tiny classifier [DiseaseClassifier] (38 PlantVillage lab classes) and the
 * YOLOv11 detector [JktkDetector] (116 lab+field classes) both implement this. Hilt binds the
 * primary -- currently [JktkDetector], which generalizes to real-world field photos -- while
 * [DiseaseClassifier] is retained as the PlantVillage-only fallback (switch by changing which
 * concrete implementation [InferenceModule] returns for `LeafClassifier`).
 */
interface LeafClassifier {
    fun classify(bitmap: Bitmap): Diagnosis
}
