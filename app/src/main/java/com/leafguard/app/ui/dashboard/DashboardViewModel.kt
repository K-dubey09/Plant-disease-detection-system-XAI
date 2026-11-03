package com.leafguard.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.local.Plant
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.domain.repository.PlantRepository
import com.leafguard.app.domain.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Phase 6 dashboard data. Exposes the live scan + plant lists (already DESC-by-createdAt from the
 * DAO, so [scans].first() is the most recent diagnosis); the screen derives the headline stats
 * (total scans, plants tracked, most-recent disease) from these. Eagerly started so the current
 * value is available synchronously for the screen and for tests, matching [PlantsViewModel].
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    scanRepository: ScanRepository,
    plantRepository: PlantRepository,
) : ViewModel() {

    val scans: StateFlow<List<ScanEntity>> = scanRepository.observeScans()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val plants: StateFlow<List<Plant>> = plantRepository.observePlants()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
