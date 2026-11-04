package com.leafguard.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.inference.LeafClassifier
import com.leafguard.app.data.inference.ModelIntegrity
import com.leafguard.app.data.inference.SaliencyRunner
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.inference.ScanResultHolder
import com.leafguard.app.data.inference.SuggestionEngine
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.di.IoDispatcher
import com.leafguard.app.domain.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the classify -> suggest -> persist pipeline for a leaf photo, then publishes the result so
 * the UI can navigate to `result/{scanId}` immediately (Phase 9). The occlusion-sensitivity
 * heatmap is computed afterwards in an independent background coroutine and streamed onto the
 * result screen via [ScanResultHolder.updateSaliency] when ready, so the user sees their diagnosis
 * at once instead of staring at a "Generating explanation…" spinner for the ~6-32 s the 64-pass
 * [SaliencyGenerator] takes.
 *
 * Phase 9 reordering (the fix for "scan page stuck in scanning state"): previously `Done(scanId)`
 * was emitted only after classify -> saliency -> suggest -> save all completed serially, so the
 * scan screen stayed on the Analyzing spinner for the whole heatmap. Now the scan is persisted and
 * `Done` is emitted right after classify + suggest + source-image save (one forward pass — a second
 * or two), and history updates at the same instant because Room's observe Flow reacts to the
 * `saveScan` insert. The heatmap is best-effort: if it throws (OOM, TFLite error) the scan is
 * already complete and the failure is swallowed — the result screen simply stays without a heatmap
 * instead of stranding the UI.
 *
 * The UI observes [ui] to show staging ("Classifying…", "Saving scan…"). The heatmap stage no
 * longer gates [ui] (it shows on the result screen via the nullable [ScanResult.saliency]).
 *
 * [plantId] is non-null when the scan was launched from PlantDetail ("Scan this plant"); the scan
 * row is then linked to that plant so per-plant history (Phase 6) can filter by it. An unattached
 * scan can later be linked from the result screen ("Add to plant") to start tracking growth.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val classifier: LeafClassifier,
    private val saliencyRunner: SaliencyRunner,
    private val suggestionEngine: SuggestionEngine,
    private val holder: ScanResultHolder,
    private val scanRepository: ScanRepository,
    private val modelIntegrity: ModelIntegrity,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /**
     * True when the bundled model's SHA-256 no longer matches the digest captured at build time —
     * i.e. the .tflite was modified after the APK was signed. The scan screen surfaces a non-blocking
     * "model integrity warning" banner so the user knows the diagnosis is untrusted, without refusing
     * to scan (the classification still runs; it is the *trust* in it that is flagged).
     */
    val modelTampered: Boolean = !modelIntegrity.isIntact()

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
                // Fast path: one forward pass + suggestion + persist + navigate. History updates
                // the instant saveScan inserts the row (Room's observe Flow fires on its own).
                val diagnosis = withContext(io) { classifier.classify(bitmap) }
                val suggestion = suggestionEngine.suggest(diagnosis.topLabel)
                _ui.value = UiState.Analyzing("Saving scan…")
                val sourcePath = withContext(io) { saveBitmap(bitmap, "source", Bitmap.CompressFormat.JPEG) }
                val scanId = withContext(io) {
                    scanRepository.saveScan(
                        ScanEntity(
                            plantId = plantId,
                            labelName = diagnosis.topLabel.name,
                            labelCondition = diagnosis.topLabel.condition,
                            confidence = diagnosis.topConfidence,
                            sourceImagePath = sourcePath,
                            // Back-filled by the heatmap coroutine below once it finishes.
                            saliencyImagePath = null,
                        ),
                    )
                }
                // Publish WITHOUT the heatmap so the result screen renders the diagnosis at once;
                // saliency is nullable and shown as a placeholder until updateSaliency streams it in.
                holder.set(ScanResult(scanId, diagnosis, suggestion, bitmap, saliency = null))
                _ui.value = UiState.Done(scanId)

                // Slow path: occlusion heatmap, delegated to [SaliencyRunner] which runs on an
                // APPLICATION-scoped coroutine. This is the fix for the Phase 9 regression where the
                // heatmap was a viewModelScope sibling: that scope is cancelled when this ViewModel
                // is cleared (scan -> result -> dashboard pops the scan entry), which aborted
                // in-flight heatmaps and left saliencyImagePath null forever. The application scope
                // outlives this ViewModel, so the heatmap always runs to completion.
                saliencyRunner.generateFor(scanId, bitmap)
            } catch (e: Exception) {
                _ui.value = UiState.Error(e.message ?: "Analysis failed.")
            }
        }
    }

    fun reset() {
        _ui.value = UiState.Idle
    }

    /**
     * Persists a [Bitmap] to internal storage (filesDir/scans/) so the Phase 6 Images gallery can
     * render it later without the in-memory copy (which is held by [ScanResultHolder] only for the
     * active result screen and is lost on process death). Returns the absolute path, or null on
     * failure so the scan still saves its metadata when image persistence is unavailable.
     */
    private fun saveBitmap(bitmap: Bitmap, prefix: String, format: Bitmap.CompressFormat): String? =
        try {
            val dir = File(context.filesDir, "scans").apply { mkdirs() }
            val ext = if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png"
            val file = File(dir, "scan_${System.currentTimeMillis()}_$prefix.$ext")
            FileOutputStream(file).use { out -> bitmap.compress(format, 90, out) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
}
