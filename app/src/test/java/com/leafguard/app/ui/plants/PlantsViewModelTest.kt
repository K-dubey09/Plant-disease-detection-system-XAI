package com.leafguard.app.ui.plants

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.local.Plant
import com.leafguard.app.domain.repository.FakePlantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlantsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }
    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun plants_emitInitialList() = runTest {
        val repo = FakePlantRepository(listOf(Plant(id = 1, name = "Tomato")))
        val vm = PlantsViewModel(repo)
        assertThat(vm.plants.value).hasSize(1)
        assertThat(vm.plants.value[0].name).isEqualTo("Tomato")
    }

    @Test
    fun addPlant_blank_setsNameBlankError_andDoesNotCallRepo() = runTest {
        val repo = FakePlantRepository()
        val vm = PlantsViewModel(repo)
        vm.addPlant("   ", "", "")
        assertThat(vm.plantError.value).isEqualTo(PlantsViewModel.PlantsError.NAME_BLANK)
        assertThat(repo.addCalls).isEqualTo(0)
    }

    @Test
    fun addPlant_valid_callsRepoAndClearsError() = runTest {
        val repo = FakePlantRepository()
        val vm = PlantsViewModel(repo)
        vm.addPlant("Basil", "Sweet", "Keep moist")
        assertThat(vm.plantError.value).isNull()
        assertThat(repo.addCalls).isEqualTo(1)
        assertThat(repo.lastAddArgs).isEqualTo(Triple("Basil", "Sweet", "Keep moist"))
        assertThat(vm.plants.value).hasSize(1)
    }

    @Test
    fun deletePlant_callsRepo() = runTest {
        val repo = FakePlantRepository(listOf(Plant(id = 7, name = "A")))
        val vm = PlantsViewModel(repo)
        vm.deletePlant(7)
        assertThat(repo.deleteCalls).isEqualTo(1)
        assertThat(repo.lastDeleteId).isEqualTo(7)
    }

    @Test
    fun clearError_clears() = runTest {
        val repo = FakePlantRepository()
        val vm = PlantsViewModel(repo)
        vm.addPlant("   ", "", "")
        assertThat(vm.plantError.value).isEqualTo(PlantsViewModel.PlantsError.NAME_BLANK)
        vm.clearError()
        assertThat(vm.plantError.value).isNull()
    }
}
