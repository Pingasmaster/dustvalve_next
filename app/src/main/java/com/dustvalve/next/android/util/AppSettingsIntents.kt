package com.dustvalve.next.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Deep-links to this app's system details page (Permissions / Open by default /
 * etc.). Used when a runtime permission is permanently denied and
 * [androidx.activity.result.contract.ActivityResultContracts.RequestPermission]
 * can no longer surface a dialog.
 */
fun openAppDetailsSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Deep-links to the per-app notification settings screen, falling back to
 * [openAppDetailsSettings] when that action is unavailable on the OEM.
 */
fun openAppNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        openAppDetailsSettings(context)
    }
}
