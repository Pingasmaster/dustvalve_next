package com.dustvalve.next.android.util

import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires API 35+ ProfilingManager so the OS auto-captures a heap dump + Perfetto
 * trace when the app is LMK-killed before we can take one ourselves. The result
 * lands in a file under filesDir/profiles/ for `adb pull`.
 *
 * Background: when Android kills the process for memory pressure, the platform
 * has a small window to capture a trace and we want it. registerForAllProfilingResults()
 * also drains any results that arrived while the process was dead, which is the
 * common case for TRIGGER_TYPE_ANOMALY.
 *
 * Legacy branch: minSdk=26, so the ProfilingManager + ProfilingTrigger +
 * ANOMALY trigger surface (API 35/36) is gated behind a SDK_INT check and
 * start() is a no-op on older devices.
 */
@Singleton
class ProfilingCaptureController @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    @Suppress("TooGenericExceptionCaught") // Robolectric NPE catch - see below.
    fun start() {
        // ProfilingManager is API 35+, ProfilingTrigger + ANOMALY is API 37+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
        try {
            val pm = context.getSystemService(ProfilingManager::class.java) ?: return
            // Drain anything captured while we were dead.
            pm.registerForAllProfilingResults(executor, Consumer { onResult(it) })
            // Re-arm the anomaly trigger for the next death. Builder on
            // android.os.ProfilingTrigger only takes the trigger type; the
            // Executor + Consumer come from registerForAllProfilingResults.
            val trigger = android.os.ProfilingTrigger.Builder(
                android.os.ProfilingTrigger.TRIGGER_TYPE_ANOMALY,
            ).build()
            // addProfilingTriggers replaces-by-type: safe to call repeatedly.
            pm.addProfilingTriggers(listOf(trigger))
        } catch (se: SecurityException) {
            Log.w(TAG, "start: missing permission", se)
        } catch (iae: IllegalArgumentException) {
            Log.w(TAG, "start: invalid trigger config", iae)
        } catch (t: Throwable) {
            // Robolectric and other headless environments throw NPE from
            // ProfilingManager.registerForAllProfilingResults because the
            // IProfilingService binder is absent. Real devices succeed;
            // anything else is best-effort diagnostics we never want to
            // crash the app over.
            Log.w(TAG, "start: platform ProfilingManager unavailable", t)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun onResult(result: ProfilingResult) {
        if (result.errorCode != ProfilingResult.ERROR_NONE) {
            Log.w(TAG, "capture failed errorCode=${result.errorCode} message=${result.errorMessage}")
            return
        }
        // ProfilingResult exposes a path, not a File handle - the platform
        // owns the file lifetime; we only re-stamp it under filesDir/profiles
        // for easier adb pull. The original is deleted when this consumer
        // returns; if the user wants the artifact they must pull now.
        try {
            val src = File(result.resultFilePath.orEmpty())
            val dir = File(context.filesDir, "profiles").apply { mkdirs() }
            val outFile = File(dir, "anomaly-${System.currentTimeMillis()}.perfetto-trace")
            src.copyTo(outFile, overwrite = true)
            Log.i(TAG, "captured trace to ${outFile.absolutePath}")
        } catch (io: java.io.IOException) {
            Log.w(TAG, "copy trace failed", io)
        }
    }

    private companion object {
        const val TAG = "ProfilingCapture"
    }
}
