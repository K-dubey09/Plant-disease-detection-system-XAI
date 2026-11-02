package com.leafguard.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * LeafGuard application entry point.
 *
 * Annotated with [HiltAndroidApp] so the Hilt dependency graph is generated and the
 * application singleton becomes the graph root. Concrete initialisation (Room, TFLite
 * interpreter warm-up, strict-mode, logging) is added in later phases alongside the
 * repositories and inference engine they depend on.
 *
 * WorkManager's default initializer is disabled in AndroidManifest.xml; it will be
 * explicitly configured when background sync (e.g. periodic suggestion refresh) lands.
 */
@HiltAndroidApp
class LeafGuardApplication : Application()
