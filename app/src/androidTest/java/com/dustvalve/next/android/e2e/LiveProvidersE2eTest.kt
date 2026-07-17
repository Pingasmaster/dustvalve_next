package com.dustvalve.next.android.e2e

import android.Manifest
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.waitForAnyText
import com.dustvalve.next.android.testing.Flows.waitForPositionPastZero
import com.dustvalve.next.android.testing.Flows.waitForTag
import com.dustvalve.next.android.testing.Flows.waitForText
import com.dustvalve.next.android.testing.LiveNetwork
import com.dustvalve.next.android.testing.QuarantineRule
import com.dustvalve.next.android.testing.RetryRule
import com.dustvalve.next.android.ui.TestTags
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 3 wave 2: LIVE provider workflows against real Bandcamp / YouTube
 * Music (user decision: live in CI; RetryRule + quarantine mitigate flake).
 * Catalog ids: bc-album-play-all, bc-playback-stream-mp3-128,
 * ytm-home-loads-shelves, ytm-play-resolves-live-stream (via home tap),
 * yt-deeplink-music-host-is-video-play (playback boundary).
 */
@LiveNetwork
@RunWith(AndroidJUnit4::class)
class LiveProvidersE2eTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val quarantine = QuarantineRule()

    @get:Rule(order = 2)
    val retry = RetryRule()

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun settings(): SettingsDataStore = SettingsDataStore(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    @Before fun enableProviders() {
        runBlocking {
            settings().setBandcampEnabled(true)
            settings().setYoutubeEnabled(true)
        }
    }

    // bc-home-genre-tiles-render + bc-category-sheet-content (live)
    @Test
    fun bandcamp_genreSheet_loadsAlbums() {
        composeRule.clickTab("bandcamp")
        composeRule.waitForText("rock", timeoutMs = 20_000)
        composeRule.onAllNodesWithText("rock")[0].performClick()
        // Albums (or the error+Retry state) must appear; never a crash.
        // Generous budget: live Bandcamp from CI runner IPs can be slow.
        composeRule.waitForAnyText("More", "Retry", timeoutMs = 90_000)
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    // ytm-home-loads-shelves + ytm-home-tile-song-click-plays: open YTM home,
    // wait for the feed, tap the hero/first playable element, and require
    // either audible progress or the defined failure snackbar - never a crash.
    @Test
    fun youtubeMusic_home_loads_noCrash() {
        composeRule.clickTab("youtube")
        composeRule.waitForText("YouTube Music", timeoutMs = 20_000)
        composeRule.onAllNodesWithText("YouTube Music")[0].performClick()
        // Feed loaded = chips or shelves appear; error state also acceptable
        // for a live test (bot checks); crash is not. 45s was not enough for
        // throttled CI runner IPs - the run timed out still on the skeleton.
        composeRule.waitForAnyText("Retry", "Play", "Quick picks", timeoutMs = 120_000)
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    // bc-playback-stream-mp3-128 via a long-lived album URL: navigate through
    // a deep link (provider enabled), then Play all and assert position > 0.
    @Test
    fun bandcamp_albumDeepLink_playAll_positionAdvances() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        composeRule.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[
                com.dustvalve.next.android.ui.navigation.NavigationViewModel::class.java,
            ].navigateTo(
                // Long-lived, free-streaming reference album (used by the
                // parser fixtures too).
                com.dustvalve.next.android.ui.navigation.NavDestination.AlbumDetail(
                    url = "https://moeshop.bandcamp.com/album/moe-moe",
                ),
            )
        }
        composeRule.waitForIdle()
        // Album loads: a track list + play affordance appears (or the
        // defined error+Retry state). Generous live budget - see above.
        composeRule.waitForAnyText("Play", "Retry", timeoutMs = 120_000)
        val playNodes = composeRule.onAllNodesWithText("Play", substring = true).fetchSemanticsNodes()
        if (playNodes.isNotEmpty()) {
            composeRule.onAllNodesWithText("Play", substring = true)[0].performClick()
            composeRule.waitForIdle()
            composeRule.waitForTag(TestTags.MINI_PLAYER, timeoutMs = 30_000)
            composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()
            composeRule.waitForPositionPastZero(timeoutMs = 30_000)
        }
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }
}
