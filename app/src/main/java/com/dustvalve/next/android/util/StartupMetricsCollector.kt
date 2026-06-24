package com.dustvalve.next.android.util

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads [ApplicationStartInfo] from the most recent cold/warm start on API 35+
 * and writes a single-line CSV under filesDir/metrics/. minSdk=36 on this app
 * means the API is always available.
 *
 * One row per process start, appended on each onCreate. No UI; pull with
 * `adb pull /sdcard/Android/data/com.dustvalve.next.android/files/metrics/`
 * for offline comparison against Macrobenchmark StartupTimingMetric.
 */
@Singleton
class StartupMetricsCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun collectOnColdStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val infos = am.getHistoricalProcessStartReasons(context.packageName, MAX_ENTRIES)
            val info = infos.firstOrNull() ?: return
            writeRow(info)
            Log.i(TAG, info.compactLine())
        } catch (se: SecurityException) {
            Log.w(TAG, "collectOnColdStart: missing GET_TASKS", se)
        } catch (iae: IllegalArgumentException) {
            Log.w(TAG, "collectOnColdStart: invalid package/pid", iae)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun ApplicationStartInfo.compactLine(): String = buildString {
        append("reason=").append(startupReason).append("(").append(reasonName(startupReason)).append(")")
        append(" startUptime=").append(startUptimeMillis)
        append(" app=").append(applicationDelayElapsedMillis)
        append(" firstActivity=").append(firstActivityDelayElapsedMillis)
        append(" total=").append(totalDelayElapsedMillis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Pre/post-baseline-profile breakdown — meaningful only when a
            // baseline-prof.txt is shipped.
            try {
                append(" preBaseline=").append(preBaselineProfileCompilationMillis)
                append(" postBaseline=").append(postBaselineProfileCompilationMillis)
            } catch (_: NoSuchMethodError) {
                // Fallback if running on an OS that lacks the API surface.
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationStartInfo.STARTUP_REASON_ALARM -> "ALARM"
        ApplicationStartInfo.STARTUP_REASON_BACKUP -> "BACKUP"
        ApplicationStartInfo.STARTUP_REASON_BOOT_COMPLETE -> "BOOT"
        ApplicationStartInfo.STARTUP_REASON_LAUNCHER -> "LAUNCHER"
        ApplicationStartInfo.STARTUP_REASON_LAUNCHER_RECENTS -> "RECENTS"
        ApplicationStartInfo.STARTUP_REASON_OTHER -> "OTHER"
        else -> "UNKNOWN($reason)"
    }

    private fun writeRow(info: ApplicationStartInfo) {
        try {
            val dir = File(context.filesDir, "metrics").apply { mkdirs() }
            val file = File(dir, "startup.csv")
            val wasNew = !file.exists()
            file.bufferedWriter().use { w ->
                if (wasNew) w.appendLine("ts,reason,startUptime,appDelay,firstActivityDelay,totalDelay")
                w.append(System.currentTimeMillis()).append(',')
                w.append(info.startupReason.toString()).append(',')
                w.append(info.startUptimeMillis.toString()).append(',')
                w.append(info.applicationDelayElapsedMillis.toString()).append(',')
                w.append(info.firstActivityDelayElapsedMillis.toString()).append(',')
                w.appendLine(info.totalDelayElapsedMillis.toString())
            }
        } catch (io: java.io.IOException) {
            Log.w(TAG, "writeRow failed", io)
        }
    }

    private companion object {
        const val TAG = "StartupMetrics"
        const val MAX_ENTRIES = 5
    }
}
