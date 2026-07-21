package com.dustvalve.next.android.di

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.remote.CookieStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the OkHttp timeout split introduced after the v0.5.0 regression:
 *
 * The base client's 30s callTimeout caps the WHOLE call - from newCall()
 * until the response body is closed. ExoPlayer's OkHttpDataSource keeps a
 * progressive stream's body open for the life of the track, so routing the
 * player (or a multi-minute track/APK download) through the base client
 * force-aborted every stream ~30s in. Media transfers must use the
 * [com.dustvalve.next.android.di.qualifiers.MediaHttp] client, which has NO
 * call timeout but keeps connect/read timeouts as the stall guards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class NetworkModuleMediaClientTest {

    private fun baseClient() = NetworkModule.provideOkHttpClient(
        ApplicationProvider.getApplicationContext(),
        CookieStore(
            SettingsDataStore(ApplicationProvider.getApplicationContext()),
            UnconfinedTestDispatcher(),
        ),
    )

    @Test
    fun baseClient_keepsWholeCallTimeout_forScrapingAndApiCalls() {
        val base = baseClient()
        assertThat(base.callTimeoutMillis).isEqualTo(30_000)
    }

    @Test
    fun mediaClient_hasNoWholeCallTimeout_butKeepsStallGuards() {
        val base = baseClient()
        val media = NetworkModule.provideMediaOkHttpClient(base)

        // No whole-call cap: streams and long downloads must be allowed to
        // outlive 30s.
        assertThat(media.callTimeoutMillis).isEqualTo(0)
        // Stall guards survive the derivation.
        assertThat(media.connectTimeoutMillis).isEqualTo(base.connectTimeoutMillis)
        assertThat(media.readTimeoutMillis).isEqualTo(base.readTimeoutMillis)
        // Shared infrastructure survives the derivation (same pool + cache).
        assertThat(media.connectionPool).isSameInstanceAs(base.connectionPool)
        assertThat(media.cache).isSameInstanceAs(base.cache)
    }
}
