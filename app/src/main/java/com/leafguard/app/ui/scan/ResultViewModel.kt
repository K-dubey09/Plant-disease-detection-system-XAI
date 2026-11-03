package com.leafguard.app.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.inference.ScanResultHolder
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.domain.repository.ScanRepository
import com.leafguard.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the most recent scan for the result screen. Two sources:
 *  - [result]: the in-memory [ScanResult] (Bitmaps + diagnosis + suggestion) from
 *    [ScanResultHolder], set by [ScanViewModel]. Null only after process death.
 *  - [scan]: the persisted [ScanEntity] for the `result/{scanId}` route, loaded from Room so the
 *    diagnosis metadata survives process death and so Phase 6 history -> result navigation works
 *    even when [ScanResultHolder] is empty (the Bitmaps are not serialized, so a process-death
 *    recovery shows metadata without the source/heatmap pair).
 */
@HiltViewModel
class ResultViewModel @Inject constructor(
    holder: ScanResultHolder,
    scanRepository: ScanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val result: StateFlow<ScanResult?> = holder.state

    private val scanId: Long = savedStateHandle.get<Long>(Routes.RESULT_ARG_SCAN_ID) ?: -1L

    val scan: StateFlow<ScanEntity?> =
        scanRepository.observeScan(scanId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
