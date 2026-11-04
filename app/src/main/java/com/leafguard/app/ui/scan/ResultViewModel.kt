package com.leafguard.app.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.inference.ModelIntegrity
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.inference.ScanResultHolder
import com.leafguard.app.data.local.Plant
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.domain.repository.PlantRepository
import com.leafguard.app.domain.repository.ScanRepository
import com.leafguard.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reads the most recent scan for the result screen. Two sources:
 *  - [result]: the in-memory [ScanResult] (Bitmaps + diagnosis + suggestion) from
 *    [ScanResultHolder], set by [ScanViewModel]. Null only after process death.
 *  - [scan]: the persisted [ScanEntity] for the `result/{scanId}` route, loaded from Room so the
 *    diagnosis metadata survives process death and so Phase 6 history -> result navigation works
 *    even when [ScanResultHolder] is empty (the Bitmaps are not serialized, so a process-death
 *    recovery shows metadata without the source/heatmap pair).
 *
 * Phase 9 growth tracking: [plants] + [attachedPlantId] back the "Add to plant" action — an
 * unattached scan (or one whose plant was deleted) can be linked to any plant from the result
 * screen so it joins that plant's per-plant Records timeline. [attachToPlant] /
 * [createPlantAndAttach] mutate the row; Room's observe Flow then re-emits [scan] and
 * [attachedPlantId] reactively.
 */
@HiltViewModel
class ResultViewModel @Inject constructor(
    holder: ScanResultHolder,
    private val scanRepository: ScanRepository,
    private val plantRepository: PlantRepository,
    private val modelIntegrity: ModelIntegrity,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * True when the bundled model's SHA-256 no longer matches the build-time digest — the .tflite
     * was modified after the APK was signed. The result screen shows a "model integrity warning"
     * banner so a tampered-model diagnosis is presented as untrusted rather than authoritative,
     * without hiding the rest of the result (the heatmap, suggestion, and growth-tracking actions
     * stay available).
     */
    val modelTampered: Boolean = !modelIntegrity.isIntact()

    val result: StateFlow<ScanResult?> = holder.state

    private val scanId: Long = savedStateHandle.get<Long>(Routes.RESULT_ARG_SCAN_ID) ?: -1L

    val scan: StateFlow<ScanEntity?> =
        scanRepository.observeScan(scanId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every plant the user tracks, for the "Add to plant" picker. */
    val plants: StateFlow<List<Plant>> =
        plantRepository.observePlants()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The plant id this scan is currently linked to (null = unattached). Drives the attach UI. */
    val attachedPlantId: StateFlow<Long?> =
        scan.map { it?.plantId }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Links this scan to [plantId] (or unlinks it when null) so it joins that plant's growth timeline. */
    fun attachToPlant(plantId: Long?) {
        viewModelScope.launch { scanRepository.attachScanToPlant(scanId, plantId) }
    }

    /** Creates a new plant and links this scan to it in one go — the common "save this scan as a new
     *  plant's first recorded state" flow for tracking a plant's growth from a fresh diagnosis. */
    fun createPlantAndAttach(name: String, species: String) {
        viewModelScope.launch {
            val newPlantId = plantRepository.addPlant(name, species, "")
            scanRepository.attachScanToPlant(scanId, newPlantId)
        }
    }
}
