@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.remote.CookieStore
import com.dustvalve.next.android.util.CookieEncryption
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.URLEncoder

@RunWith(RobolectricTestRunner::class)
class AccountRepositoryImplTest {

    private lateinit var settings: SettingsDataStore
    private lateinit var cookieStore: CookieStore
    private lateinit var repo: AccountRepositoryImpl

    @Before fun setUp() {
        // AndroidKeyStore unavailable in Robolectric; stub encryption to identity so the
        // settings-backed persistence path works end-to-end in unit tests.
        mockkObject(CookieEncryption)
        every { CookieEncryption.encrypt(any()) } answers { firstArg() }
        every { CookieEncryption.decrypt(any()) } answers { firstArg() }

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
        settings = SettingsDataStore(ctx)
        runBlocking {
            settings.setAuthCookies(null)
            settings.clearAccount()
        }
        cookieStore = CookieStore(settings, UnconfinedTestDispatcher())
        repo = AccountRepositoryImpl(settings, cookieStore)
    }

    @After fun tearDown() {
        unmockkAll()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
    }

    @Test fun `saveCookies extracts identity fields`() = runBlocking {
        val identity = URLEncoder.encode(
            """{"username":"fan123","photo":"https://img/x.jpg","fan_id":42}""",
            "UTF-8",
        )
        repo.saveCookies(mapOf("identity" to identity, "session" to "s"))
        assertThat(settings.accountUsername.first()).isEqualTo("fan123")
        assertThat(settings.accountAvatar.first()).isEqualTo("https://img/x.jpg")
        assertThat(settings.accountFanId.first()).isEqualTo(42L)
    }

    @Test fun `saveCookies survives identity fields that are not primitives`() = runBlocking {
        // Regression: a server-controlled identity blob whose username/photo is an
        // object or array used to throw from .jsonPrimitive AFTER the cookies were
        // imported, so saveCookies aborted and the user looked logged out despite
        // holding valid cookies.
        val identity = URLEncoder.encode(
            """{"username":{"weird":"shape"},"photo":["not","a","string"],"fan_id":7}""",
            "UTF-8",
        )
        repo.saveCookies(mapOf("identity" to identity, "session" to "s"))
        // Cookies imported; malformed fields degraded to null instead of aborting.
        assertThat(cookieStore.loadCookiesForDomain("bandcamp.com").map { it.name }).contains("session")
        assertThat(settings.accountUsername.first()).isNull()
        assertThat(settings.accountAvatar.first()).isNull()
        assertThat(settings.accountFanId.first()).isEqualTo(7L)
    }
}
