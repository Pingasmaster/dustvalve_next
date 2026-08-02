package com.dustvalve.next.android.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compat flavor stub: ApplicationStartInfo is an API 35+ type that must not
 * be referenced at class-load on minSdk 26. Future flavor ships the real
 * implementation that writes startup.csv under filesDir/metrics/.
 */
@Singleton
class StartupMetricsCollector @Inject constructor(@Suppress("UNUSED_PARAMETER") @param:ApplicationContext private val context: Context) {
    fun collectOnColdStart() {
        // No-op on compat (minSdk 26).
    }
}
