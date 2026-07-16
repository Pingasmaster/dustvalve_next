package com.dustvalve.next.android.workflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Regression net for the v0.5.0 "tapping Bandcamp or YouTube Music crashes
 * the app" bug. Boots the REAL MainActivity on the REAL Hilt graph under
 * Robolectric, enables the remote providers, and drives the bottom nav into
 * each provider screen. Compose's test harness rethrows any exception from
 * composition, so a render/click crash in these screens fails the test with
 * the original stack trace. Network is unavailable under Robolectric; the
 * screens must degrade to their error states, never crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = com.dustvalve.next.android.DustvalveNextApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ProviderScreensCrashRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // SettingsDataStore's backing DataStore is a top-level Context extension
    // delegate, i.e. one instance per process: constructing a second wrapper
    // here writes through the exact store the running app observes.
    private fun settings(): SettingsDataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

    private fun waitForText(text: String, timeoutMs: Long = 10_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun appLaunches_defaultTabsRender() {
        waitForText(string(R.string.nav_label_local))
        composeRule.onNodeWithText(string(R.string.nav_label_library)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_label_settings)).assertIsDisplayed()
    }

    @Test
    fun bandcampTab_opensWithoutCrash() {
        runBlocking { settings().setBandcampEnabled(true) }
        waitForText(string(R.string.nav_label_bandcamp))

        composeRule.onNodeWithText(string(R.string.nav_label_bandcamp)).performClick()
        composeRule.waitForIdle()

        // Discover header proves BandcampScreen actually composed.
        waitForText(string(R.string.bandcamp_discover))
    }

    @Test
    fun youtubeTab_opensWithoutCrash_andSubTabsSwitch() {
        runBlocking { settings().setYoutubeEnabled(true) }
        waitForText(string(R.string.nav_label_youtube))

        composeRule.onNodeWithText(string(R.string.nav_label_youtube)).performClick()
        composeRule.waitForIdle()

        // Switch to the YouTube Music sub-tab; this composes YouTubeMusicHome.
        val ytmLabel = string(R.string.youtube_tab_source_ytm)
        waitForText(ytmLabel)
        composeRule.onAllNodesWithText(ytmLabel)[0].performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settingsTab_opensWithoutCrash() {
        waitForText(string(R.string.nav_label_settings))
        composeRule.onNodeWithText(string(R.string.nav_label_settings)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun libraryTab_opensWithoutCrash() {
        waitForText(string(R.string.nav_label_library))
        composeRule.onNodeWithText(string(R.string.nav_label_library)).performClick()
        composeRule.waitForIdle()
    }

    // The v0.5.0 crash fired "when a Bandcamp/YouTube Music item is clicked":
    // a click lands in a detail destination. Compose each detail screen via
    // the app's real navigation - network is down, so they must render their
    // error/loading states without crashing.
    private fun navigate(dest: com.dustvalve.next.android.ui.navigation.NavDestination) {
        composeRule.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[
                com.dustvalve.next.android.ui.navigation.NavigationViewModel::class.java,
            ].navigateTo(dest)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun albumDetail_composesWithoutCrash() {
        runBlocking { settings().setBandcampEnabled(true) }
        waitForText(string(R.string.nav_label_bandcamp))
        navigate(
            com.dustvalve.next.android.ui.navigation.NavDestination.AlbumDetail(
                url = "https://testartist.bandcamp.com/album/test-album",
            ),
        )
        composeRule.waitForIdle()
    }

    @Test
    fun artistDetail_composesWithoutCrash() {
        runBlocking { settings().setBandcampEnabled(true) }
        waitForText(string(R.string.nav_label_bandcamp))
        navigate(
            com.dustvalve.next.android.ui.navigation.NavDestination.ArtistDetail(
                url = "https://testartist.bandcamp.com",
                sourceId = "bandcamp",
            ),
        )
        composeRule.waitForIdle()
    }

    @Test
    fun collectionDetail_composesWithoutCrash() {
        runBlocking { settings().setYoutubeEnabled(true) }
        waitForText(string(R.string.nav_label_youtube))
        navigate(
            com.dustvalve.next.android.ui.navigation.NavDestination.CollectionDetail(
                url = "https://www.youtube.com/playlist?list=PL0000000000000000000000000000000000",
                sourceId = "youtube",
                name = "Test Playlist",
            ),
        )
        composeRule.waitForIdle()
    }
}
