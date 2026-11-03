package com.leafguard.app.ui.history

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
class HistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }
    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun scan(id: Long, plantId: Long? = null, condition: String = "Early blight", createdAt: Long = 1_000L) =
        ScanEntity(id = id, plantId = plantId, labelName = "Tomato___Early_blight", labelCondition = condition, confidence = 0.9f, createdAt = createdAt)

    @Test
    fun items_resolvePlantNameForAttachedScan() = runTest {
        val vm = HistoryViewModel(
            scanRepository = FakeScanRepository(listOf(scan(1, plantId = 7L))),
            plantRepository = FakePlantRepository(listOf(Plant(id = 7, name = "Balcony Tomato"))),
        )
        assertThat(vm.items.value).hasSize(1)
        assertThat(vm.items.value[0].plantName).isEqualTo("Balcony Tomato")
    }

    @Test
    fun items_unattachedScan_hasNullPlantName() = runTest {
        val vm = HistoryViewModel(
            scanRepository = FakeScanRepository(listOf(scan(1, plantId = null))),
            plantRepository = FakePlantRepository(),
        )
        assertThat(vm.items.value[0].plantName).isNull()
    }

    @Test
    fun items_deletedPlant_hasNullPlantName() = runTest {
        // Scan references a plant id that no longer exists -> plantName null (ON DELETE SET NULL
        // in Room mirrors this: the scan survives with plantId intact but the plant is gone).
        val vm = HistoryViewModel(
            scanRepository = FakeScanRepository(listOf(scan(1, plantId = 99L))),
            plantRepository = FakePlantRepository(listOf(Plant(id = 1, name = "Other"))),
        )
        assertThat(vm.items.value[0].plantName).isNull()
    }

    @Test
    fun setFilter_filtersByPlant() = runTest {
        val vm = HistoryViewModel(
            scanRepository = FakeScanRepository(listOf(
                scan(1, plantId = 1L, createdAt = 3_000L),
                scan(2, plantId = 2L, createdAt = 2_000L),
                scan(3, plantId = 1L, createdAt = 1_000L),
            )),
            plantRepository = FakePlantRepository(listOf(Plant(id = 1, name = "A"), Plant(id = 2, name = "B"))),
        )
        assertThat(vm.items.value).hasSize(3)
        vm.setFilter(1L)
        assertThat(vm.items.value).hasSize(2)
        assertThat(vm.items.value.all { it.scan.plantId == 1L }).isTrue()
        assertThat(vm.selectedPlantId.value).isEqualTo(1L)
    }

    @Test
    fun setFilter_null_showsAll() = runTest {
        val vm = HistoryViewModel(
            scanRepository = FakeScanRepository(listOf(scan(1, plantId = 1L), scan(2, plantId = 2L))),
            plantRepository = FakePlantRepository(listOf(Plant(id = 1, name = "A"))),
        )
        vm.setFilter(1L)
        assertThat(vm.items.value).hasSize(1)
        vm.setFilter(null)
        assertThat(vm.items.value).hasSize(2)
        assertThat(vm.selectedPlantId.value).isNull()
    }

    @Test
    fun items_descByCreatedAt_mostRecentFirst() = runTest {
        val vm = HistoryViewModel(
            scanRepository = FakeScanRepository(listOf(
                scan(1, createdAt = 1_000L, condition = "Old"),
                scan(2, createdAt = 9_000L, condition = "New"),
            )),
            plantRepository = FakePlantRepository(),
        )
        assertThat(vm.items.value.first().scan.labelCondition).isEqualTo("New")
    }

    @Test
    fun plants_emitInitialList() = runTest {
        val vm = HistoryViewModel(
            scanRepository = FakeScanRepository(),
            plantRepository = FakePlantRepository(listOf(Plant(id = 1, name = "Tomato"))),
        )
        assertThat(vm.plants.value).hasSize(1)
    }
}
