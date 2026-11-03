package com.leafguard.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * LeafGuard's local store. Grows one entity group per phase: Phase 3 adds [Plant]; later phases
 * add ScanRecord (with a Grad-CAM image path) and treatment suggestions. `exportSchema = false`
 * keeps the build lean for now; enable it once the schema stabilizes before production.
 */
@Database(
    entities = [Plant::class],
    version = 1,
    exportSchema = false,
)
abstract class LeafGuardDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao

    companion object {
        const val NAME = "leafguard.db"
    }
}
