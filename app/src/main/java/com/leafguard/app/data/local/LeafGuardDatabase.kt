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
 * Version 2 ships the scans table; [MIGRATION_1_2] adds it for existing v1 installs. Version 3
 * (Phase 6 Images) adds [ScanEntity.sourceImagePath] / [saliencyImagePath] so the gallery can
 * render past scan images; [MIGRATION_2_3] ALTER-TABLEs the two nullable columns in place, so an
 * upgrade preserves every prior scan and plant. Room throws IllegalStateException on a version
 * bump without a migration or fallbackToDestructiveMigration, so both migrations are registered.
 */
@Database(
    entities = [Plant::class, ScanEntity::class],
    version = 3,
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

        /** Phase 6 Images: add nullable image-path columns to `scans` for the gallery. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scans ADD COLUMN sourceImagePath TEXT")
                database.execSQL("ALTER TABLE scans ADD COLUMN saliencyImagePath TEXT")
            }
        }
    }
}
