package com.leafguard.app.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.inference.DiseaseClassifier
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.inference.ScanResultHolder
import com.leafguard.app.data.inference.SaliencyGenerator
import com.leafguard.app.data.inference.SuggestionEngine
import com.leafguard.app.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the classify -> saliency -> suggest pipeline for a chosen leaf photo and publishes the
 * outcome to [ScanResultHolder] for the result screen. The UI observes [ui] to show staging
 * ("Classifying…", "Generating explanation…") and to navigate once Done.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val classifier: DiseaseClassifier,
    private val saliencyGenerator: SaliencyGenerator,
    private val suggestionEngine: SuggestionEngine,
    private val holder: ScanResultHolder,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Analyzing(val stage: String) : UiState
        data class Error(val message: String) : UiState
        data object Done : UiState
    }

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun analyze(bitmap: Bitmap) {
        if (_ui.value is UiState.Analyzing) return
        viewModelScope.launch {
            _ui.value = UiState.Analyzing("Classifying leaf…")
            try {
                val diagnosis = withContext(io) { classifier.classify(bitmap) }
                _ui.value = UiState.Analyzing("Generating explanation heatmap…")
                val saliency = withContext(io) { saliencyGenerator.generate(bitmap) }
                val suggestion = suggestionEngine.suggest(diagnosis.topLabel)
                holder.set(ScanResult(diagnosis, suggestion, saliency, bitmap))
                _ui.value = UiState.Done
            } catch (e: Exception) {
                _ui.value = UiState.Error(e.message ?: "Analysis failed.")
            }
        }
    }

    fun reset() {
        _ui.value = UiState.Idle
    }
}
