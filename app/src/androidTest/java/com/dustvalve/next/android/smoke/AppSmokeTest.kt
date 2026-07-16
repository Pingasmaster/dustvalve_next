package com.dustvalve.next.android.smoke

import android.Manifest
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.player.PlaybackService
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.enableLocalMusicViaCta
import com.dustvalve.next.android.testing.Flows.waitForPositionPastZero
import com.dustvalve.next.android.testing.Flows.waitForTag
import com.dustvalve.next.android.testing.Flows.waitForText
import com.dustvalve.next.android.testing.LiveNetwork
import com.dustvalve.next.android.testing.LocalMusicSeeder
import com.dustvalve.next.android.testing.QuarantineRule
import com.dustvalve.next.android.testing.RetryRule
import com.dustvalve.next.android.testing.SmokeTest
import com.dustvalve.next.android.ui.TestTags
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 2: on-device smoke. Gates everything else in CI. Every test here maps
 * to a v0.5.0 production regression:
 * - local playback must actually advance past 0:00 (both via the UI label
 *   and a real MediaController connected to PlaybackService), and
 * - opening/clicking through Bandcamp and YouTube Music must never crash.
 */
@SmokeTest
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val quarantine = QuarantineRule()

    @get:Rule(order = 2)
    val retry = RetryRule()

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun settings(): SettingsDataStore = SettingsDataStore(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    @Before fun seed() {
        LocalMusicSeeder.seed()
    }

    @After fun cleanup() {
        LocalMusicSeeder.cleanup()
    }

    @Test
    fun appLaunches_bottomNavRenders() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        composeRule.waitForTag(TestTags.bottomNavItem("local"))
        composeRule.waitForTag(TestTags.bottomNavItem("settings"))
    }

    @Test
    fun localTrack_playsPastZero() {
        val enableLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(com.dustvalve.next.android.R.string.local_enable)
        composeRule.enableLocalMusicViaCta(enableLabel)

        // Wait for the app's own scan to surface the seeded tones, then tap one.
        composeRule.waitForText("Dustvalve Test Tone 1", timeoutMs = 30_000)
        composeRule.onAllNodesWithText("Dustvalve Test Tone 1")[0].performClick()
        composeRule.waitForIdle()

        // Mini player appears; expand to the full player.
        composeRule.waitForTag(TestTags.MINI_PLAYER)
        composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()

        // THE regression assertion: elapsed label leaves 0:00.
        composeRule.waitForPositionPastZero()

        // Independent, UI-free confirmation through the real media session.
        // MediaController must only be touched from its application thread
        // (the main looper it was built on).
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val token = SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().get()
        try {
            val position = java.util.concurrent.atomic.AtomicLong(0)
            val playing = java.util.concurrent.atomic.AtomicBoolean(false)
            composeRule.waitUntil(10_000) {
                instrumentation.runOnMainSync {
                    position.set(controller.currentPosition)
                    playing.set(controller.isPlaying)
                }
                position.get() > 0
            }
            assertThat(playing.get()).isTrue()
            assertThat(position.get()).isGreaterThan(0L)
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }

    @Test
    @LiveNetwork
    fun bandcampTab_browseAndOpenAlbum_noCrash() {
        runBlocking { settings().setBandcampEnabled(true) }
        composeRule.clickTab("bandcamp")
        // Discover header proves BandcampScreen composed; genre tiles render
        // even without preview art. Tap the first genre tile.
        composeRule.waitForText("Discover")
        composeRule.waitForText("rock")
        composeRule.onAllNodesWithText("rock")[0].performClick()
        composeRule.waitForIdle()
        // Category sheet must open (content or error state - never a crash).
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    @Test
    @LiveNetwork
    fun youtubeMusicTab_openAndSwitch_noCrash() {
        runBlocking { settings().setYoutubeEnabled(true) }
        composeRule.clickTab("youtube")
        composeRule.waitForText("YouTube Music")
        composeRule.onAllNodesWithText("YouTube Music")[0].performClick()
        composeRule.waitForIdle()
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    @Test
    fun navSweep_allTabs_processAlive() {
        runBlocking {
            settings().setLocalMusicEnabled(true)
            settings().setBandcampEnabled(true)
            settings().setYoutubeEnabled(true)
        }
        for (tab in listOf("local", "bandcamp", "youtube", "library", "settings")) {
            composeRule.clickTab(tab)
        }
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }
}
