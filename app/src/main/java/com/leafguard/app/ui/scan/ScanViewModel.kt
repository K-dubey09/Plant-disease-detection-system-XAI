package com.leafguard.app.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.inference.DiseaseClassifier
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.inference.ScanResultHolder
import com.leafguard.app.data.inference.SaliencyGenerator
import com.leafguard.app.data.inference.SuggestionEngine
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.di.IoDispatcher
import com.leafguard.app.domain.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the classify -> saliency -> suggest pipeline for a leaf photo, persists the outcome as a
 * [ScanEntity] (Phase 5), publishes the Bitmaps to [ScanResultHolder] for the result screen, and
 * reports the new [ScanEntity] id via [UiState.Done] so the UI can navigate to `result/{scanId}`.
 * The UI observes [ui] to show staging ("Classifying…", "Generating explanation…", "Saving scan…").
 *
 * [plantId] is non-null when the scan was launched from PlantDetail ("Scan this plant"); the scan
 * row is then linked to that plant so per-plant history (Phase 6) can filter by it.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val classifier: DiseaseClassifier,
    private val saliencyGenerator: SaliencyGenerator,
    private val suggestionEngine: SuggestionEngine,
    private val holder: ScanResultHolder,
    private val scanRepository: ScanRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Analyzing(val stage: String) : UiState
        data class Error(val message: String) : UiState
        data class Done(val scanId: Long) : UiState
    }

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun analyze(bitmap: Bitmap, plantId: Long? = null) {
        if (_ui.value is UiState.Analyzing) return
        viewModelScope.launch {
            _ui.value = UiState.Analyzing("Classifying leaf…")
            try {
                val diagnosis = withContext(io) { classifier.classify(bitmap) }
                _ui.value = UiState.Analyzing("Generating explanation heatmap…")
                val saliency = withContext(io) { saliencyGenerator.generate(bitmap) }
                val suggestion = suggestionEngine.suggest(diagnosis.topLabel)
                _ui.value = UiState.Analyzing("Saving scan…")
                val scanId = withContext(io) {
                    scanRepository.saveScan(
                        ScanEntity(
                            plantId = plantId,
                            labelName = diagnosis.topLabel.name,
                            labelCondition = diagnosis.topLabel.condition,
                            confidence = diagnosis.topConfidence,
                        )
                    )
                }
                holder.set(ScanResult(diagnosis, suggestion, saliency, bitmap))
                _ui.value = UiState.Done(scanId)
            } catch (e: Exception) {
                _ui.value = UiState.Error(e.message ?: "Analysis failed.")
            }
        }
    }

    fun reset() {
        _ui.value = UiState.Idle
    }
}
