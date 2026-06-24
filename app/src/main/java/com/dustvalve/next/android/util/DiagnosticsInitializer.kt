package com.dustvalve.next.android.util

import android.content.Context
import androidx.startup.Initializer

/**
 * androidx.startup Initializer that runs the cold-start diagnostic captures
 * (ApplicationStartInfo + ApplicationExitInfo + ProfilingManager ANOMALY
 * trigger) once per process. Registered via the [androidx.startup
 * .InitializationProvider] in AndroidManifest.xml so the OS can run them
 * before MainActivity, shaving a few ms off the cold-start critical path
 * relative to running them inside [com.dustvalve.next.android.DustvalveNextApplication.onCreate].
 *
 * Reads no dependencies from Hilt (these collectors take only
 * @ApplicationContext), so they can run before the Hilt graph is built.
 */
class DiagnosticsInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        StartupMetricsCollector(context).collectOnColdStart()
        DiagnosticsCollector(context).collectOnColdStart()
        ProfilingCaptureController(context).start()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
