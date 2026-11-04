package com.leafguard.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test of [PlantDao] against an in-memory Room database on a device/emulator.
 * Complements the JVM [FakePlantDao]-based repository test by exercising the real SQLite + Room
 * query codegen path that KSP generated at build time.
 */
@RunWith(AndroidJUnit4::class)
class PlantDaoTest {

    private lateinit var database: LeafGuardDatabase
    private lateinit var dao: PlantDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, LeafGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.plantDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_andObserveAll() = runTest {
        val id = dao.insert(Plant(name = "Tomato", species = "Roma"))
        assertThat(id).isGreaterThan(0L)
        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].name).isEqualTo("Tomato")
    }

    @Test
    fun update_changesRow() = runTest {
        val id = dao.insert(Plant(name = "A"))
        dao.update(dao.getById(id)!!.copy(notes = "watered"))
        assertThat(dao.getById(id)?.notes).isEqualTo("watered")
    }

    @Test
    fun deleteById_removesRow() = runTest {
        val id = dao.insert(Plant(name = "A"))
        dao.deleteById(id)
        assertThat(dao.count()).isEqualTo(0)
        assertThat(dao.getById(id)).isNull()
    }

    @Test
    fun observeById_emitsNullForMissing() = runTest {
        assertThat(dao.observeById(999L).first()).isNull()
    }
}
