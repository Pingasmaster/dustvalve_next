@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.remote

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.util.CookieEncryption
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
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

        store = CookieStore(settings, UnconfinedTestDispatcher())
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

    @Test fun `clearCookiesForDomain removes matching only`() = runBlocking {
        store.importCookies(mapOf("k" to "v"), domain = "bandcamp.com")
        store.importCookies(mapOf("yk" to "yv"), domain = "youtube.com")
        store.clearCookiesForDomain("bandcamp.com")
        assertThat(store.loadCookiesForDomain("bandcamp.com")).isEmpty()
        assertThat(store.loadCookiesForDomain("youtube.com").map { it.name }).contains("yk")
    }

    @Test fun `loadForRequest omits secure cookies on plain http`() = runBlocking {
        // importCookies marks imported cookies secure; they must never be
        // attached to a cleartext request.
        store.importCookies(mapOf("session" to "x"))
        val httpCookies = store.loadForRequest("http://bandcamp.com/foo".toHttpUrl())
        val httpsCookies = store.loadForRequest("https://bandcamp.com/foo".toHttpUrl())
        assertThat(httpCookies).isEmpty()
        assertThat(httpsCookies.map { it.name }).contains("session")
    }

    @Test fun `cookies imported before init completes are not clobbered by the disk load`() {
        // Regression: the async init used to unconditionally assign the disk
        // snapshot to the cache, wiping cookies imported during startup.
        val diskJson = """[{"name":"old","value":"v","domain":"bandcamp.com"}]"""
        val gate = CountDownLatch(1)
        val spySettings = spyk(settings)
        every { spySettings.authCookies } returns flow {
            gate.await() // hold the disk load until the import below has run
            emit(diskJson)
        }
        val racyStore = CookieStore(spySettings, Dispatchers.IO)

        runBlocking { racyStore.importCookies(mapOf("new" to "nv")) }
        gate.countDown()

        // loadCookiesForDomain awaits the init latch, so by the time it returns
        // the init coroutine has taken its keep-or-clobber decision.
        val names = racyStore.loadCookiesForDomain("bandcamp.com").map { it.name }
        assertThat(names).contains("new")
    }

    @Test fun `init failure still releases the latch so later saves are not dropped`() {
        // Regression: a throw during the init load used to leave the latch
        // armed forever; every subsequent save/load then timed out and was
        // silently skipped.
        val spySettings = spyk(settings)
        every { spySettings.authCookies } returns flow { throw IllegalStateException("boom") }
        val failingStore = CookieStore(spySettings, Dispatchers.IO)

        val cookie = Cookie.Builder()
            .name("s").value("v").domain("bandcamp.com").path("/").build()
        failingStore.saveFromResponse("https://bandcamp.com/".toHttpUrl(), listOf(cookie))
        assertThat(failingStore.loadCookiesForDomain("bandcamp.com").map { it.name }).contains("s")
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
