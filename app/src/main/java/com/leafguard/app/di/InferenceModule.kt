package com.leafguard.app.di

import android.content.Context
import com.leafguard.app.data.inference.DiseaseClassifier
import com.leafguard.app.data.inference.LabelParser
import com.leafguard.app.data.inference.LeafClassifier
import com.leafguard.app.data.inference.ModelIntegrity
import com.leafguard.app.data.inference.SaliencyGenerator
import com.leafguard.app.data.inference.SuggestionEngine
import com.leafguard.app.data.inference.SuggestionParser
import com.leafguard.app.domain.model.DiseaseLabel
import com.leafguard.app.domain.model.Suggestion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter

/**
 * Wires the on-device inference stack. v2.5.0.0 ships the DINOv2 ViT-S/14 fine-tune (v3.5.0.0) as
 * the sole primary [LeafClassifier], exposed through [DiseaseClassifier] which feeds it the
 * `[1,224,224,3]` float32 [0,1] input the graph expects (ImageNet norm + softmax baked in) and runs
 * 3-view test-time augmentation on the headline path.
 *
 * The model is a drop-in for the prior Swin-Tiny `plant_disease.tflite`: same 38-class output order
 * as `labels.txt`, so `disease_suggestions.json` (the 38-entry PlantVillage suggestion map) and
 * [LabelParser] are unchanged. [ModelIntegrity] guards the same buffer the Interpreter executes,
 * against the DINOv2 SHA-256 in [ModelIntegrity.EXPECTED_SHA256].
 *
 * This retires the v2.0.0.0 primary (the v3 Swin-Tiny ONNX via ONNX Runtime): DINOv2 beats it on
 * field accuracy (86.7% vs 67.3%) AND ships as a committed TFLite (the 107 MB v3 ONNX was over
 * GitHub's 100 MB file limit and .gitignore'd, so a fresh checkout could not load it). The ORT
 * dependency, [com.leafguard.app.data.inference.OnnxDiseaseClassifier], and the @V3Model/@SwinModel/
 * @JktkModel qualifiers are removed. [JktkDetector] + its labels/suggestion JSON remain in the
 * source tree and assets for reference but are unwired. [SaliencyGenerator] binds to the primary
 * [LeafClassifier] and calls its single-pass `classifyForSaliency` for the 64-cell occlusion loop.
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    /** Loads the DINOv2 model into a direct [ByteBuffer]. readBytes() (not mmap via
     *  AssetFileDescriptor) is compression-safe: aapt may compress .tflite in the APK, which makes
     *  openFd()'s region invalid for mmap; reading the stream works either way. */
    @Provides
    @Singleton
    fun provideModelBuffer(@ApplicationContext ctx: Context): ByteBuffer =
        loadDirectBuffer(ctx, "models/plant_disease.tflite")

    @Provides
    @Singleton
    fun provideInterpreter(buffer: ByteBuffer): Interpreter =
        Interpreter(buffer, Interpreter.Options())

    @Provides
    @Singleton
    fun provideLabels(@ApplicationContext ctx: Context): List<DiseaseLabel> =
        readAsset(ctx, "models/labels.txt").let(LabelParser::parse)

    /** Primary leaf model: the DINOv2 classifier with 3-view TTA on the headline path. */
    @Provides
    @Singleton
    fun provideLeafClassifier(
        interpreter: Interpreter,
        labels: List<DiseaseLabel>,
    ): LeafClassifier = DiseaseClassifier(interpreter, labels)

    /** The 38-entry PlantVillage suggestion map, keyed by the labels.txt class names. */
    @Provides
    @Singleton
    fun provideSuggestions(@ApplicationContext ctx: Context): Map<String, Suggestion> =
        readAsset(ctx, "models/disease_suggestions.json").let(SuggestionParser::parse)

    @Provides
    @Singleton
    fun provideSaliencyGenerator(classifier: LeafClassifier): SaliencyGenerator =
        SaliencyGenerator(classifier)

    @Provides
    @Singleton
    fun provideSuggestionEngine(suggestions: Map<String, Suggestion>): SuggestionEngine =
        SuggestionEngine(suggestions)

    /** Integrity over the primary (DINOv2) model buffer. */
    @Provides
    @Singleton
    fun provideModelIntegrity(modelBuffer: ByteBuffer): ModelIntegrity =
        ModelIntegrity(modelBuffer, ModelIntegrity.EXPECTED_SHA256)

    // ---------- helpers ----------

    private fun loadDirectBuffer(ctx: Context, assetPath: String): ByteBuffer {
        val bytes = ctx.assets.open(assetPath).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
    }

    private fun readAsset(ctx: Context, assetPath: String): String =
        ctx.assets.open(assetPath).use { stream ->
            stream.bufferedReader().use { it.readText() }
        }
}
