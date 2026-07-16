package com.dustvalve.next.android.workflow

import android.content.Context
import androidx.startup.AppInitializer
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManagerInitializer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the contract the manifest relies on to disable WorkManager's
 * auto-init: androidx.startup's InitializationProvider only runs entries
 * whose meta-data value is exactly "androidx.startup". The app overrides
 * WorkManagerInitializer's value (tools:node="replace") instead of removing
 * the node, so Hilt's HiltWorkerFactory can provide on-demand init via
 * Configuration.Provider. If a manifest-merge regression ever restores the
 * library value, WorkManager would double-initialize - this test fails
 * first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class WorkManagerInitializerDisabledTest {

    @Test
    fun workManagerInitializer_isNotEagerlyInitialized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val eagerlyInitialized = AppInitializer.getInstance(context)
            .isEagerlyInitialized(WorkManagerInitializer::class.java)
        assertThat(eagerlyInitialized).isFalse()
    }
}
