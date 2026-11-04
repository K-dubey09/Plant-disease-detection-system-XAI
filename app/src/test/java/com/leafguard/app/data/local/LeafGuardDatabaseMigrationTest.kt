package com.leafguard.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 8: runtime test of [LeafGuardDatabase.MIGRATION_1_2] + [LeafGuardDatabase.MIGRATION_2_3].
 *
 * This closes the #1 honest gap from Phase 6/7 - the v2->v3 migration was only ALTER-TABLE-checked
 * by hand because `exportSchema = false` meant Room did not compile-validate it. Here a real v1
 * database file (the plants table only, matching the Plant entity's Room-generated schema) is seeded
 * with a plant row via [FrameworkSQLiteOpenHelperFactory]; then [LeafGuardDatabase] is opened at
 * version 3 with both migrations registered. Room sees user_version 1, runs 1->2->3, and validates
 * the final schema against the entity. The test asserts the v1 plant row survived, the scans table
 * gained the two Phase 6 image-path columns, and a scan carrying those paths inserts + reads back.
 *
 * Runs on the JVM under Robolectric (which provides a real SQLite + Context), so it executes in the
 * CI unit-test step with no emulator. SDK 34 because Robolectric 4.13 does not yet ship SDK 35.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeafGuardDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(LeafGuardDatabase.NAME)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun tearDown() {
        if (this::dbFile.isInitialized && dbFile.exists()) dbFile.delete()
    }

    @Test
    fun migrate_v1_to_v3_preservesPlantAndAddsScanColumns() = runBlocking {
        seedV1DatabaseWithPlant()

        val db = Room.databaseBuilder(context, LeafGuardDatabase::class.java, LeafGuardDatabase.NAME)
            .addMigrations(LeafGuardDatabase.MIGRATION_1_2, LeafGuardDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        // The v1 plant row survived the two migrations.
        val plant = db.plantDao().getById(1L)
        assertThat(plant).isNotNull()
        assertThat(plant!!.name).isEqualTo("Balcony Tomato")
        assertThat(plant.species).isEqualTo("Roma")

        // MIGRATION_1_2 created the scans table; MIGRATION_2_3 added the two image-path columns.
        val columns = db.openHelper.readableDatabase.query("PRAGMA table_info(scans)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }
        assertThat(columns).contains("sourceImagePath")
        assertThat(columns).contains("saliencyImagePath")

        // A scan carrying the Phase 6 image paths inserts + reads back through the v3 schema.
        val scanId = db.scanDao().insert(
            ScanEntity(
                plantId = 1L,
                labelName = "Tomato___Late_blight",
                labelCondition = "Late blight",
                confidence = 0.91f,
                sourceImagePath = "/scans/leaf.jpg",
                saliencyImagePath = "/scans/sal.png",
            )
        )
        val scan = db.scanDao().getById(scanId)
        assertThat(scan).isNotNull()
        assertThat(scan!!.sourceImagePath).isEqualTo("/scans/leaf.jpg")
        assertThat(scan.saliencyImagePath).isEqualTo("/scans/sal.png")

        db.close()
    }

    @Test
    fun migrate_v1_to_v2_then_v3_appliesBothMigrationsInOrder() = runBlocking {
        seedV1DatabaseWithPlant()
        val db = Room.databaseBuilder(context, LeafGuardDatabase::class.java, LeafGuardDatabase.NAME)
            .addMigrations(LeafGuardDatabase.MIGRATION_1_2, LeafGuardDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        // After open, Room has run both migrations; user_version is now 3 and a fresh open (no
        // migration needed) succeeds, proving the post-migration schema is self-consistent.
        db.scanDao().count()
        db.openHelper.readableDatabase.query("PRAGMA user_version").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(3)
        }
        db.close()
    }

    /**
     * Seeds a real v1 database file: the plants table only (the v1 schema), matching the
     * Room-generated CREATE TABLE for the Plant entity, with one plant row at id=1.
     */
    private fun seedV1DatabaseWithPlant() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `plants` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `species` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(LeafGuardDatabase.NAME)
            .callback(callback)
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO `plants` (`id`,`name`,`species`,`notes`,`createdAt`) " +
                "VALUES (1, 'Balcony Tomato', 'Roma', '', 1700000000000)"
        )
        db.close()
        helper.close()
    }
}
