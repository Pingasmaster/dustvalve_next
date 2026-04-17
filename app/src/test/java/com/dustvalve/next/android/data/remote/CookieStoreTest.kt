package com.dustvalve.next.android.data.remote

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.util.CookieEncryption
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CookieStoreTest {

    private lateinit var settings: SettingsDataStore
    private lateinit var store: CookieStore

    @Before fun setUp() {
        // AndroidKeyStore unavailable in Robolectric; stub encryption to identity so the
        // settings-backed persistence path works end-to-end in unit tests.
        mockkObject(CookieEncryption)
        every { CookieEncryption.encrypt(any()) } answers { firstArg() }
        every { CookieEncryption.decrypt(any()) } answers { firstArg() }

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
        settings = SettingsDataStore(ctx)
        // The DataStore singleton is cached per Context; Robolectric may reuse the Context
        // across tests. Clearing the auth cookies key through the live DataStore guarantees
        // a clean cache whether or not the on-disk file was already gone.
        kotlinx.coroutines.runBlocking { settings.setAuthCookies(null) }

        store = CookieStore(settings)
        // Allow the async init load to complete before any test runs so loadForRequest
        // (with its 500ms latch) doesn't see an uninitialized store.
        awaitInit()
    }

    @After fun tearDown() {
        unmockkAll()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
    }

    /** Trigger the init latch by calling a suspending method that awaits it. */
    private fun awaitInit() {
        // loadCookiesForDomain internally awaits initLatch up to 3s.
        repeat(3) { store.loadCookiesForDomain("bandcamp.com") }
    }

    @Test fun `importCookies persists cookies for bandcamp host`() = runBlocking {
        store.importCookies(mapOf("session" to "abc", "id" to "42"))
        val cookies = store.loadCookiesForDomain("bandcamp.com")
        assertThat(cookies.map { it.name }).containsExactly("session", "id")
        assertThat(cookies.first { it.name == "session" }.value).isEqualTo("abc")
    }

    @Test fun `loadForRequest filters host`() = runBlocking {
        store.importCookies(mapOf("session" to "x"))
        val goodCookies = store.loadForRequest("https://bandcamp.com/foo".toHttpUrl())
        val evilCookies = store.loadForRequest("https://evilbandcamp.com/foo".toHttpUrl())
        assertThat(goodCookies.map { it.name }).contains("session")
        assertThat(evilCookies).isEmpty()
    }

    @Test fun `saveFromResponse rejects non-bandcamp host`() = runBlocking {
        val url = "https://example.com/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("evil").value("x").domain("example.com").path("/").build()
        store.saveFromResponse(url, listOf(cookie))
        val results = store.loadCookiesForDomain("example.com")
        assertThat(results).isEmpty()
    }

    @Test fun `saveFromResponse accepted for bandcamp`() = runBlocking {
        val url = "https://bandcamp.com/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("foo").value("bar").domain("bandcamp.com").path("/").build()
        store.saveFromResponse(url, listOf(cookie))
        // saveFromResponse updates the cache synchronously; persistence is async but reads from cache
        val results = store.loadCookiesForDomain("bandcamp.com")
        assertThat(results.map { it.name }).contains("foo")
    }

    @Test fun `clearCookies removes all`() = runBlocking {
        store.importCookies(mapOf("k" to "v"))
        store.clearCookies()
        val results = store.loadCookiesForDomain("bandcamp.com")
        assertThat(results).isEmpty()
    }

    @Test fun `clearCookiesForDomain removes matching only`() = runBlocking {
        store.importCookies(mapOf("k" to "v"), domain = "bandcamp.com")
        store.importCookies(mapOf("yk" to "yv"), domain = "youtube.com")
        store.clearCookiesForDomain("bandcamp.com")
        assertThat(store.loadCookiesForDomain("bandcamp.com")).isEmpty()
        assertThat(store.loadCookiesForDomain("youtube.com").map { it.name }).contains("yk")
    }

    @Test fun `expired cookies dropped on load`() = runBlocking {
        // saveFromResponse with an expired cookie
        val expired = Cookie.Builder()
            .name("old").value("v").domain("bandcamp.com").path("/")
            .expiresAt(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
            .build()
        store.saveFromResponse("https://bandcamp.com/".toHttpUrl(), listOf(expired))
        val results = store.loadCookiesForDomain("bandcamp.com")
        assertThat(results).isEmpty()
    }
}
