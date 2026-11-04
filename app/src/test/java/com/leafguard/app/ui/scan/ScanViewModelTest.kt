package com.leafguard.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.inference.DiseaseClassifier
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.inference.ScanResultHolder
import com.leafguard.app.data.inference.SaliencyGenerator
import com.leafguard.app.data.inference.SuggestionEngine
import com.leafguard.app.data.local.FakeScanDao
import com.leafguard.app.data.repository.ScanRepositoryImpl
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.DiseaseLabel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 9: proves the scan-flow fix. The headline regression was that [ScanViewModel.analyze] ran
 * classify -> saliency -> save serially and only emitted [ScanViewModel.UiState.Done] after the
 * slow 64-pass [SaliencyGenerator] finished, leaving the scan screen pinned on "Generating
 * explanation heatmap…" for seconds. These tests assert the new behaviour:
 *
 *  - Done is emitted + the scan is persisted even when saliency throws (the failure is contained in
 *    a background coroutine and never strands the UI — the exact bug being fixed).
 *  - When saliency succeeds, the heatmap streams onto [ScanResultHolder] (so the result screen,
 *    already showing the diagnosis, swaps the placeholder for the heatmap) and its path is
 *    back-filled in Room.
 *  - plantId threads through to the persisted row (per-plant growth tracking).
 *  - A stale heatmap from a superseded scan does not clobber the current scan's holder.
 *
 * Runs under Robolectric (real Context + Bitmap on the JVM); [DiseaseClassifier] /
 * [SaliencyGenerator] are mocked with MockK since they wrap a TFLite Interpreter we cannot
 * construct in a unit test. SDK 34 matches the migration test (Robolectric 4.13).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var classifier: DiseaseClassifier
    private lateinit var saliency: SaliencyGenerator
    private lateinit var holder: ScanResultHolder
    private lateinit var repo: ScanRepositoryImpl

    private val leaf = DiseaseLabel(0, "Tomato___Late_blight", "Tomato", "Late blight")
    private val diagnosis = Diagnosis(leaf, 0.91f, listOf(leaf to 0.91f))
    private val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        classifier = mockk()
        every { classifier.classify(any()) } returns diagnosis
        saliency = mockk()
        holder = ScanResultHolder()
        repo = ScanRepositoryImpl(FakeScanDao(), dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = ScanViewModel(
        classifier,
        saliency,
        SuggestionEngine(emptyMap()),
        holder,
        repo,
        context,
        dispatcher,
    )

    @Test
    fun analyze_emitsDoneAndPersistsScanEvenWhenSaliencyThrows() = runTest(dispatcher) {
        // The old serial pipeline would have stranded the UI here: saliency threw, Done never fired,
        // and the scan screen stayed on the Analyzing spinner forever. The fast path must still win.
        every { saliency.generate(any()) } throws RuntimeException("saliency OOM")
        val viewModel = vm()

        viewModel.analyze(bitmap)
        advanceUntilIdle()

        val state = viewModel.ui.value
        assertThat(state).isInstanceOf(ScanViewModel.UiState.Done::class.java)
        val scanId = (state as ScanViewModel.UiState.Done).scanId
        assertThat(scanId).isGreaterThan(0L)

        // History updates at the same instant Done fires: the row is persisted with the diagnosis
        // and a null heatmap path (the failure left no heatmap, but the scan is intact).
        val persisted = repo.getScan(scanId)
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.labelCondition).isEqualTo("Late blight")
        assertThat(persisted.confidence).isWithin(0.001f).of(0.91f)
        assertThat(persisted.saliencyImagePath).isNull()

        // The result is published immediately with a null saliency (placeholder shown on screen).
        val result = holder.state.value
        assertThat(result).isNotNull()
        assertThat(result!!.scanId).isEqualTo(scanId)
        assertThat(result.saliency).isNull()
    }

    @Test
    fun analyze_streamsSaliencyOntoHolderWhenItSucceeds() = runTest(dispatcher) {
        val heat = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        every { saliency.generate(any()) } returns heat
        val viewModel = vm()

        viewModel.analyze(bitmap)
        advanceUntilIdle()

        val state = viewModel.ui.value as ScanViewModel.UiState.Done
        // The heatmap streamed onto the result the background coroutine produced.
        val result = holder.state.value
        assertThat(result).isNotNull()
        assertThat(result!!.scanId).isEqualTo(state.scanId)
        assertThat(result.saliency).isSameInstanceAs(heat)
        // And its path was back-filled in Room for the Images gallery / future history renders.
        assertThat(repo.getScan(state.scanId)?.saliencyImagePath).isNotNull()
    }

    @Test
    fun analyze_passesPlantIdThroughToPersistedScan() = runTest(dispatcher) {
        every { saliency.generate(any()) } returns Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val viewModel = vm()

        viewModel.analyze(bitmap, plantId = 7L)
        advanceUntilIdle()

        val scanId = (viewModel.ui.value as ScanViewModel.UiState.Done).scanId
        assertThat(repo.getScan(scanId)?.plantId).isEqualTo(7L)
    }

    @Test
    fun holder_updateSaliency_ignoresStaleHeatmapFromSupersededScan() = runTest(dispatcher) {
        // A second scan overwrites the holder while the first scan's heatmap is still computing.
        // When the first scan's heatmap finally lands, it must NOT clobber the second scan's result.
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val heat = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        holder.set(ScanResult(scanId = 2, diagnosis = diagnosis, suggestion = null, source = source))

        holder.updateSaliency(scanId = 1, heat) // stale — scan #1 is no longer on screen
        assertThat(holder.state.value?.saliency).isNull()

        holder.updateSaliency(scanId = 2, heat) // current scan — applies
        assertThat(holder.state.value?.saliency).isSameInstanceAs(heat)
    }
}
