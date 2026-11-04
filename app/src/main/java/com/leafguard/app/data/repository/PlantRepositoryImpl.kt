package com.leafguard.app.data.repository

import com.leafguard.app.data.local.Plant
import com.leafguard.app.data.local.PlantDao
import com.leafguard.app.di.IoDispatcher
import com.leafguard.app.domain.repository.PlantRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [PlantRepository] backed by [PlantDao]. Read/query flows are piped through [ioDispatcher] via
 * [flowOn] so Room queries run off the main thread; mutations run in [withContext] for the same
 * reason and so the repository stays single-threaded for tests with a fake DAO.
 */
class PlantRepositoryImpl @Inject constructor(
    private val dao: PlantDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PlantRepository {

    override fun observePlants(): Flow<List<Plant>> = dao.observeAll().flowOn(io)

    override fun observePlant(id: Long): Flow<Plant?> = dao.observeById(id).flowOn(io)

    override suspend fun getPlant(id: Long): Plant? = withContext(io) { dao.getById(id) }

    override suspend fun addPlant(name: String, species: String, notes: String): Long =
        withContext(io) {
            val trimmedName = name.trim()
            require(trimmedName.isNotEmpty()) { "Plant name must not be blank" }
            dao.insert(
                Plant(
                    name = trimmedName,
                    species = species.trim(),
                    notes = notes.trim(),
                )
            )
        }

    override suspend fun updatePlant(plant: Plant) = withContext(io) { dao.update(plant) }

    override suspend fun deletePlant(id: Long) = withContext(io) { dao.deleteById(id) }

    override suspend fun count(): Int = withContext(io) { dao.count() }
}
