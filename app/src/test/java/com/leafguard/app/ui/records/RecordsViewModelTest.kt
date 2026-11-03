package com.leafguard.app.ui.records

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
class RecordsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }
    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun scan(id: Long, plantId: Long? = null, condition: String = "Early blight", createdAt: Long = 1_000L) =
        ScanEntity(id = id, plantId = plantId, labelName = "Tomato___Early_blight", labelCondition = condition, confidence = 0.9f, createdAt = createdAt)

    @Test
    fun groups_empty_whenNoScans() = runTest {
        val vm = RecordsViewModel(FakeScanRepository(), FakePlantRepository())
        assertThat(vm.groups.value).isEmpty()
    }

    @Test
    fun groups_groupedByPlant_withResolvedNames() = runTest {
        val vm = RecordsViewModel(
            FakeScanRepository(listOf(scan(1, plantId = 1L), scan(2, plantId = 2L))),
            FakePlantRepository(listOf(Plant(id = 1, name = "Tomato"), Plant(id = 2, name = "Basil"))),
        )
        assertThat(vm.groups.value).hasSize(2)
        val byId = vm.groups.value.associateBy { it.plantId }
        assertThat(byId[1L]?.plantName).isEqualTo("Tomato")
        assertThat(byId[2L]?.plantName).isEqualTo("Basil")
    }

    @Test
    fun groups_unattachedScans_inSeparateGroup_withNullName() = runTest {
        val vm = RecordsViewModel(
            FakeScanRepository(listOf(scan(1, plantId = null), scan(2, plantId = 1L))),
            FakePlantRepository(listOf(Plant(id = 1, name = "Tomato"))),
        )
        assertThat(vm.groups.value).hasSize(2)
        val unattached = vm.groups.value.first { it.plantId == null }
        assertThat(unattached.plantName).isNull()
        assertThat(unattached.count).isEqualTo(1)
    }

    @Test
    fun groups_deletedPlant_resolvedAsUnattached() = runTest {
        // Scan references a plant id that no longer exists -> plantName null (rendered "Unattached"),
        // mirroring Room's ON DELETE SET NULL surviving the diagnosis history.
        val vm = RecordsViewModel(
            FakeScanRepository(listOf(scan(1, plantId = 99L))),
            FakePlantRepository(listOf(Plant(id = 1, name = "Other"))),
        )
        assertThat(vm.groups.value).hasSize(1)
        assertThat(vm.groups.value[0].plantName).isNull()
    }

    @Test
    fun groups_sortedByMostRecentScan_newestPlantFirst() = runTest {
        // Plant A's most-recent scan (9_000) is newer than Plant B's (5_000) -> A leads.
        val vm = RecordsViewModel(
            FakeScanRepository(listOf(
                scan(1, plantId = 1L, createdAt = 9_000L),
                scan(2, plantId = 2L, createdAt = 5_000L),
            )),
            FakePlantRepository(listOf(Plant(id = 1, name = "A"), Plant(id = 2, name = "B"))),
        )
        assertThat(vm.groups.value[0].plantName).isEqualTo("A")
        assertThat(vm.groups.value[1].plantName).isEqualTo("B")
    }

    @Test
    fun groups_scansDescWithinGroup() = runTest {
        val vm = RecordsViewModel(
            FakeScanRepository(listOf(
                scan(1, plantId = 1L, createdAt = 1_000L, condition = "Old"),
                scan(2, plantId = 1L, createdAt = 9_000L, condition = "New"),
            )),
            FakePlantRepository(listOf(Plant(id = 1, name = "Tomato"))),
        )
        val group = vm.groups.value[0]
        assertThat(group.scans.first().labelCondition).isEqualTo("New")
        assertThat(group.scans.last().labelCondition).isEqualTo("Old")
    }

    @Test
    fun groups_countPerGroup() = runTest {
        val vm = RecordsViewModel(
            FakeScanRepository(listOf(
                scan(1, plantId = 1L),
                scan(2, plantId = 1L),
                scan(3, plantId = 2L),
            )),
            FakePlantRepository(listOf(Plant(id = 1, name = "A"), Plant(id = 2, name = "B"))),
        )
        val byId = vm.groups.value.associateBy { it.plantId }
        assertThat(byId[1L]?.count).isEqualTo(2)
        assertThat(byId[2L]?.count).isEqualTo(1)
    }

    @Test
    fun groups_mostRecentAtSetToFirstScan() = runTest {
        val vm = RecordsViewModel(
            FakeScanRepository(listOf(scan(1, plantId = 1L, createdAt = 7_000L))),
            FakePlantRepository(listOf(Plant(id = 1, name = "A"))),
        )
        assertThat(vm.groups.value[0].mostRecentAt).isEqualTo(7_000L)
    }
}
