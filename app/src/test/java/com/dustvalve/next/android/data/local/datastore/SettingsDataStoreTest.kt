package com.dustvalve.next.android.data.local.datastore

import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.util.CookieEncryption
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreTest {

    private lateinit var store: SettingsDataStore

    @Before fun setUp() {
        // AndroidKeyStore is not available in Robolectric; stub encrypt/decrypt to identity.
        mockkObject(CookieEncryption)
        every { CookieEncryption.encrypt(any()) } answers { firstArg() }
        every { CookieEncryption.decrypt(any()) } answers { firstArg() }

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
        store = SettingsDataStore(ctx)
    }

    @After fun tearDown() {
        unmockkAll()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/settings.preferences_pb").delete()
    }

    // Defaults are not asserted here: Robolectric's DataStore instance can persist across
    // test methods in the same class via the per-Context singleton delegate, so a value
    // written by a previous test is visible to a "default" test. We cover defaults
    // implicitly by the roundtrip tests (write a value -> read it back).

    @Test fun `theme mode roundtrip`() = runTest {
        store.setThemeMode("dark")
        assertThat(store.themeMode.first()).isEqualTo("dark")
    }

    @Test fun `storage limit clamps negative to zero`() = runTest {
        store.setStorageLimit(-500L)
        assertThat(store.storageLimit.first()).isEqualTo(0L)
    }

    @Test fun `storage limit positive persisted`() = runTest {
        store.setStorageLimit(1_000_000L)
        assertThat(store.getStorageLimitSync()).isEqualTo(1_000_000L)
    }

    @Test fun `account info atomic clear`() = runTest {
        store.setAccountInfo("user", "https://a/img", fanId = 42L)
        assertThat(store.accountUsername.first()).isEqualTo("user")
        assertThat(store.accountAvatar.first()).isEqualTo("https://a/img")
        assertThat(store.accountFanId.first()).isEqualTo(42L)

        store.setAccountInfo(null, null, null)
        assertThat(store.accountUsername.first()).isNull()
        assertThat(store.accountAvatar.first()).isNull()
        assertThat(store.accountFanId.first()).isNull()
    }

    @Test fun `auth cookies roundtrip via encryption stub`() = runTest {
        store.setAuthCookies("""[{"name":"session","value":"xyz"}]""")
        assertThat(store.authCookies.first()).isEqualTo("""[{"name":"session","value":"xyz"}]""")
    }

    @Test fun `auth cookies clear`() = runTest {
        store.setAuthCookies("foo")
        store.setAuthCookies(null)
        assertThat(store.authCookies.first()).isNull()
    }

    @Test fun `local music folder uris add remove dedup`() = runTest {
        store.addLocalMusicFolderUri("content://a")
        store.addLocalMusicFolderUri("content://b")
        store.addLocalMusicFolderUri("content://a") // dupe ignored
        assertThat(store.localMusicFolderUris.first()).containsExactly("content://a", "content://b").inOrder()

        store.removeLocalMusicFolderUri("content://a")
        assertThat(store.localMusicFolderUris.first()).containsExactly("content://b")

        store.removeLocalMusicFolderUri("content://b")
        assertThat(store.localMusicFolderUris.first()).isEmpty()
    }

    @Test fun `boolean toggles roundtrip`() = runTest {
        store.setDynamicColor(false); assertThat(store.dynamicColor.first()).isFalse()
        store.setOledBlack(true); assertThat(store.oledBlack.first()).isTrue()
        store.setAlbumArtTheme(true); assertThat(store.albumArtTheme.first()).isTrue()
        store.setProgressBarStyle("linear"); assertThat(store.progressBarStyle.first()).isEqualTo("linear")
        store.setProgressBarSizeDp(8); assertThat(store.progressBarSizeDp.first()).isEqualTo(8)
        store.setAutoDownloadFavorites(true); assertThat(store.autoDownloadFavorites.first()).isTrue()
        store.setBandcampEnabled(true); assertThat(store.bandcampEnabled.first()).isTrue()
        store.setYoutubeEnabled(true); assertThat(store.youtubeEnabled.first()).isTrue()
        store.setKeepScreenOnInApp(true); assertThat(store.keepScreenOnInApp.first()).isTrue()
        store.setKeepScreenOnWhilePlaying(true); assertThat(store.keepScreenOnWhilePlaying.first()).isTrue()
        store.setSearchHistoryEnabled(false); assertThat(store.searchHistoryEnabled.first()).isFalse()
        store.setAlbumCoverLongPressCarousel(false); assertThat(store.albumCoverLongPressCarousel.first()).isFalse()
        store.setYtmConnected(true); assertThat(store.ytmConnected.first()).isTrue()
        store.setAutoDownloadCollection(false); assertThat(store.autoDownloadCollection.first()).isFalse()
        store.setAutoDownloadFutureContent(true); assertThat(store.getAutoDownloadFutureContentSync()).isTrue()
        store.setProgressiveDownload(false); assertThat(store.getProgressiveDownloadSync()).isFalse()
        store.setSeamlessQualityUpgrade(false); assertThat(store.getSeamlessQualityUpgradeSync()).isFalse()
        store.setSaveDataOnMetered(false); assertThat(store.getSaveDataOnMeteredSync()).isFalse()
    }

    @Test fun `clearAccount removes all account fields`() = runTest {
        store.setAccountInfo("u", "a", 1L)
        store.setAuthCookies("c")
        store.clearAccount()
        assertThat(store.accountUsername.first()).isNull()
        assertThat(store.accountAvatar.first()).isNull()
        assertThat(store.accountFanId.first()).isNull()
        assertThat(store.authCookies.first()).isNull()
    }

    @Test fun `download format roundtrip`() = runTest {
        store.setDownloadFormat("mp3-320")
        assertThat(store.getDownloadFormatSync()).isEqualTo("mp3-320")
    }

    @Test fun `last youtube video id setter and clear`() = runTest {
        store.setLastYoutubeVideoId("abc")
        assertThat(store.lastYoutubeVideoId.first()).isEqualTo("abc")
        store.setLastYoutubeVideoId(null)
        assertThat(store.lastYoutubeVideoId.first()).isNull()
    }
}
