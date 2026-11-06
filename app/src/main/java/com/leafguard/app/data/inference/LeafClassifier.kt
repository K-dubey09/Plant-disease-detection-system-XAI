package com.leafguard.app.data.inference

import android.graphics.Bitmap
import com.leafguard.app.domain.model.Diagnosis

/**
 * Abstraction over the on-device leaf model so the scan pipeline ([ScanViewModel],
 * [SaliencyGenerator], [SaliencyRunner]) is decoupled from the concrete inference engine.
 *
 * v2.5.0.0 ships [DiseaseClassifier] backed by the DINOv2 ViT-S/14 fine-tune (v3.5.0.0: 38
 * PlantVillage classes, 86.7% field / 99.3% lab / 96.1% mixed) as the sole primary. The
 * [JktkDetector] (YOLOv11) class is retained in the source tree for reference but is no longer
 * wired, since its 228 MB model is over GitHub's file limit and not bundled.
 *
 * [classify] is the headline path used by [ScanViewModel] -- [DiseaseClassifier] runs 3-view
 * test-time augmentation there (orig + hflip + scale0.875, the exact v3.5 eval recipe) so on-device
 * field accuracy tracks the validated 86.7%. [classifyForSaliency] is the single-pass path the
 * 64-cell occlusion heatmap uses: TTA on every occluded patch would 3x the heatmap cost for no
 * explainability gain (the occlusion drop is the signal, not per-view confidence), so it defaults
 * to [classify] and [DiseaseClassifier] overrides it with one forward pass.
 */
interface LeafClassifier {
    /** Headline classification (TTA-enabled where the implementation supports it). */
    fun classify(bitmap: Bitmap): Diagnosis

    /**
     * Single-pass classification for the occlusion-saliency loop. Defaults to [classify]; overridden
     * by [DiseaseClassifier] to skip TTA so the heatmap stays fast and its per-cell drops are clean.
     */
    fun classifyForSaliency(bitmap: Bitmap): Diagnosis = classify(bitmap)
}
