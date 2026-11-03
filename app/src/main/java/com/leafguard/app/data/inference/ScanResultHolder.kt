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
 * (consumer) without going through Room (scan history persistence arrives in a later phase).
 * In-memory only; cleared on a new scan. [Bitmap]s are Android references, not serialized.
 */
data class ScanResult(
    val diagnosis: Diagnosis,
    val suggestion: Suggestion?,
    val saliency: Bitmap,
    val source: Bitmap,
)

@Singleton
class ScanResultHolder @Inject constructor() {
    private val _state = MutableStateFlow<ScanResult?>(null)
    val state: StateFlow<ScanResult?> = _state.asStateFlow()

    fun set(result: ScanResult) {
        _state.value = result
    }

    fun clear() {
        _state.value = null
    }
}
