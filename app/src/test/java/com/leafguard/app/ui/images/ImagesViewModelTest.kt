package com.leafguard.app.ui.images

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
class ImagesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }
    @After
    fun tearDown() { Dispatchers.resetMain() }

    // Default sourcePath non-null so scans are image-bearing; pass null to simulate a pre-Phase-6 scan.
    private fun scan(
        id: Long,
        plantId: Long? = null,
        condition: String = "Early blight",
        createdAt: Long = 1_000L,
        sourcePath: String? = "/scans/source_$id.jpg",
    ) = ScanEntity(
        id = id, plantId = plantId, labelName = "Tomato___Early_blight",
        labelCondition = condition, confidence = 0.9f, createdAt = createdAt,
        sourceImagePath = sourcePath, saliencyImagePath = null,
    )

    @Test
    fun groups_empty_whenNoScans() = runTest {
        val vm = ImagesViewModel(FakeScanRepository(), FakePlantRepository())
        assertThat(vm.groups.value).isEmpty()
    }

    @Test
    fun groups_excludeScansWithoutStoredImage() = runTest {
        // A pre-Phase-6 scan (sourceImagePath = null) is not shown in the gallery.
        val vm = ImagesViewModel(
            FakeScanRepository(listOf(
                scan(1, sourcePath = null),
                scan(2, sourcePath = "/scans/source_2.jpg"),
            )),
            FakePlantRepository(),
        )
        assertThat(vm.groups.value).hasSize(1) // only the Unattached group from scan 2
        assertThat(vm.groups.value[0].scans).hasSize(1)
        assertThat(vm.groups.value[0].scans[0].id).isEqualTo(2L)
    }

    @Test
    fun groups_groupedByPlant_withResolvedNames() = runTest {
        val vm = ImagesViewModel(
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
        val vm = ImagesViewModel(
            FakeScanRepository(listOf(scan(1, plantId = null), scan(2, plantId = 1L))),
            FakePlantRepository(listOf(Plant(id = 1, name = "Tomato"))),
        )
        assertThat(vm.groups.value).hasSize(2)
        val unattached = vm.groups.value.first { it.plantId == null }
        assertThat(unattached.plantName).isNull()
    }

    @Test
    fun groups_deletedPlant_resolvedAsUnattached() = runTest {
        val vm = ImagesViewModel(
            FakeScanRepository(listOf(scan(1, plantId = 99L))),
            FakePlantRepository(listOf(Plant(id = 1, name = "Other"))),
        )
        assertThat(vm.groups.value).hasSize(1)
        assertThat(vm.groups.value[0].plantName).isNull()
    }

    @Test
    fun groups_sortedByMostRecentScan_newestPlantFirst() = runTest {
        val vm = ImagesViewModel(
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
    fun groups_countPerGroup() = runTest {
        val vm = ImagesViewModel(
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
}
