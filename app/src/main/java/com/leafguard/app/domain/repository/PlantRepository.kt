package com.leafguard.app.domain.repository

import com.leafguard.app.data.local.Plant
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the user's plants. The UI observes the [Flow]s so it reacts to
 * background writes; the suspend functions perform one-shot mutations.
 */
interface PlantRepository {
    fun observePlants(): Flow<List<Plant>>
    fun observePlant(id: Long): Flow<Plant?>
    suspend fun getPlant(id: Long): Plant?
    suspend fun addPlant(name: String, species: String, notes: String): Long
    suspend fun updatePlant(plant: Plant)
    suspend fun deletePlant(id: Long)
    suspend fun count(): Int
}
