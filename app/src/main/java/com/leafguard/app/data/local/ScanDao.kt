package com.leafguard.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [ScanEntity]. Mirrors [PlantDao]: Flow return types for observe (the UI reacts to
 * background writes), suspend for one-shot mutations. The per-plant query drives Phase 6 history.
 */
@Dao
interface ScanDao {

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE plantId = :plantId ORDER BY createdAt DESC")
    fun observeByPlant(plantId: Long): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE id = :id")
    fun observeById(id: Long): Flow<ScanEntity?>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): ScanEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(scan: ScanEntity): Long

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM scans")
    suspend fun count(): Int

    /** Phase 9: link (or re-link/unlink) a scan to a plant so it joins that plant's growth timeline. */
    @Query("UPDATE scans SET plantId = :plantId WHERE id = :id")
    suspend fun updatePlantId(id: Long, plantId: Long?)

    /** Phase 9: back-fill the saliency image path once the (slow) heatmap finishes in the background. */
    @Query("UPDATE scans SET saliencyImagePath = :path WHERE id = :id")
    suspend fun updateSaliencyImagePath(id: Long, path: String?)
}
