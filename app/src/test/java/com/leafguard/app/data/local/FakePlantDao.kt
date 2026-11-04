package com.leafguard.app.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [PlantDao] for JVM unit tests of the repository. Mirrors Room's semantics closely
 * enough (auto-increment id, live observe via a backing [MutableStateFlow]) without needing the
 * Android framework. Room's real query path is exercised by the instrumented [PlantDaoTest].
 */
class FakePlantDao : PlantDao {

    private val rows = mutableListOf<Plant>()
    private val state = MutableStateFlow<List<Plant>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Plant>> = state

    override fun observeById(id: Long): Flow<Plant?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): Plant? = rows.firstOrNull { it.id == id }

    override suspend fun insert(plant: Plant): Long {
        val id = if (plant.id == 0L) nextId++ else plant.id.also { if (it >= nextId) nextId = it + 1 }
        val stored = plant.copy(id = id)
        rows.add(stored)
        publish()
        return id
    }

    override suspend fun update(plant: Plant) {
        val idx = rows.indexOfFirst { it.id == plant.id }
        if (idx >= 0) {
            rows[idx] = plant
            publish()
        }
    }

    override suspend fun delete(plant: Plant) {
        rows.removeAll { it.id == plant.id }
        publish()
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
        publish()
    }

    override suspend fun count(): Int = rows.size

    private fun publish() {
        state.value = rows.sortedByDescending { it.createdAt }
    }
}
