package com.leafguard.app.data.repository

import com.leafguard.app.data.local.ScanDao
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.di.IoDispatcher
import com.leafguard.app.domain.repository.ScanRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * [ScanRepository] backed by [ScanDao]. Read/query flows are piped through [ioDispatcher] via
 * [flowOn] so Room queries run off the main thread; mutations run in [withContext] for the same
 * reason and so the repository stays single-threaded for tests with a fake DAO. Mirrors
 * [PlantRepositoryImpl].
 */
class ScanRepositoryImpl @Inject constructor(
    private val dao: ScanDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ScanRepository {

    override fun observeScans(): Flow<List<ScanEntity>> = dao.observeAll().flowOn(io)

    override fun observeScansForPlant(plantId: Long): Flow<List<ScanEntity>> =
        dao.observeByPlant(plantId).flowOn(io)

    override fun observeScan(id: Long): Flow<ScanEntity?> = dao.observeById(id).flowOn(io)

    override suspend fun getScan(id: Long): ScanEntity? = withContext(io) { dao.getById(id) }

    override suspend fun saveScan(scan: ScanEntity): Long = withContext(io) { dao.insert(scan) }

    override suspend fun deleteScan(id: Long) = withContext(io) { dao.deleteById(id) }

    override suspend fun count(): Int = withContext(io) { dao.count() }

    override suspend fun attachScanToPlant(scanId: Long, plantId: Long?) =
        withContext(io) { dao.updatePlantId(scanId, plantId) }

    override suspend fun setSaliencyImage(scanId: Long, path: String?) =
        withContext(io) { dao.updateSaliencyImagePath(scanId, path) }
}
