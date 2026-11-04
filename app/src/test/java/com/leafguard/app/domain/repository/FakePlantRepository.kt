package com.leafguard.app.domain.repository

import com.leafguard.app.data.local.Plant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake [PlantRepository] for [com.leafguard.app.ui.plants.PlantsViewModelTest]. Maintains an
 * in-memory list exposed as a [MutableStateFlow] so the VM's `stateIn` observes realistic
 * updates; records call counts for assertions.
 */
class FakePlantRepository(initial: List<Plant> = emptyList()) : PlantRepository {

    private val state = MutableStateFlow(initial)
    var addCalls = 0; var deleteCalls = 0; var updateCalls = 0
    var lastAddArgs: Triple<String, String, String>? = null
    var lastDeleteId: Long? = null

    override fun observePlants(): Flow<List<Plant>> = state
    override fun observePlant(id: Long): Flow<Plant?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getPlant(id: Long): Plant? = state.value.firstOrNull { it.id == id }

    override suspend fun addPlant(name: String, species: String, notes: String): Long {
        addCalls += 1
        lastAddArgs = Triple(name, species, notes)
        if (name.isBlank()) throw IllegalArgumentException("blank name")
        val id = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        state.value = state.value + Plant(id = id, name = name.trim(), species = species.trim(), notes = notes.trim())
        return id
    }

    override suspend fun updatePlant(plant: Plant) {
        updateCalls += 1
        state.value = state.value.map { if (it.id == plant.id) plant else it }
    }

    override suspend fun deletePlant(id: Long) {
        deleteCalls += 1
        lastDeleteId = id
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun count(): Int = state.value.size
}
