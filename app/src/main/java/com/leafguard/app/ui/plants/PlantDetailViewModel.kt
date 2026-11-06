package com.leafguard.app.ui.plants

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.local.Plant
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.domain.repository.PlantRepository
import com.leafguard.app.domain.repository.ScanRepository
import com.leafguard.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the plant detail screen. Reads `plantId` from the nav [SavedStateHandle], observes the
 * plant, and supports editing name/species/notes + deletion. As of v2.7.0.0 it also exposes the
 * plant's scan timeline ([scans]) via [ScanRepository.observeScansForPlant] so the detail screen can
 * render that plant's checks in recent-to-last order alongside a progress summary; tapping a check
 * opens the full result screen for that scan.
 */
@HiltViewModel
class PlantDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plantRepository: PlantRepository,
    private val scanRepository: ScanRepository,
) : ViewModel() {

    // Routes.PLANT_DETAIL declares a Long "plantId" arg; Hilt's SavedStateHandle carries it.
    val plantId: Long = savedStateHandle.get<Long>("plantId") ?: -1L

    val plant: StateFlow<Plant?> = plantRepository.observePlant(plantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** This plant's scans, newest-first (the DAO returns `ORDER BY createdAt DESC`). */
    val scans: StateFlow<List<ScanEntity>> = scanRepository.observeScansForPlant(plantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _savedTick = MutableStateFlow(0L)
    val savedTick: StateFlow<Long> = _savedTick.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun update(name: String, species: String, notes: String) {
        if (_busy.value) return
        val current = plant.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                plantRepository.updatePlant(current.copy(name = name.trim(), species = species.trim(), notes = notes.trim()))
                _savedTick.value = System.currentTimeMillis()
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (plantId <= 0L) return
        viewModelScope.launch {
            plantRepository.deletePlant(plantId)
            onDeleted()
        }
    }
}
