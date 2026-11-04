package com.leafguard.app.ui.records

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Phase 6 Records. Groups every persisted scan by its plant into a per-plant disease log
 * ([PlantRecordGroup]); each group's scans stay DESC by createdAt (the DAO order), and the groups
 * themselves sort newest-scan-first so the most recently active plant leads. The plant name is
 * resolved from the plants list -- null (rendered "Unattached") when the scan has no plant or its
 * plant was deleted (Room's ON DELETE SET NULL clears plantId on delete, so this is consistent).
 *
 * Export (CSV via [CsvGenerator], PDF via [PdfExporter]) is deliberately kept out of the ViewModel:
 * both need an Android [android.content.Context] for file I/O, which would make this class
 * untestable on the JVM. The screen reads [scans] + [plants] and does the file write, while the
 * pure CSV layout is unit-tested via [CsvGenerator].
 */
@HiltViewModel
class RecordsViewModel @Inject constructor(
    scanRepository: ScanRepository,
    plantRepository: PlantRepository,
) : ViewModel() {

    val scans: StateFlow<List<ScanEntity>> = scanRepository.observeScans()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val plants: StateFlow<List<Plant>> = plantRepository.observePlants()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groups: StateFlow<List<PlantRecordGroup>> =
        combine(scanRepository.observeScans(), plantRepository.observePlants()) { scans, plants ->
            val byId = plants.associateBy { it.id }
            scans
                .groupBy { it.plantId }
                .map { (plantId, groupScans) ->
                    PlantRecordGroup(
                        plantId = plantId,
                        plantName = plantId?.let { byId[it]?.name },
                        scans = groupScans,
                        mostRecentAt = groupScans.firstOrNull()?.createdAt,
                        count = groupScans.size,
                    )
                }
                .sortedByDescending { it.mostRecentAt ?: 0L }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

/**
 * One plant's disease log: its scans (DESC by createdAt), the resolved display name (null =
 * Unattached), and the most-recent scan timestamp (for sorting + the "last seen" caption).
 */
data class PlantRecordGroup(
    val plantId: Long?,
    val plantName: String?,
    val scans: List<ScanEntity>,
    val mostRecentAt: Long?,
    val count: Int,
)
