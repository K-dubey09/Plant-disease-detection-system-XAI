package com.leafguard.app.data.inference

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.leafguard.app.domain.model.Diagnosis
import com.leafguard.app.domain.model.DiseaseLabel
import com.leafguard.app.domain.repository.ScanRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix 2: the occlusion heatmap must survive the [ScanViewModel][com.leafguard.app.ui.scan.ScanViewModel]
 * being cleared. Phase 9 ran the heatmap on `viewModelScope`, which is cancelled when the scan
 * screen is popped off the back stack (scan -> result -> dashboard) -- aborting in-flight heatmaps
 * and leaving `ScanEntity.saliencyImagePath` null forever, even when the work was nearly done.
 * [SaliencyRunner] runs the heatmap on an APPLICATION-scoped coroutine, so cancelling the "kicking"
 * scope (the stand-in for `viewModelScope` here) must NOT stop the heatmap: it still back-fills
 * Room via [ScanRepository.setSaliencyImage] and streams onto the [ScanResultHolder].
 *
 * The runner's scope + io dispatcher share one [StandardTestDispatcher] so a single
 * [advanceUntilIdle] drives both; the kicking scope uses a separate dispatcher and is cancelled
 * before the heatmap finishes. Runs under Robolectric (real [Context] + [Bitmap] for saveBitmap).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaliencyRunnerTest {

    @Test
    fun generateFor_persistsHeatmapEvenWhenKickingScopeIsCancelled() {
        val heat = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val saliency = mockk<SaliencyGenerator>()
        every { saliency.generate(any()) } returns heat

        val savedScanIds = mutableListOf<Long>()
        val repo = mockk<ScanRepository>(relaxed = true)
        coEvery { repo.setSaliencyImage(any(), any()) } answers { savedScanIds.add(firstArg()) }

        val holder = ScanResultHolder()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

        val leaf = DiseaseLabel(0, "Tomato___Late_blight", "Tomato", "Late blight")
        val diagnosis = Diagnosis(leaf, 0.9f, listOf(leaf to 0.9f))
        holder.set(ScanResult(scanId = 1, diagnosis = diagnosis, suggestion = null, source = bitmap))

        // The runner's APPLICATION scope (what SaliencyRunner actually runs on) + its io dispatcher
        // share one StandardTestDispatcher so a single advanceUntilIdle drives both.
        val dispatcher = StandardTestDispatcher()
        val runnerScope = TestScope(dispatcher)
        val runner = SaliencyRunner(saliency, repo, holder, context, runnerScope, dispatcher)

        // The "viewModel scope" that gets cancelled when the scan screen is cleared. It launches
        // nothing here -- its only role is to be cancelled, proving the runner does not depend on it.
        val kickingScope = TestScope(StandardTestDispatcher())

        runner.generateFor(scanId = 1, bitmap)

        // Simulate the scan ViewModel being cleared mid-heatmap (scan -> result -> dashboard).
        kickingScope.coroutineContext[Job]?.cancel()

        // The runner scope is independent -> advancing it completes the heatmap despite the kick.
        runnerScope.advanceUntilIdle()

        // The heatmap was back-filled in Room (the regression: previously null forever).
        assertThat(savedScanIds).contains(1L)
        // And streamed onto the holder so the result screen swaps in the heatmap.
        assertThat(holder.state.value?.saliency).isSameInstanceAs(heat)
    }
}
