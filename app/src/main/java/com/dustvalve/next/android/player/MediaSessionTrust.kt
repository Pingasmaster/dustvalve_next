package com.dustvalve.next.android.player

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Decides whether a MediaSession controller package may receive full transport
 * and custom commands. Own app, packages holding MEDIA_CONTENT_CONTROL, and
 * system/OEM surfaces get full access; everyone else is limited to Media3's
 * DEFAULT_UNTRUSTED_* sets.
 */
object MediaSessionTrust {

    /** Well-known system/OEM media surfaces that may lack MEDIA_CONTENT_CONTROL. */
    val TRUSTED_MEDIA_PACKAGES: Set<String> = setOf(
        "com.android.systemui",
        "com.google.android.systemui",
        "com.android.bluetooth",
        "com.google.android.projection.gearhead",
        "com.google.android.wearable.app",
        "com.google.android.apps.wearable.companion",
        "com.samsung.android.app.routines",
    )

    fun isTrustedController(context: Context, packageName: String?): Boolean {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return false
        if (pkg == context.packageName) return true
        if (pkg in TRUSTED_MEDIA_PACKAGES) return true
        val pm = context.packageManager
        if (pm.checkPermission(Manifest.permission.MEDIA_CONTENT_CONTROL, pkg) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return try {
            val flags = pm.getApplicationInfo(pkg, 0).flags
            flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
