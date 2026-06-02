package com.dustvalve.next.android.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Routes the download notification's Pause/Resume and Cancel action buttons to
 * [DownloadController]. Non-exported; only the notification's own
 * [android.app.PendingIntent]s ever fire it.
 *
 * Uses [EntryPointAccessors] (the same pattern as the app's Coil entry point)
 * rather than `@AndroidEntryPoint` so dependency resolution is explicit and
 * doesn't depend on receiver bytecode transforms. The controller methods just
 * flip a flag / cancel a Job, so `onReceive` stays trivial and non-blocking.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadActionEntryPoint {
        fun downloadController(): DownloadController
    }

    override fun onReceive(context: Context, intent: Intent) {
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DownloadActionEntryPoint::class.java,
        ).downloadController()
        when (intent.action) {
            ACTION_PAUSE_RESUME ->
                if (controller.isPaused.value) controller.resume() else controller.pause()

            ACTION_CANCEL -> controller.cancelAll()
        }
    }

    companion object {
        const val ACTION_PAUSE_RESUME = "com.dustvalve.next.android.download.action.PAUSE_RESUME"
        const val ACTION_CANCEL = "com.dustvalve.next.android.download.action.CANCEL"

        // Distinct request codes so FLAG_UPDATE_CURRENT does not collapse the
        // two PendingIntents into one (they differ only by action string).
        const val RC_PAUSE_RESUME = 4243
        const val RC_CANCEL = 4244
    }
}
