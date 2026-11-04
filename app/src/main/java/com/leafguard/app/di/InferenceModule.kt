package com.leafguard.app.di

import android.content.Context
import com.leafguard.app.data.inference.DiseaseClassifier
import com.leafguard.app.data.inference.JktkDetector
import com.leafguard.app.data.inference.JktkLabelParser
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
import javax.inject.Qualifier
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter

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
 * Wires the on-device inference stack. The PRIMARY [LeafClassifier] is [JktkDetector] (YOLOv11,
 * 116 classes, trained on lab + real-field images -- generalizes to camera input). The original
 * [DiseaseClassifier] (Swin-Tiny, 38 PlantVillage classes) is retained under [@SwinModel] as a
 * rollback: to switch back, change [provideLeafClassifier] to return the [DiseaseClassifier]
 * instead of [JktkDetector], and point [provideSuggestions] / [provideModelIntegrity] back at the
 * Swin assets + digest.
 *
 * The [SaliencyGenerator], [SuggestionEngine], and [ModelIntegrity] all bind to the PRIMARY path:
 * the occlusion heatmap calls the detector, suggestions are keyed by the JK-TK label names (117
 * entries incl. the "Unknown" OOD sentinel), and integrity checks the JK-TK buffer's SHA-256.
 */
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    // ---------- JK-TK YOLOv11 detector (PRIMARY) ----------

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

    /** Primary leaf model. Roll back to Swin: return `DiseaseClassifier(swinInterp, swinLabels)`. */
    @Provides
    @Singleton
    fun provideLeafClassifier(detector: JktkDetector): LeafClassifier = detector

    /** Primary suggestions map: the 117-entry JK-TK set (116 classes + "Unknown" OOD entry). */
    @Provides
    @Singleton
    fun provideSuggestions(@ApplicationContext ctx: Context): Map<String, Suggestion> =
        readAsset(ctx, "models/disease_suggestions_jktk.json").let(SuggestionParser::parse)

    @Provides
    @Singleton
    fun provideSaliencyGenerator(classifier: LeafClassifier): SaliencyGenerator =
        SaliencyGenerator(classifier)

    @Provides
    @Singleton
    fun provideSuggestionEngine(suggestions: Map<String, Suggestion>): SuggestionEngine =
        SuggestionEngine(suggestions)

    /** Integrity over the PRIMARY (JK-TK) model buffer. */
    @Provides
    @Singleton
    fun provideModelIntegrity(@JktkModel modelBuffer: ByteBuffer): ModelIntegrity =
        ModelIntegrity(modelBuffer, ModelIntegrity.EXPECTED_SHA256_JKTK)

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
