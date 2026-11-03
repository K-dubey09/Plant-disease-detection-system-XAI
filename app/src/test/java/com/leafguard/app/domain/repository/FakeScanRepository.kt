package com.leafguard.app.domain.repository

import com.leafguard.app.data.local.ScanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ScanRepository] for JVM unit tests of ViewModels that consume scans (Phase 6+).
 * Mirrors [com.leafguard.app.domain.repository.FakePlantRepository]'s shape so ViewModel tests
 * stay symmetric with the Plants tests.
 */
class FakeScanRepository(initial: List<ScanEntity> = emptyList()) : ScanRepository {

    private val rows = mutableListOf<ScanEntity>().apply { addAll(initial) }
    private val state = MutableStateFlow<List<ScanEntity>>(rows.sortedByDescending { it.createdAt })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeScans(): Flow<List<ScanEntity>> = state

    override fun observeScansForPlant(plantId: Long): Flow<List<ScanEntity>> =
        state.map { list -> list.filter { it.plantId == plantId } }

    override fun observeScan(id: Long): Flow<ScanEntity?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getScan(id: Long): ScanEntity? = rows.firstOrNull { it.id == id }

    override suspend fun saveScan(scan: ScanEntity): Long {
        val id = if (scan.id == 0L) nextId++ else scan.id.also { if (it >= nextId) nextId = it + 1 }
        rows.add(scan.copy(id = id))
        publish()
        return id
    }

    override suspend fun deleteScan(id: Long) {
        rows.removeAll { it.id == id }
        publish()
    }

    override suspend fun count(): Int = rows.size

    private fun publish() {
        state.value = rows.sortedByDescending { it.createdAt }
    }
}
