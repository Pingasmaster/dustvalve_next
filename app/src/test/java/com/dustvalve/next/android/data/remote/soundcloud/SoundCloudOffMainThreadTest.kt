package com.dustvalve.next.android.data.remote.soundcloud

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Regression guard for the `NetworkOnMainThreadException` that shipped in
 * 0.5.20: [SoundCloudApi] and [SoundCloudClientIdProvider] ran their blocking
 * OkHttp calls straight inside suspend functions with no `withContext`, and
 * every caller sits on Main - `SoundCloudViewModel.init` starts `loadHome()`
 * on `viewModelScope`, whose `Dispatchers.Main.immediate` runs the body
 * *inline in the constructor* when it is already on the main thread. Opening
 * the SoundCloud tab therefore did DNS + HTTP inside Compose composition, and
 * release builds (which keep the OS-default StrictMode death-on-network
 * policy) died on it. Debug builds masked it: the app installs a
 * `penaltyLog()`-only ThreadPolicy.
 *
 * Both tests call from a named single-thread dispatcher standing in for Main
 * and assert the HTTP work landed on some *other* thread. An OkHttp
 * application interceptor short-circuits every request, so this is hermetic -
 * no socket is ever opened - while still running on exactly the thread that
 * `Call.execute()` was invoked from, which is the thread under test.
 */
class SoundCloudOffMainThreadTest {

    private val fakeMainExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, FAKE_MAIN_THREAD) }
    private val fakeMain = fakeMainExecutor.asCoroutineDispatcher()

    /** Threads that actually executed an OkHttp call, in order. */
    private val executingThreads = mutableListOf<String>()

    @After fun tearDown() {
        fakeMain.close()
        fakeMainExecutor.shutdownNow()
    }

    private fun clientReturning(body: (String) -> Pair<String, String>): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                synchronized(executingThreads) { executingThreads += Thread.currentThread().name }
                val url = chain.request().url.toString()
                val (contentType, payload) = body(url)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(HTTP_OK)
                    .message("OK")
                    .body(payload.toResponseBody(contentType.toMediaType()))
                    .build()
            },
        )
        .build()

    @Test fun `client_id scrape never runs on the calling thread`() = runTest {
        val settings = mockk<SettingsDataStore>()
        every { settings.soundcloudClientId } returns flowOf(null)
        coJustRun { settings.setSoundcloudClientId(any()) }

        val provider = SoundCloudClientIdProvider(
            okHttpClient = clientReturning { url ->
                if (url.endsWith(".js")) {
                    "application/javascript" to """window.x={client_id:"$FAKE_CLIENT_ID"};"""
                } else {
                    "text/html" to """<html><script src="https://a-v2.sndcdn.com/assets/app.js"></script></html>"""
                }
            },
            settingsDataStore = settings,
            ioDispatcher = Dispatchers.IO,
        )

        val clientId = withContext(fakeMain) { provider.getClientId() }

        assertThat(clientId).isEqualTo(FAKE_CLIENT_ID)
        // The homepage GET plus the script GET both happened...
        assertThat(executingThreads).hasSize(2)
        // ...and neither on the caller's (stand-in Main) thread.
        assertThat(executingThreads).doesNotContain(FAKE_MAIN_THREAD)
    }

    @Test fun `api calls never run on the calling thread`() = runTest {
        val clientIdProvider = mockk<SoundCloudClientIdProvider>()
        coEvery { clientIdProvider.getClientId() } returns FAKE_CLIENT_ID

        val api = SoundCloudApi(
            okHttpClient = clientReturning { "application/json" to "{}" },
            clientIdProvider = clientIdProvider,
            ioDispatcher = Dispatchers.IO,
        )

        withContext(fakeMain) { api.charts(genreSlug = "all-music") }

        // getAppVersion's versions.json GET and the charts GET.
        assertThat(executingThreads).isNotEmpty()
        assertThat(executingThreads).doesNotContain(FAKE_MAIN_THREAD)
    }

    private companion object {
        const val FAKE_MAIN_THREAD = "fake-main-thread"
        const val FAKE_CLIENT_ID = "abcdefghij0123456789ABCDEFGHIJ01"
        const val HTTP_OK = 200
    }
}
