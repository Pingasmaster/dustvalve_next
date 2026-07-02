package com.dustvalve.next.android.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cold-start diagnostics: surfaces [ApplicationExitInfo] for past process
 * deaths that weren't the user closing the app. Available API 30+ on stock
 * Android; minSdk=36 on this app means every device can supply it.
 *
 * Pure logging + on-disk dump. No UI, no telemetry — the diagnostics file
 * lives under filesDir/diagnostics/exit-info-<timestamp>.txt and can be
 * pulled with `adb pull` for offline review.
 *
 * REASON_ANR + REASON_CRASH + REASON_LOW_MEMORY are the three cases the
 * Play Console would otherwise hide behind a generic "process died" entry.
 * Surfacing them here lets field-debug reports carry an actionable stack
 * trace without rebuilding from scratch.
 */
@Singleton
class DiagnosticsCollector @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * Best-effort: read the last few process exit reasons and dump them.
     * Bounded to 5 entries so we don't spam logcat after a long uptime.
     * No-op on pre-API 30.
     */
    @Suppress("TooGenericExceptionCaught") // Robolectric NPE catch — see below.
    fun collectOnColdStart() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val reasons = am.getHistoricalProcessExitReasons(context.packageName, PID_UNQUERIED, MAX_ENTRIES)
            val interesting = reasons.filter { it.reason != ApplicationExitInfo.REASON_EXIT_SELF }
            if (interesting.isEmpty()) return
            interesting.forEach { Log.w(TAG, it.compactLine()) }
            writeDiagnosticsFile(interesting)
        } catch (se: SecurityException) {
            Log.w(TAG, "collectOnColdStart: missing GET_TASKS permission", se)
        } catch (iae: IllegalArgumentException) {
            Log.w(TAG, "collectOnColdStart: invalid package/pid", iae)
        } catch (t: Throwable) {
            // Robolectric throws NPE here too — never crash the app for
            // best-effort diagnostics that the host JVM can't deliver.
            Log.w(TAG, "collectOnColdStart: platform unavailable", t)
        }
    }

    private fun writeDiagnosticsFile(reasons: List<ApplicationExitInfo>) {
        try {
            val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            val file = File(dir, "exit-info-${System.currentTimeMillis()}.txt")
            file.bufferedWriter().use { w ->
                reasons.forEach { w.appendLine(it.compactLine()) }
            }
        } catch (io: IOException) {
            Log.w(TAG, "writeDiagnosticsFile failed", io)
        }
    }

    private fun ApplicationExitInfo.compactLine(): String = buildString {
        append("reason=").append(reasonName(reason))
        append(" importance=").append(importance)
        append(" pss=").append(pss)
        append(" rss=").append(rss)
        append(" timestamp=").append(timestamp)
        if (description != null) append(" description=").append(description)
        append(" process=").append(processName)
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        else -> "OTHER($reason)"
    }

    private companion object {
        const val TAG = "Diagnostics"
        const val PID_UNQUERIED = 0
        const val MAX_ENTRIES = 5
    }
}
