package com.leafguard.app.data.inference

import android.graphics.Bitmap
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.Suggestion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The most recent scan's outcome, shared between the scan screen (producer) and the result screen
 * (consumer) without going through Room. In-memory only; cleared on a new scan. [Bitmap]s are
 * Android references, not serialized.
 *
 * [saliency] is nullable: since Phase 9 the diagnosis is published immediately and the
 * occlusion-sensitivity heatmap is streamed in later via [ScanResultHolder.updateSaliency] once the
 * (slow, 64-pass) [SaliencyGenerator] finishes. The result screen renders a placeholder while it is
 * null, so the user sees their diagnosis at once instead of waiting on the heatmap.
 *
 * [scanId] lets [updateSaliency] tell a stale heatmap (from a scan the user has since superseded
 * with a newer one) from the current scan's heatmap, so a late background result never overwrites a
 * newer scan's holder.
 */
data class ScanResult(
    val scanId: Long,
    val diagnosis: Diagnosis,
    val suggestion: Suggestion?,
    val source: Bitmap,
    val saliency: Bitmap? = null,
)

@Singleton
class ScanResultHolder @Inject constructor() {
    private val _state = MutableStateFlow<ScanResult?>(null)
    val state: StateFlow<ScanResult?> = _state.asStateFlow()

    fun set(result: ScanResult) {
        _state.value = result
    }

    /**
     * Streams a completed heatmap onto the current result, but only if the holder still holds the
     * scan that launched this heatmap ([scanId] match). A newer scan overwrites the holder with its
     * own [set] call, so a straggling heatmap from the previous scan is dropped instead of clobbering
     * the new result. Room-side persistence ([ScanEntity.saliencyImagePath]) is keyed by scan id and
     * is therefore correct regardless of which scan is currently on screen.
     */
    fun updateSaliency(scanId: Long, saliency: Bitmap) {
        val current = _state.value
        if (current != null && current.scanId == scanId) {
            _state.value = current.copy(saliency = saliency)
        }
    }

    fun clear() {
        _state.value = null
    }
}
