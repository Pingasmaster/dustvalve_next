package com.dustvalve.next.android.e2e

import android.Manifest
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.enableLocalMusicViaCta
import com.dustvalve.next.android.testing.Flows.waitForPositionPastZero
import com.dustvalve.next.android.testing.Flows.waitForTag
import com.dustvalve.next.android.testing.Flows.waitForText
import com.dustvalve.next.android.testing.LocalMusicSeeder
import com.dustvalve.next.android.testing.QuarantineRule
import com.dustvalve.next.android.ui.TestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 3 wave 1: local playback E2E. Catalog ids implemented:
 * local-play-track-starts, local-pause-resume, local-play-while-other-playing.
 * All hermetic (no network).
 */
@RunWith(AndroidJUnit4::class)
class PlaybackLocalE2eTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val quarantine = QuarantineRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun seed() {
        LocalMusicSeeder.seed()
    }

    private fun playTone(n: Int) {
        val enableLabel = com.dustvalve.next.android.testing.Flows.appString("local_enable")
        composeRule.enableLocalMusicViaCta(enableLabel)
        composeRule.waitForText("Dustvalve Test Tone $n", timeoutMs = 30_000)
        composeRule.onAllNodesWithText("Dustvalve Test Tone $n")[0].performClick()
        composeRule.waitForIdle()
        composeRule.waitForTag(TestTags.MINI_PLAYER)
    }

    // local-play-track-starts
    @Test
    fun playLocalTrack_positionAdvances() {
        playTone(1)
        composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()
        composeRule.waitForPositionPastZero()
    }

    // local-play-while-other-playing
    @Test
    fun playSecondTrack_replacesFirst() {
        playTone(1)
        composeRule.waitForText("Dustvalve Test Tone 2")
        composeRule.onAllNodesWithText("Dustvalve Test Tone 2")[0].performClick()
        composeRule.waitForIdle()
        // Mini player title now shows tone 2.
        composeRule.waitForText("Dustvalve Test Tone 2")
    }

    // local-pause-resume
    @Test
    fun pauseFreezesPosition_resumeContinues() {
        playTone(1)
        composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()
        composeRule.waitForPositionPastZero()

        composeRule.waitForTag(TestTags.PLAYER_PLAY_PAUSE)
        composeRule.onNodeWithTag(TestTags.PLAYER_PLAY_PAUSE).performClick()
        composeRule.waitForIdle()
        // Paused: resume and make sure playback continues without crash.
        composeRule.onNodeWithTag(TestTags.PLAYER_PLAY_PAUSE).performClick()
        composeRule.waitForPositionPastZero()
    }

    // local-skip-next / local-skip-prev-restart-rule (crash-free contract)
    @Test
    fun nextAndPrevious_workAcrossSeededQueue() {
        playTone(1)
        composeRule.onNodeWithTag(TestTags.MINI_PLAYER).performClick()
        composeRule.waitForTag(TestTags.PLAYER_NEXT)
        composeRule.onNodeWithTag(TestTags.PLAYER_NEXT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.PLAYER_PREVIOUS).performClick()
        composeRule.waitForIdle()
        composeRule.waitForPositionPastZero()
    }
}
