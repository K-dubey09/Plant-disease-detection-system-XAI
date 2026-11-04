package com.leafguard.app.ui.dashboard

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.local.Plant
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.domain.repository.FakePlantRepository
import com.leafguard.app.domain.repository.FakeScanRepository
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
class DashboardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }
    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun scan(id: Long, plantId: Long? = null, condition: String = "Early blight", createdAt: Long = 1_000L) =
        ScanEntity(id = id, plantId = plantId, labelName = "Tomato___Early_blight", labelCondition = condition, confidence = 0.92f, createdAt = createdAt)

    @Test
    fun scans_emitInitialList() = runTest {
        val vm = DashboardViewModel(
            scanRepository = FakeScanRepository(listOf(scan(1), scan(2))),
            plantRepository = FakePlantRepository(),
        )
        assertThat(vm.scans.value).hasSize(2)
        assertThat(vm.scans.value[0].labelCondition).isEqualTo("Early blight")
    }

    @Test
    fun plants_emitInitialList() = runTest {
        val vm = DashboardViewModel(
            scanRepository = FakeScanRepository(),
            plantRepository = FakePlantRepository(listOf(Plant(id = 1, name = "Tomato"))),
        )
        assertThat(vm.plants.value).hasSize(1)
        assertThat(vm.plants.value[0].name).isEqualTo("Tomato")
    }

    @Test
    fun empty_whenNoData() = runTest {
        val vm = DashboardViewModel(
            scanRepository = FakeScanRepository(),
            plantRepository = FakePlantRepository(),
        )
        assertThat(vm.scans.value).isEmpty()
        assertThat(vm.plants.value).isEmpty()
    }

    @Test
    fun scans_descByCreatedAt_firstIsMostRecent() = runTest {
        // FakeScanRepository sorts DESC by createdAt (mirroring the DAO); the dashboard reads
        // scans.firstOrNull() as the most-recent diagnosis.
        val vm = DashboardViewModel(
            scanRepository = FakeScanRepository(listOf(
                scan(1, condition = "Old", createdAt = 1_000L),
                scan(2, condition = "New", createdAt = 9_000L),
            )),
            plantRepository = FakePlantRepository(),
        )
        assertThat(vm.scans.value.first().labelCondition).isEqualTo("New")
    }
}
