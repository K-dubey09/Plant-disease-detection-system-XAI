package com.leafguard.app.di

import android.content.Context
import com.leafguard.app.data.inference.DiseaseClassifier
import com.leafguard.app.data.inference.JktkDetector
import com.leafguard.app.data.inference.JktkLabelParser
import com.leafguard.app.data.inference.LabelParser
import com.leafguard.app.data.inference.LeafClassifier
import com.leafguard.app.data.inference.ModelIntegrity
import com.leafguard.app.data.inference.OnnxDiseaseClassifier
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
import javax.inject.Qualifier
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Hilt qualifier for the PlantVillage Swin-Tiny model artifacts (buffer / interpreter / labels).
 * Used to keep the original 38-class lab-trained model wired as a rollback path even though the
 * YOLOv11 detector is now the primary [LeafClassifier].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SwinModel

/**
 * Hilt qualifier for the JK-TK YOLOv11 detector artifacts (buffer / interpreter / labels).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JktkModel

/**
 * Hilt qualifier for the v2.0.0.0 v3 Swin-Tiny ONNX model artifacts (model bytes / ORT session).
 * The v3 model (ImageNet base + CutMix/MixUp + field-oversample + layer-wise LR + TTA) is the
 * PRIMARY [LeafClassifier]; the legacy TFLite Swin ([@SwinModel]) and JK-TK ([@JktkModel]) paths
 * are retained as rollback.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class V3Model

/**
 * Wires the on-device inference stack. The PRIMARY [LeafClassifier] is [OnnxDiseaseClassifier]
 * (v2.0.0.0): the v3 fine-tuned Swin-Tiny ONNX model — ImageNet-pretrained base (NOT the
 * PlantVillage-overfit base that capped v2.2 at 34% field) + CutMix/MixUp + field oversampling +
 * layer-wise LR decay + TTA — reaching 94.5% mixed / 67.3% field / 99.3% lab validation. It runs
 * via ONNX Runtime (Swin's window-attention Roll/Cast/Einsum ops make ONNX->TFLite fragile, so
 * the verified float32 ONNX graph executes directly).
 *
 * The legacy [DiseaseClassifier] (TFLite Swin-Tiny, 38 PlantVillage classes) is retained under
 * [@SwinModel] as a rollback; [JktkDetector] (YOLOv11) is retained under [@JktkModel] as the
 * v1.5.0.0 rollback (its 228 MB .tflite is not bundled — see .gitignore). To roll back, change
 * [provideLeafClassifier] to return the rollback classifier and repoint [provideSuggestions] /
 * [provideModelIntegrity] at the corresponding assets + digest.
 *
 * The [SaliencyGenerator], [SuggestionEngine], and [ModelIntegrity] all bind to the PRIMARY path:
 * the occlusion heatmap calls the V3 ORT classifier, suggestions are keyed by the 38-class
 * canonical names (labels.txt / disease_suggestions.json), and integrity checks the V3 ONNX
 * buffer's SHA-256 against [ModelIntegrity.EXPECTED_SHA256_V3].
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    // ---------- v3 Swin-Tiny ONNX via ORT (PRIMARY, v2.0.0.0) ----------

    /** Loads the v3 ONNX model bytes (107 MB) from assets. readBytes() is compression-safe. */
    @Provides
    @Singleton
    @V3Model
    fun provideV3ModelBytes(@ApplicationContext ctx: Context): ByteArray =
        ctx.assets.open("models/swin_v3.onnx").use { it.readBytes() }

    /** Direct ByteBuffer over the v3 model bytes for [ModelIntegrity] (SHA-256 over the same bytes
     *  ORT executes). Allocated direct + native order to match the existing integrity pattern. */
    @Provides
    @Singleton
    @V3Model
    fun provideV3ModelBuffer(@V3Model bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)

    /** Builds the singleton ORT session over the v3 ONNX bytes. Created via [OrtEnvironment]
     *  (the OrtSession constructors are package-private; the public [OrtEnvironment.createSession]
     *  overload uses the default allocator internally). The CPU execution provider runs the float32
     *  weights as-is (the model ships float32 for fidelity to the verified checkpoint). */
    @Provides
    @Singleton
    @V3Model
    fun provideV3Session(@V3Model bytes: ByteArray): OrtSession =
        OrtEnvironment.getEnvironment().createSession(bytes, OrtSession.SessionOptions())

    @Provides
    @Singleton
    @V3Model
    fun provideV3Labels(@ApplicationContext ctx: Context): List<DiseaseLabel> =
        readAsset(ctx, "models/labels.txt").let(LabelParser::parse)

    @Provides
    @Singleton
    fun provideOnnxDiseaseClassifier(
        @V3Model session: OrtSession,
        @V3Model labels: List<DiseaseLabel>,
    ): OnnxDiseaseClassifier = OnnxDiseaseClassifier(session, labels)

    /** Primary leaf model (v2.0.0.0): the v3 ORT Swin-Tiny classifier. */
    @Provides
    @Singleton
    fun provideLeafClassifier(cls: OnnxDiseaseClassifier): LeafClassifier = cls

    /** Primary suggestions map: the 38-class canonical set (labels.txt / disease_suggestions.json). */
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

    /** Integrity over the PRIMARY (v3 ONNX) model buffer. */
    @Provides
    @Singleton
    fun provideModelIntegrity(@V3Model modelBuffer: ByteBuffer): ModelIntegrity =
        ModelIntegrity(modelBuffer, ModelIntegrity.EXPECTED_SHA256_V3)

    // ---------- JK-TK YOLOv11 detector (v1.5.0.0 rollback — kept wired, not primary) ----------

    /**
     * Loads the primary model into a direct [ByteBuffer]. readBytes() (not mmap via
     * AssetFileDescriptor) is compression-safe: aapt may compress .tflite in the APK, which makes
     * openFd()'s region invalid for mmap; reading the stream works either way.
     */
    @Provides
    @Singleton
    @JktkModel
    fun provideJktkModelBuffer(@ApplicationContext ctx: Context): ByteBuffer =
        loadDirectBuffer(ctx, "models/jktk_detector.tflite")

    @Provides
    @Singleton
    @JktkModel
    fun provideJktkInterpreter(@JktkModel buffer: ByteBuffer): Interpreter =
        Interpreter(buffer, Interpreter.Options())

    @Provides
    @Singleton
    @JktkModel
    fun provideJktkLabels(@ApplicationContext ctx: Context): List<DiseaseLabel> =
        readAsset(ctx, "models/labels_jktk.txt").let(JktkLabelParser::parse)

    @Provides
    @Singleton
    fun provideJktkDetector(
        @JktkModel interpreter: Interpreter,
        @JktkModel labels: List<DiseaseLabel>,
    ): JktkDetector = JktkDetector(interpreter, labels)

    // The primary LeafClassifier / Suggestions / SaliencyGenerator / SuggestionEngine /
    // ModelIntegrity bindings are provided by the V3 ORT section above. To roll back to JK-TK,
    // repoint provideLeafClassifier -> JktkDetector, provideSuggestions -> disease_suggestions_jktk.json,
    // and provideModelIntegrity -> @JktkModel buffer + EXPECTED_SHA256_JKTK. The 228 MB
    // jktk_detector.tflite is not bundled (see .gitignore) — fetch it from the v1.5.0.0 release first.

    // ---------- Swin-Tiny (PlantVillage) fallback — kept wired for rollback, not primary ----------

    @Provides
    @Singleton
    @SwinModel
    fun provideSwinModelBuffer(@ApplicationContext ctx: Context): ByteBuffer =
        loadDirectBuffer(ctx, "models/plant_disease.tflite")

    @Provides
    @Singleton
    @SwinModel
    fun provideSwinInterpreter(@SwinModel buffer: ByteBuffer): Interpreter =
        Interpreter(buffer, Interpreter.Options())

    @Provides
    @Singleton
    @SwinModel
    fun provideSwinLabels(@ApplicationContext ctx: Context): List<DiseaseLabel> =
        readAsset(ctx, "models/labels.txt").let(LabelParser::parse)

    @Provides
    @Singleton
    fun provideSwinClassifier(
        @SwinModel interpreter: Interpreter,
        @SwinModel labels: List<DiseaseLabel>,
    ): DiseaseClassifier = DiseaseClassifier(interpreter, labels)

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
