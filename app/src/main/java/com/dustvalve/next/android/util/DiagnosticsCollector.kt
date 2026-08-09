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
 * deaths that weren't the user closing the app. ApplicationExitInfo is API
 * 30+; on the compat flavor (minSdk 26) [collectOnColdStart] early-returns
 * below API 30 so the call stays safe. Future flavor (minSdk 37) always
 * takes the real path via [isAtLeastR].
 *
 * Pure logging + on-disk dump. No UI, no telemetry - the diagnostics file
 * lives under filesDir/diagnostics/exit-info-<timestamp>.txt and can be
 * pulled with `adb pull` for offline review.
 *
 * A timestamp watermark (exit-info-watermark.txt in the same dir) records
 * the newest exit record already processed, so a cold start only dumps
 * records it has never seen before - no file at all when nothing is new.
 * Both filesDir/diagnostics and filesDir/profiles (where
 * [ProfilingCaptureController] re-stamps ANOMALY Perfetto traces) are
 * pruned to a fixed number of newest files on every cold start.
 *
 * REASON_ANR + REASON_CRASH + REASON_LOW_MEMORY are the three cases the
 * Play Console would otherwise hide behind a generic "process died" entry.
 * Surfacing them here lets field-debug reports carry an actionable stack
 * trace without rebuilding from scratch.
 */
@Singleton
class DiagnosticsCollector @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val diagnosticsDir: File get() = File(context.filesDir, "diagnostics")
    private val watermarkFile: File get() = File(diagnosticsDir, WATERMARK_FILE)

    /**
     * Best-effort: read the last few process exit reasons and dump the ones
     * newer than the watermark. Bounded to 5 entries so we don't spam
     * logcat after a long uptime. No-op below API 30.
     *
     * ApplicationExitInfo accessors stay in this method (after [isAtLeastR])
     * so NewApi lint sees the [ChecksSdkIntAtLeast] gate without needing
     * [@androidx.annotation.RequiresApi] helpers that would trip
     * ObsoleteSdkInt on the future flavor.
     *
     * Detekt lists NullPointerException under TooGenericExceptionCaught; the
     * catch is intentional for Robolectric (no ActivityManagerService) and
     * must not crash cold-start diagnostics on a real device.
     */
    @Suppress("TooGenericExceptionCaught")
    fun collectOnColdStart() {
        pruneStaleFiles()
        if (!isAtLeastR()) return
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val reasons = am.getHistoricalProcessExitReasons(context.packageName, PID_UNQUERIED, MAX_ENTRIES)
            val watermark = readWatermark()
            val interesting = reasons.filter {
                it.timestamp > watermark && it.reason != ApplicationExitInfo.REASON_EXIT_SELF
            }
            val newest = reasons.maxOfOrNull { it.timestamp } ?: 0L
            if (newest > watermark) writeWatermark(newest)
            if (interesting.isEmpty()) return
            val lines = interesting.map { info ->
                buildString {
                    append("reason=").append(reasonName(info.reason))
                    append(" importance=").append(info.importance)
                    append(" pss=").append(info.pss)
                    append(" rss=").append(info.rss)
                    append(" timestamp=").append(info.timestamp)
                    if (info.description != null) append(" description=").append(info.description)
                    append(" process=").append(info.processName)
                }
            }
            lines.forEach { Log.w(TAG, it) }
            writeDiagnosticsFile(lines)
        } catch (se: SecurityException) {
            Log.w(TAG, "collectOnColdStart: missing GET_TASKS permission", se)
        } catch (iae: IllegalArgumentException) {
            Log.w(TAG, "collectOnColdStart: invalid package/pid", iae)
        } catch (npe: NullPointerException) {
            // Robolectric throws NPE here - never crash the app for
            // best-effort diagnostics that the host JVM can't deliver.
            Log.w(TAG, "collectOnColdStart: platform unavailable", npe)
        }
    }

    private fun readWatermark(): Long = try {
        watermarkFile.readText().trim().toLong()
    } catch (_: IOException) {
        0L
    } catch (_: NumberFormatException) {
        0L
    }

    private fun writeWatermark(newest: Long) {
        try {
            diagnosticsDir.mkdirs()
            watermarkFile.writeText(newest.toString())
        } catch (io: IOException) {
            Log.w(TAG, "failed to advance exit-info watermark", io)
        }
    }

    private fun writeDiagnosticsFile(lines: List<String>) {
        try {
            val dir = diagnosticsDir.apply { mkdirs() }
            val file = File(dir, "exit-info-${System.currentTimeMillis()}.txt")
            file.bufferedWriter().use { w ->
                lines.forEach { w.appendLine(it) }
            }
        } catch (io: IOException) {
            Log.w(TAG, "writeDiagnosticsFile failed", io)
        }
    }

    /**
     * Keeps the diagnostic output bounded: the newest [MAX_DIAGNOSTIC_FILES]
     * exit-info dumps (the watermark file is exempt) and the newest
     * [MAX_PROFILE_FILES] Perfetto traces survive; everything older goes.
     */
    private fun pruneStaleFiles() {
        pruneDir(diagnosticsDir, MAX_DIAGNOSTIC_FILES, keep = WATERMARK_FILE)
        pruneDir(File(context.filesDir, "profiles"), MAX_PROFILE_FILES, keep = null)
    }

    private fun pruneDir(dir: File, maxFiles: Int, keep: String?) {
        val candidates = dir.listFiles()
            ?.filter { it.isFile && it.name != keep }
            ?: return
        candidates
            .sortedByDescending { it.lastModified() }
            .drop(maxFiles)
            .forEach { stale ->
                if (!stale.delete()) Log.w(TAG, "failed to prune ${stale.absolutePath}")
            }
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
        const val WATERMARK_FILE = "exit-info-watermark.txt"

        /** Newest exit-info dumps kept in filesDir/diagnostics after pruning. */
        const val MAX_DIAGNOSTIC_FILES = 10

        /** Newest anomaly Perfetto traces kept in filesDir/profiles after pruning. */
        const val MAX_PROFILE_FILES = 5
    }
}
