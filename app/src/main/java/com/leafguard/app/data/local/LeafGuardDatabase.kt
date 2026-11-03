package com.leafguard.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * LeafGuard's local store. Grows one entity group per phase: Phase 3 adds [Plant]; Phase 5 adds
 * [ScanEntity] (the scans table, linked to plants by a nullable foreign key with ON DELETE SET
 * NULL so a plant's diagnosis history survives its deletion). `exportSchema = false` keeps the
 * build lean for now; enable it once the schema stabilizes before production.
 *
 * Version 2 ships the scans table; [MIGRATION_1_2] adds it for existing v1 installs so an
 * in-place app upgrade does not lose the user's plants (Room throws IllegalStateException on a
 * version bump without a migration or fallbackToDestructiveMigration).
 */
@Database(
    entities = [Plant::class, ScanEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class LeafGuardDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun scanDao(): ScanDao

    companion object {
        const val NAME = "leafguard.db"

        /** Phase 5: add the `scans` table with its plant foreign key + index. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scans` (
                        `id` INTEGER NOT NULL,
                        `plantId` INTEGER,
                        `labelName` TEXT NOT NULL,
                        `labelCondition` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_scans_plantId` ON `scans` (`plantId`)")
            }
        }
    }
}
