package com.dustvalve.next.android.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compat flavor stub: ProfilingManager / ProfilingTrigger / ANOMALY are
 * API 35+/37+ types that must not be referenced at class-load on minSdk 26.
 * Future flavor ships the real implementation.
 *
 * Context is accepted so [DiagnosticsInitializer] and Hilt stay API-compatible
 * with the future implementation; the stub does not use it.
 */
@Singleton
class ProfilingCaptureController @Inject constructor(@Suppress("UNUSED_PARAMETER") @param:ApplicationContext private val context: Context) {
    fun start() {
        // No-op on compat (minSdk 26).
    }
}
