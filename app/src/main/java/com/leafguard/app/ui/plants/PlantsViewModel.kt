package com.leafguard.app.ui.plants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.local.Plant
import com.leafguard.app.domain.repository.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Plants list screen. Exposes the live list of plants plus a one-shot
 * [plantError] stream for blank-name / save failures, so the UI can surface a snackbar without
 * the ViewModel holding Android string resources.
 */
@HiltViewModel
class PlantsViewModel @Inject constructor(
    private val plantRepository: PlantRepository,
) : ViewModel() {

    // Eagerly so the list's current value is available synchronously (e.g. for the screen and
    // for tests) without requiring an active subscriber, matching AuthViewModel's pattern.
    val plants: StateFlow<List<Plant>> = plantRepository.observePlants()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _plantError = MutableStateFlow<PlantsError?>(null)
    val plantError: StateFlow<PlantsError?> = _plantError.asStateFlow()

    fun addPlant(name: String, species: String, notes: String) {
        if (_busy.value) return
        if (name.isBlank()) {
            _plantError.value = PlantsError.NAME_BLANK
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _plantError.value = null
            try {
                plantRepository.addPlant(name, species, notes)
            } catch (e: IllegalArgumentException) {
                _plantError.value = PlantsError.NAME_BLANK
            } finally {
                _busy.value = false
            }
        }
    }

    fun deletePlant(id: Long) {
        viewModelScope.launch { plantRepository.deletePlant(id) }
    }

    fun clearError() {
        _plantError.value = null
    }

    enum class PlantsError { NAME_BLANK }
}
