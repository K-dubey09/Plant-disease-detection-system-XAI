package com.leafguard.app.ui.images

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
 * Phase 6 Images gallery. Groups persisted scans that have a stored source image
 * ([ScanEntity.sourceImagePath] != null) by plant, so the gallery shows only scans whose leaf photo
 * survived to disk (scans from before Phase 6 stored no image and are excluded). Each group's scans
 * stay DESC by createdAt (the DAO order); groups sort newest-scan-first. The plant name is resolved
 * from the plants list -- null (rendered "Unattached") when the scan has no plant or its plant was
 * deleted (Room's ON DELETE SET NULL clears plantId on delete).
 *
 * The actual Bitmap decoding + display is done by Coil in [com.leafguard.app.ui.screens.ImagesScreen]
 * (AsyncImage(model = File(path))); this ViewModel only shapes the grouped list, so it is JVM-testable.
 */
@HiltViewModel
class ImagesViewModel @Inject constructor(
    scanRepository: ScanRepository,
    plantRepository: PlantRepository,
) : ViewModel() {

    val groups: StateFlow<List<ImageGroup>> =
        combine(scanRepository.observeScans(), plantRepository.observePlants()) { scans, plants ->
            val byId = plants.associateBy { it.id }
            scans
                .filter { it.sourceImagePath != null }
                .groupBy { it.plantId }
                .map { (plantId, groupScans) ->
                    ImageGroup(
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

/** One plant's image gallery: its image-bearing scans (DESC), resolved name (null = Unattached). */
data class ImageGroup(
    val plantId: Long?,
    val plantName: String?,
    val scans: List<ScanEntity>,
    val mostRecentAt: Long?,
    val count: Int,
)
