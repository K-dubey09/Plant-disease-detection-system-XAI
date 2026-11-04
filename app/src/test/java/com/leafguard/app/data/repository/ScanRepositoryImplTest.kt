package com.leafguard.app.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.local.FakeScanDao
import com.leafguard.app.data.local.ScanEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanRepositoryImplTest {

    private fun repo(dao: FakeScanDao = FakeScanDao()) =
        ScanRepositoryImpl(dao, UnconfinedTestDispatcher())

    private fun scan(plantId: Long? = null, label: String = "Apple___Apple_scab") =
        ScanEntity(plantId = plantId, labelName = label, labelCondition = "Apple scab", confidence = 0.97f)

    @Test
    fun observeScans_startsEmpty() = runTest {
        assertThat(repo().observeScans().first()).isEmpty()
    }

    @Test
    fun saveScan_persistsAndEmits() = runTest {
        val r = repo()
        val id = r.saveScan(scan(label = "Tomato___healthy"))
        assertThat(id).isGreaterThan(0L)
        val list = r.observeScans().first()
        assertThat(list).hasSize(1)
        assertThat(list[0].labelName).isEqualTo("Tomato___healthy")
        assertThat(list[0].confidence).isWithin(0.001f).of(0.97f)
    }

    @Test
    fun saveScan_withPlantId_linksToPlant() = runTest {
        val r = repo()
        val id = r.saveScan(scan(plantId = 7L))
        assertThat(r.getScan(id)?.plantId).isEqualTo(7L)
    }

    @Test
    fun saveScan_withoutPlantId_isUnattached() = runTest {
        val r = repo()
        val id = r.saveScan(scan(plantId = null))
        assertThat(r.getScan(id)?.plantId).isNull()
    }

    @Test
    fun observeScansForPlant_filtersByPlant() = runTest {
        val r = repo()
        r.saveScan(scan(plantId = 1L))
        r.saveScan(scan(plantId = 2L))
        r.saveScan(scan(plantId = 1L))
        r.saveScan(scan(plantId = null))
        assertThat(r.observeScansForPlant(1L).first()).hasSize(2)
        assertThat(r.observeScansForPlant(2L).first()).hasSize(1)
    }

    @Test
    fun getScan_returnsById() = runTest {
        val r = repo()
        val id = r.saveScan(scan(label = "Grape___Black_rot"))
        val fetched = r.getScan(id)
        assertThat(fetched?.labelName).isEqualTo("Grape___Black_rot")
        assertThat(r.getScan(999L)).isNull()
    }

    @Test
    fun deleteScan_removesRow() = runTest {
        val r = repo()
        val id = r.saveScan(scan())
        assertThat(r.count()).isEqualTo(1)
        r.deleteScan(id)
        assertThat(r.count()).isEqualTo(0)
        assertThat(r.getScan(id)).isNull()
    }

    @Test
    fun observeScan_emitsThenNullAfterDelete() = runTest {
        val r = repo()
        val id = r.saveScan(scan())
        r.observeScan(id).test {
            assertThat(awaitItem()?.labelCondition).isEqualTo("Apple scab")
            r.deleteScan(id)
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun attachScanToPlant_linksUnattachedScanAndEmits() = runTest {
        val r = repo()
        val id = r.saveScan(scan(plantId = null))
        assertThat(r.getScan(id)?.plantId).isNull()

        r.attachScanToPlant(id, 42L)

        assertThat(r.getScan(id)?.plantId).isEqualTo(42L)
        r.observeScan(id).test {
            assertThat(awaitItem()?.plantId).isEqualTo(42L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun attachScanToPlant_canRelinkToDifferentPlantForGrowthTracking() = runTest {
        val r = repo()
        val id = r.saveScan(scan(plantId = 1L))
        r.attachScanToPlant(id, 2L)
        assertThat(r.getScan(id)?.plantId).isEqualTo(2L)
        // The scan now appears under the new plant's per-plant history, not the old one's.
        assertThat(r.observeScansForPlant(1L).first()).isEmpty()
        assertThat(r.observeScansForPlant(2L).first()).hasSize(1)
    }

    @Test
    fun attachScanToPlant_nullPlantId_unlinksScan() = runTest {
        val r = repo()
        val id = r.saveScan(scan(plantId = 5L))
        r.attachScanToPlant(id, null)
        assertThat(r.getScan(id)?.plantId).isNull()
    }

    @Test
    fun setSaliencyImage_backfillsPathAndEmits() = runTest {
        val r = repo()
        val id = r.saveScan(scan())
        assertThat(r.getScan(id)?.saliencyImagePath).isNull()

        r.setSaliencyImage(id, "/scans/sal.png")

        assertThat(r.getScan(id)?.saliencyImagePath).isEqualTo("/scans/sal.png")
        r.observeScan(id).test {
            assertThat(awaitItem()?.saliencyImagePath).isEqualTo("/scans/sal.png")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
