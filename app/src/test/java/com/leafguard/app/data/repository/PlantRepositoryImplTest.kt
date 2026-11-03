package com.leafguard.app.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.local.FakePlantDao
import com.leafguard.app.data.local.Plant
import com.leafguard.app.di.IoDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlantRepositoryImplTest {

    private fun repo(dao: FakePlantDao = FakePlantDao()) =
        PlantRepositoryImpl(dao, UnconfinedTestDispatcher())

    @Test
    fun observePlants_startsEmpty() = runTest {
        assertThat(repo().observePlants().first()).isEmpty()
    }

    @Test
    fun addPlant_persistsAndEmits() = runTest {
        val r = repo()
        val id = r.addPlant("Tomato", "Roma", "Balcony")
        assertThat(id).isGreaterThan(0L)
        val list = r.observePlants().first()
        assertThat(list).hasSize(1)
        assertThat(list[0].name).isEqualTo("Tomato")
        assertThat(list[0].species).isEqualTo("Roma")
    }

    @Test
    fun addPlant_trimsFields() = runTest {
        val r = repo()
        val id = r.addPlant("  Basil  ", "  Sweet  ", "  Keep moist  ")
        val p = r.getPlant(id)
        assertThat(p?.name).isEqualTo("Basil")
        assertThat(p?.species).isEqualTo("Sweet")
        assertThat(p?.notes).isEqualTo("Keep moist")
    }

    @Test(expected = IllegalArgumentException::class)
    fun addPlant_blankName_throws() = runTest {
        repo().addPlant("   ", "", "")
    }

    @Test
    fun updatePlant_changesFields() = runTest {
        val r = repo()
        val id = r.addPlant("A", "", "")
        val original = r.getPlant(id)!!
        r.updatePlant(original.copy(notes = "watered today"))
        assertThat(r.getPlant(id)?.notes).isEqualTo("watered today")
    }

    @Test
    fun deletePlant_removesRow() = runTest {
        val r = repo()
        val id = r.addPlant("A", "", "")
        assertThat(r.count()).isEqualTo(1)
        r.deletePlant(id)
        assertThat(r.count()).isEqualTo(0)
        assertThat(r.getPlant(id)).isNull()
    }

    @Test
    fun observePlant_emitsThenNullAfterDelete() = runTest {
        val r = repo()
        val id = r.addPlant("A", "", "")
        r.observePlant(id).test {
            assertThat(awaitItem()?.name).isEqualTo("A")
            r.deletePlant(id)
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
