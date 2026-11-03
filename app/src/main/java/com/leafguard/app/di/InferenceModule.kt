package com.leafguard.app.di

import android.content.Context
import com.leafguard.app.data.inference.DiseaseClassifier
import com.leafguard.app.data.inference.LabelParser
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

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    /**
     * Loads the model into a direct [ByteBuffer]. Using readBytes() (rather than mmap via
     * AssetFileDescriptor) is compression-safe: aapt may compress .tflite in the APK, which makes
     * openFd()'s region invalid for mmap; reading the stream works either way.
     */
    @Provides
    @Singleton
    fun provideModelBuffer(@ApplicationContext ctx: Context): ByteBuffer {
        val bytes = ctx.assets.open("models/plant_disease.tflite").use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
    }

    @Provides
    @Singleton
    fun provideInterpreter(buffer: ByteBuffer): Interpreter =
        Interpreter(buffer, Interpreter.Options())

    @Provides
    @Singleton
    fun provideLabels(@ApplicationContext ctx: Context): List<DiseaseLabel> =
        ctx.assets.open("models/labels.txt").use { stream ->
            stream.bufferedReader().use { it.readText() }
        }.let(LabelParser::parse)

    @Provides
    @Singleton
    fun provideSuggestions(@ApplicationContext ctx: Context): Map<String, Suggestion> =
        ctx.assets.open("models/disease_suggestions.json").use { stream ->
            stream.bufferedReader().use { it.readText() }
        }.let(SuggestionParser::parse)

    @Provides
    @Singleton
    fun provideClassifier(
        interpreter: Interpreter,
        labels: List<DiseaseLabel>,
    ): DiseaseClassifier = DiseaseClassifier(interpreter, labels)

    @Provides
    @Singleton
    fun provideSaliencyGenerator(classifier: DiseaseClassifier): SaliencyGenerator =
        SaliencyGenerator(classifier)

    @Provides
    @Singleton
    fun provideSuggestionEngine(suggestions: Map<String, Suggestion>): SuggestionEngine =
        SuggestionEngine(suggestions)
}

