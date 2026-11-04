package com.leafguard.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.local.Plant
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.domain.repository.PlantRepository
import com.leafguard.app.domain.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Phase 6 scan history. Joins each persisted [ScanEntity] with its [Plant] name for display and
 * applies the user's plant filter. [items] is the filtered, display-ready list; [plants] backs the
 * filter chips; [selectedPlantId] is the active filter (null = all plants).
 *
 * The join is done in-memory here (scans + plants are small per-user lists) rather than via a Room
 * @Relation query, so the logic is unit-testable with the existing fakes and stays off the DB layer.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    scanRepository: ScanRepository,
    plantRepository: PlantRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow<Long?>(null)
    val selectedPlantId: StateFlow<Long?> = _filter.asStateFlow()

    val plants: StateFlow<List<Plant>> = plantRepository.observePlants()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<HistoryItem>> =
        combine(scanRepository.observeScans(), plantRepository.observePlants(), _filter) { scans, plants, filter ->
            val byId = plants.associateBy { it.id }
            val filtered = if (filter == null) scans else scans.filter { it.plantId == filter }
            filtered.map { scan -> HistoryItem(scan, plantName = scan.plantId?.let { byId[it]?.name }) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setFilter(plantId: Long?) {
        _filter.value = plantId
    }
}

/** A scan plus its resolved plant name (null when the scan is unattached or its plant was deleted). */
data class HistoryItem(
    val scan: ScanEntity,
    val plantName: String?,
)
