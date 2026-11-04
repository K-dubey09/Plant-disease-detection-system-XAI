package com.leafguard.app.domain.repository

import com.leafguard.app.data.local.ScanEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for persisted leaf scans. The UI observes the [Flow]s so it reacts to
 * new scans as they are saved; the suspend functions perform one-shot read/write mutations.
 * Mirrors [PlantRepository].
 */
interface ScanRepository {
    fun observeScans(): Flow<List<ScanEntity>>
    fun observeScansForPlant(plantId: Long): Flow<List<ScanEntity>>
    fun observeScan(id: Long): Flow<ScanEntity?>
    suspend fun getScan(id: Long): ScanEntity?
    suspend fun saveScan(scan: ScanEntity): Long
    suspend fun deleteScan(id: Long)
    suspend fun count(): Int

    /** Phase 9: link a scan to a plant (plantId non-null) or unlink it (null) — for growth tracking. */
    suspend fun attachScanToPlant(scanId: Long, plantId: Long?)

    /** Phase 9: back-fill the saliency image path after the heatmap finishes in the background. */
    suspend fun setSaliencyImage(scanId: Long, path: String?)
}
