package com.leafguard.app.ui.scan

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.inference.ModelIntegrity
import com.leafguard.app.data.inference.ScanResultHolder
import com.leafguard.app.data.local.Plant
import com.leafguard.app.data.local.ScanEntity
import com.leafguard.app.domain.repository.FakePlantRepository
import com.leafguard.app.domain.repository.FakeScanRepository
import com.leafguard.app.ui.navigation.Routes
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Phase 9 growth tracking: the "Add to plant" action on the result screen. [ResultViewModel] links
 * a scan to an existing plant or to a freshly created one; Room's observe Flow then re-emits so the
 * UI reactively shows "Saved to <plant>" and the scan joins that plant's per-plant Records timeline.
 *
 * Fix 3: [ResultViewModel] now also takes a [ModelIntegrity] so it can surface a "model integrity
 * warning" banner when the bundled .tflite's SHA-256 no longer matches the build-time digest. These
 * tests pass a 9-byte buffer (which cannot match the real digest) purely to satisfy the constructor
 * — the integrity flag itself is exercised by [com.leafguard.app.data.inference.ModelIntegrityTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun scan(id: Long, plantId: Long? = null) =
        ScanEntity(id = id, plantId = plantId, labelName = "Tomato___Late_blight", labelCondition = "Late blight", confidence = 0.9f)

    private val tamperedModelIntegrity = ModelIntegrity(ByteBuffer.allocate(9))

    @Test
    fun attachToPlant_linksScanToChosenPlant() = runTest {
        val scanRepo = FakeScanRepository(listOf(scan(id = 5, plantId = null)))
        val vm = ResultViewModel(
            holder = ScanResultHolder(),
            scanRepository = scanRepo,
            plantRepository = FakePlantRepository(listOf(Plant(id = 7, name = "Balcony Tomato"))),
            modelIntegrity = tamperedModelIntegrity,
            savedStateHandle = SavedStateHandle(mapOf(Routes.RESULT_ARG_SCAN_ID to 5L)),
        )

        vm.attachToPlant(7L)
        advanceUntilIdle()

        assertThat(scanRepo.getScan(5)?.plantId).isEqualTo(7L)
    }

    @Test
    fun attachToPlant_null_unlinksScan() = runTest {
        val scanRepo = FakeScanRepository(listOf(scan(id = 5, plantId = 3L)))
        val vm = ResultViewModel(
            holder = ScanResultHolder(),
            scanRepository = scanRepo,
            plantRepository = FakePlantRepository(listOf(Plant(id = 3, name = "X"))),
            modelIntegrity = tamperedModelIntegrity,
            savedStateHandle = SavedStateHandle(mapOf(Routes.RESULT_ARG_SCAN_ID to 5L)),
        )

        vm.attachToPlant(null)
        advanceUntilIdle()

        assertThat(scanRepo.getScan(5)?.plantId).isNull()
    }

    @Test
    fun createPlantAndAttach_createsPlantThenLinksScanToIt() = runTest {
        val scanRepo = FakeScanRepository(listOf(scan(id = 5, plantId = null)))
        val plantRepo = FakePlantRepository()
        val vm = ResultViewModel(
            holder = ScanResultHolder(),
            scanRepository = scanRepo,
            plantRepository = plantRepo,
            modelIntegrity = tamperedModelIntegrity,
            savedStateHandle = SavedStateHandle(mapOf(Routes.RESULT_ARG_SCAN_ID to 5L)),
        )

        vm.createPlantAndAttach("Balcony Tomato", "Roma")
        advanceUntilIdle()

        // A new plant was created with the given name + species...
        assertThat(plantRepo.addCalls).isEqualTo(1)
        assertThat(plantRepo.lastAddArgs?.first).isEqualTo("Balcony Tomato")
        assertThat(plantRepo.lastAddArgs?.second).isEqualTo("Roma")
        // ...and the scan was linked to that new plant's id (the first plant -> id 1).
        assertThat(scanRepo.getScan(5)?.plantId).isEqualTo(1L)
    }

    @Test
    fun attachedPlantId_reflectsScanAndUpdatesWhenReattached() = runTest {
        val scanRepo = FakeScanRepository(listOf(scan(id = 5, plantId = 3L)))
        val vm = ResultViewModel(
            holder = ScanResultHolder(),
            scanRepository = scanRepo,
            plantRepository = FakePlantRepository(listOf(Plant(id = 3, name = "Old"), Plant(id = 9, name = "New"))),
            modelIntegrity = tamperedModelIntegrity,
            savedStateHandle = SavedStateHandle(mapOf(Routes.RESULT_ARG_SCAN_ID to 5L)),
        )

        vm.attachedPlantId.test {
            assertThat(awaitItem()).isEqualTo(3L)
            vm.attachToPlant(9L)
            assertThat(awaitItem()).isEqualTo(9L)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
