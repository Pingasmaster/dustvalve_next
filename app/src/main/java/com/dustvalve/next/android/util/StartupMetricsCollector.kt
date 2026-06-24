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
 *
 * The platform returns timestamps keyed by START_TIMESTAMP_* constants
 * (FORK, BIND_APPLICATION, APPLICATION_ONCREATE, LAUNCH, FIRST_FRAME,
 * FULLY_DRAWN). We pick out the most useful deltas: bind→onCreate,
 * onCreate→first-frame, fork→fully-drawn.
 */
@Singleton
class StartupMetricsCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun collectOnColdStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val infos = am.getHistoricalProcessStartReasons(MAX_ENTRIES)
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
    private fun ApplicationStartInfo.compactLine(): String {
        val ts = startupTimestamps
        fun delta(from: Int, to: Int): Long? {
            val f = ts[from] ?: return null
            val t = ts[to] ?: return null
            return t - f
        }
        return buildString {
            append("reason=").append(reason).append('(').append(reasonName(reason)).append(')')
            append(" startType=").append(startType)
            append(" bind→onCreate=").append(delta(
                ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION,
                ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE,
            ) ?: "?")
            append("ms onCreate→firstFrame=").append(delta(
                ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE,
                ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME,
            ) ?: "?")
            append("ms fork→fullyDrawn=").append(delta(
                ApplicationStartInfo.START_TIMESTAMP_FORK,
                ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN,
            ) ?: "?")
            append("ms")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationStartInfo.START_REASON_ALARM -> "ALARM"
        ApplicationStartInfo.START_REASON_BACKUP -> "BACKUP"
        ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> "BOOT"
        ApplicationStartInfo.START_REASON_LAUNCHER -> "LAUNCHER"
        ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> "RECENTS"
        ApplicationStartInfo.START_REASON_OTHER -> "OTHER"
        else -> "UNKNOWN($reason)"
    }

    private fun writeRow(info: ApplicationStartInfo) {
        try {
            val dir = File(context.filesDir, "metrics").apply { mkdirs() }
            val file = File(dir, "startup.csv")
            val wasNew = !file.exists()
            file.bufferedWriter().use { w ->
                if (wasNew) {
                    w.appendLine("ts,reason,startType,bindToOnCreate,onCreateToFirstFrame,forkToFullyDrawn")
                }
                val ts = info.startupTimestamps
                val bindToOnCreate = (ts[ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION]
                    ?.let { b ->
                        ts[ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE]?.minus(b)
                    })?.toString().orEmpty()
                val onCreateToFirst = (ts[ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE]
                    ?.let { c ->
                        ts[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME]?.minus(c)
                    })?.toString().orEmpty()
                val forkToFully = (ts[ApplicationStartInfo.START_TIMESTAMP_FORK]
                    ?.let { f ->
                        ts[ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN]?.minus(f)
                    })?.toString().orEmpty()
                w.append(System.currentTimeMillis().toString()).append(',')
                w.append(info.reason.toString()).append(',')
                w.append(info.startType.toString()).append(',')
                w.append(bindToOnCreate).append(',')
                w.append(onCreateToFirst).append(',')
                w.appendLine(forkToFully)
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
