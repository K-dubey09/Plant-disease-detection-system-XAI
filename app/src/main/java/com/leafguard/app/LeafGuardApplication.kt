package com.leafguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * LeafGuard application entry point.
 *
 * Annotated with [HiltAndroidApp] so the Hilt dependency graph is generated and the application
 * singleton becomes the graph root. The only startup work today is registering the export
 * notification channel (v2.7.0.0): CSV/PDF exports write to a user-picked location via the Storage
 * Access Framework and post a completion notification, which on Android 8.0+ requires a channel
 * created before the first `notify()`.
 */
@HiltAndroidApp
class LeafGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createExportNotificationChannel()
    }

    private fun createExportNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EXPORT_CHANNEL_ID,
                getString(R.string.channel_exports_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.channel_exports_desc)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXPORT_CHANNEL_ID = "exports"
    }
}
