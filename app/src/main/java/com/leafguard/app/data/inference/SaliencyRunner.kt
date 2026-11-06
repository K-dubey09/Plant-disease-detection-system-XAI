package com.leafguard.app.data.inference

import android.content.Context
import android.graphics.Bitmap
import com.leafguard.app.di.ApplicationScope
import com.leafguard.app.di.IoDispatcher
import com.leafguard.app.domain.repository.ScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the occlusion-sensitivity heatmap on an APPLICATION-scoped [CoroutineScope] ([ApplicationScope])
 * rather than the [ScanViewModel][com.leafguard.app.ui.scan.ScanViewModel]'s `viewModelScope`, so the
 * ~6-32 s, 64-pass computation survives the scan screen being popped off the back stack.
 *
 * Why this matters: Phase 9 launched the heatmap as a sibling on `viewModelScope`. That scope is
 * cancelled the moment the [ScanViewModel] is cleared — which happens not only on process death but
 * whenever the user navigates scan -> result -> dashboard, popping the scan entry. A heatmap
 * cancelled mid-flight left the persisted [ScanEntity.saliencyImagePath] null forever (the coroutine
 * that calls [ScanRepository.setSaliencyImage] never ran to completion), so the Images gallery and
 * future history renders permanently showed that scan without its explanation even though the work
 * was 90% done. Moving the work to an application-scoped [SupervisorJob][kotlinx.coroutines.SupervisorJob]
 * scope decouples the heatmap's lifetime from the scan screen's: the [ScanViewModel] publishes the
 * diagnosis + `Done` immediately (fast path), hands the bitmap to [generateFor], and is then free to
 * be destroyed; the heatmap finishes on the long-lived scope and back-fills Room + the
 * [ScanResultHolder] whenever it completes.
 *
 * Failures stay best-effort: a heatmap exception is swallowed here (Done was already emitted by the
 * scan flow, so the scan is never stranded), and a stale-heatmap guard in [ScanResultHolder] ensures
 * a late-arriving heatmap from a superseded scan does not clobber the current result.
 */
@Singleton
class SaliencyRunner @Inject constructor(
    private val saliencyGenerator: SaliencyGenerator,
    private val scanRepository: ScanRepository,
    private val holder: ScanResultHolder,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Kicks off the heatmap for [scanId] on the application scope and returns immediately. The
     * bitmap is captured by the launched coroutine, so the caller may release its own reference
     * (e.g. by clearing the ViewModel) without cancelling the work.
     */
    fun generateFor(scanId: Long, bitmap: Bitmap) {
        appScope.launch {
            try {
                val saliency = withContext(io) { saliencyGenerator.generate(bitmap) }
                val path = saveBitmap(saliency, "saliency", Bitmap.CompressFormat.PNG)
                if (path != null) {
                    scanRepository.setSaliencyImage(scanId, path)
                }
                holder.updateSaliency(scanId, saliency)
            } catch (e: Exception) {
                // Best-effort explainability: a heatmap failure must never break the scan. Done was
                // already emitted by the scan flow; the result screen stays without a heatmap and
                // the row keeps a null saliencyImagePath, which the Images gallery handles gracefully.
            }
        }
    }

    /**
     * Persists a [Bitmap] to internal storage (filesDir/scans/) so the Phase 6 Images gallery can
     * render it later without the in-memory copy. Returns the absolute path, or null on failure so
     * a write error does not fail the whole heatmap step.
     */
    private fun saveBitmap(bitmap: Bitmap, prefix: String, format: Bitmap.CompressFormat): String? =
        try {
            val dir = File(context.filesDir, "scans").apply { mkdirs() }
            val ext = if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png"
            val file = File(dir, "scan_${System.currentTimeMillis()}_$prefix.$ext")
            FileOutputStream(file).use { out -> bitmap.compress(format, 90, out) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
}
