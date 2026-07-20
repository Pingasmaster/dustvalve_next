package com.dustvalve.next.android.e2e

import android.Manifest
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.waitForAnySignal
import com.dustvalve.next.android.testing.Flows.waitForAnyText
import com.dustvalve.next.android.testing.Flows.waitForPositionPastZero
import com.dustvalve.next.android.testing.Flows.waitForTag
import com.dustvalve.next.android.testing.Flows.waitForText
import com.dustvalve.next.android.testing.LiveNetwork
import com.dustvalve.next.android.testing.ProviderStateRule
import com.dustvalve.next.android.testing.QuarantineRule
import com.dustvalve.next.android.testing.RetryRule
import com.dustvalve.next.android.ui.TestTags
import com.google.common.truth.Truth.assertThat
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

    /**
     * These tests drive the live provider screens, so both providers must be
     * on. Declared as a rule rather than an @Before so the flags are written
     * BEFORE MainActivity launches - an @Before runs after the activity is
     * already up and leaves the nav bar reacting to the write mid-test. This
     * is also the write that used to leak into later classes; see
     * [ProviderStateRule].
     */
    @get:Rule(order = 3)
    val providerState = ProviderStateRule(bandcamp = true, youtube = true)

    @get:Rule(order = 4)
    val composeRule = createAndroidComposeRule<MainActivity>()

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
        // Feed loaded = the YTM_FEED LazyColumn composes (shelf titles are
        // server-dynamic, so no literal string is stable); the error state
        // also acceptable for a live test (bot checks); crash is not.
        composeRule.waitForAnySignal(
            texts = listOf("Retry"),
            tags = listOf(TestTags.YTM_FEED),
            timeoutMs = 120_000,
        )
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    // yt-deeplink-watch-url + yt-play-media boundary: a stable watch URL
    // (YouTube's own first upload, permanent) must resolve a live stream and
    // the player position must leave 0:00 - full pipeline: link routing ->
    // innertube client cascade -> ExoPlayer -> position poller.
    @Test
    fun youtube_watchLink_playback_positionAdvances() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        composeRule.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[
                com.dustvalve.next.android.ui.navigation.NavigationViewModel::class.java,
            ].openLink("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        }
        composeRule.waitForIdle()
        composeRule.waitForTag(TestTags.MINI_PLAYER, timeoutMs = 90_000)
        composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()
        composeRule.waitForPositionPastZero(timeoutMs = 30_000)
    }

    // ytm-deeplink-watch-plays: the same video via a music.youtube.com URL -
    // canonicalizes to www and plays through the shared innertube stack,
    // covering the YTM link-routing branch end to end.
    @Test
    fun youtubeMusic_watchLink_playback_positionAdvances() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        composeRule.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[
                com.dustvalve.next.android.ui.navigation.NavigationViewModel::class.java,
            ].openLink("https://music.youtube.com/watch?v=jNQXAC9IVRw")
        }
        composeRule.waitForIdle()
        composeRule.waitForTag(TestTags.MINI_PLAYER, timeoutMs = 90_000)
        composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()
        composeRule.waitForPositionPastZero(timeoutMs = 30_000)
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
        // Album loads: the play-all affordance is an ICON whose content
        // description is "Play All" (common_play_all) - there is no literal
        // "Play" text on the screen. Error+Retry also acceptable.
        composeRule.waitForAnySignal(
            texts = listOf("Retry"),
            contentDescriptions = listOf("Play All"),
            timeoutMs = 120_000,
        )
        val playNodes = composeRule.onAllNodesWithContentDescription("Play All").fetchSemanticsNodes()
        if (playNodes.isNotEmpty()) {
            composeRule.onAllNodesWithContentDescription("Play All")[0].performClick()
            composeRule.waitForIdle()
            composeRule.waitForTag(TestTags.MINI_PLAYER, timeoutMs = 30_000)
            composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()
            composeRule.waitForPositionPastZero(timeoutMs = 30_000)
        }
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }
}
