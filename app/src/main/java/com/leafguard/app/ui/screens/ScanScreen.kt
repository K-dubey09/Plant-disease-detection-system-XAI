package com.leafguard.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.scan.ScanViewModel
import java.io.File

/**
 * Phase 5 scan screen. Three input paths feed the same [ScanViewModel.analyze] pipeline
 * (classify -> occlusion saliency -> suggest -> persist ScanEntity -> navigate to result/{scanId}):
 *
 *  1. Live CameraX capture: a [PreviewView] bound to a back-camera [Preview] + [ImageCapture];
 *     the Capture button saves a frame to the cache dir, decodes it, and stages it for analysis.
 *  2. Gallery import (fallback for devices/emulators without a usable camera, or for re-scanning
 *     an existing photo).
 *
 * [plantId] is non-null when launched from PlantDetail ("Scan this plant"); the persisted scan
 * is then linked to that plant for per-plant history. The CAMERA permission is requested at
 * runtime (minSdk 26 uses runtime permissions; the manifest declares it with
 * `uses-feature camera.any required="false"` so the app still installs on camera-less devices).
 */
@Composable
fun ScanScreen(
    plantId: Long?,
    onResult: (Long) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        bitmap = uri?.let { decodeBitmap(context, it) }
        if (bitmap != null) viewModel.reset()
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    // Navigate to result/{scanId} once the pipeline reports Done.
    LaunchedEffect(ui) {
        val done = ui as? ScanViewModel.UiState.Done
        if (done != null) onResult(done.scanId)
    }

    LeafScaffold(title = stringResource(R.string.scan_title)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DemoModelBanner()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                when {
                    bmp != null -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.scan_captured_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.sizeIn(maxHeight = 320.dp).fillMaxWidth(),
                    )
                    hasCameraPermission -> CameraPreview(
                        imageCapture = imageCapture,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> EmptyPickerState()
                }
            }

            val analyzing = ui as? ScanViewModel.UiState.Analyzing
            when {
                analyzing != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(analyzing.stage, style = MaterialTheme.typography.bodyMedium)
                }

                ui is ScanViewModel.UiState.Error -> Text(
                    text = (ui as ScanViewModel.UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val staged = bitmap
                    if (staged != null) {
                        // Retake / re-import a fresh photo.
                        OutlinedButton(
                            onClick = { bitmap = null; viewModel.reset() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Text(stringResource(R.string.scan_retake), modifier = Modifier.padding(start = 8.dp))
                        }
                        Button(
                            onClick = { viewModel.analyze(staged, plantId) },
                            enabled = ui !is ScanViewModel.UiState.Analyzing,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null)
                            Text(stringResource(R.string.scan_analyze), modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        if (hasCameraPermission) {
                            Button(
                                onClick = {
                                    capturePhoto(context, imageCapture) { captured ->
                                        bitmap = captured
                                        viewModel.reset()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                                Text(stringResource(R.string.scan_capture), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                picker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Text(stringResource(R.string.scan_import), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A CameraX live preview bound to the back camera. [Preview] feeds the [PreviewView] surface and
 * [imageCapture] is bound alongside so the Capture button's [ImageCapture.takePicture] targets the
 * same camera. Binding happens once (factory) and re-binds on lifecycle recreation; unbindAll
 * before bind avoids "CameraX: Use cases are already bound" on re-entry.
 */
@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val preview = Preview.Builder().build().also { p ->
                p.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                } catch (_: Exception) {
                    // Swallow binding failure; the UI falls back to the Import-photo path.
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/**
 * Captures a single frame to a cache file via [ImageCapture], decodes it to a [Bitmap], and
 * invokes [onCaptured] on the main thread. On failure the UI simply stays on the live preview.
 */
private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onCaptured: (Bitmap) -> Unit,
) {
    val file = File(context.cacheDir, "leaf_capture_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                // The cache file was only a scratch carrier for the decode; the bitmap is staged
                // in memory for analysis, so delete it now instead of leaking one .jpg per capture.
                file.delete()
                if (bmp != null) onCaptured(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                // Swallow: the UI stays on the live preview so the user can retry.
            }
        },
    )
}

@Composable
private fun DemoModelBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Text(
            text = stringResource(R.string.scan_demo_banner),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun EmptyPickerState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.scan_picker_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
