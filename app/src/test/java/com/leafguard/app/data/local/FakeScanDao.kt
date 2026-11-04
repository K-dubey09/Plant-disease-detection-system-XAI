package com.leafguard.app.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ScanDao] for JVM unit tests of [com.leafguard.app.data.repository.ScanRepositoryImpl].
 * Mirrors Room's semantics (auto-increment id, live observe via a backing [MutableStateFlow],
 * nullable-plantId filtering) without needing the Android framework. Room's real query path
 * (incl. the ForeignKey ON DELETE SET NULL behaviour) is exercised by an instrumented test.
 */
class FakeScanDao : ScanDao {

    private val rows = mutableListOf<ScanEntity>()
    private val state = MutableStateFlow<List<ScanEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ScanEntity>> = state

    override fun observeByPlant(plantId: Long): Flow<List<ScanEntity>> =
        state.map { list -> list.filter { it.plantId == plantId } }

    override fun observeById(id: Long): Flow<ScanEntity?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): ScanEntity? = rows.firstOrNull { it.id == id }

    override suspend fun insert(scan: ScanEntity): Long {
        val id = if (scan.id == 0L) nextId++ else scan.id.also { if (it >= nextId) nextId = it + 1 }
        val stored = scan.copy(id = id)
        rows.add(stored)
        publish()
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
        publish()
    }

    override suspend fun count(): Int = rows.size

    override suspend fun updatePlantId(id: Long, plantId: Long?) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) {
            rows[idx] = rows[idx].copy(plantId = plantId)
            publish()
        }
    }

    override suspend fun updateSaliencyImagePath(id: Long, path: String?) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) {
            rows[idx] = rows[idx].copy(saliencyImagePath = path)
            publish()
        }
    }

    private fun publish() {
        state.value = rows.sortedByDescending { it.createdAt }
    }
}
