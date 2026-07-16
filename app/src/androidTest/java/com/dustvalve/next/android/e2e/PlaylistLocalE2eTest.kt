package com.dustvalve.next.android.e2e

import android.Manifest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.enableLocalMusicViaCta
import com.dustvalve.next.android.testing.Flows.waitForText
import com.dustvalve.next.android.testing.LocalMusicSeeder
import com.dustvalve.next.android.testing.QuarantineRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 3 wave 1: playlist workflows with a LOCAL track driven entirely
 * through the UI. Catalog ids: local-playlist-create-via-sheet,
 * local-playlist-detail-lists-tracks. (Bandcamp/YTM playlist variants live
 * in the @LiveNetwork wave.)
 */
@RunWith(AndroidJUnit4::class)
class PlaylistLocalE2eTest {

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

    @After fun cleanup() {
        LocalMusicSeeder.cleanup()
    }

    // local-playlist-create-via-sheet: long-press track -> Add to playlist ->
    // create new named playlist -> appears in Library with the track inside.
    @Test
    fun createPlaylistFromLocalTrack_visibleInLibrary() {
        val enableLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(com.dustvalve.next.android.R.string.local_enable)
        composeRule.enableLocalMusicViaCta(enableLabel)
        composeRule.waitForText("Dustvalve Test Tone 1", timeoutMs = 30_000)
        composeRule.onAllNodesWithText("Dustvalve Test Tone 1")[0]
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.waitForText("Add to playlist")
        composeRule.onAllNodesWithText("Add to playlist")[0].performClick()
        composeRule.waitForIdle()

        // AddToPlaylistSheet -> create-new (ic_add with cd "Create playlist").
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("Create playlist").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("Create playlist")[0].performClick()
        composeRule.waitForIdle()

        // PlaylistEditSheet: type the name, confirm with Create.
        composeRule.waitForText("Name")
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("E2E Playlist")
        composeRule.waitForText("Create")
        composeRule.onAllNodesWithText("Create")[0].performClick()
        composeRule.waitForIdle()

        // Library lists the new playlist; opening it shows the track.
        composeRule.clickTab("library")
        composeRule.waitForText("E2E Playlist")
        composeRule.onAllNodesWithText("E2E Playlist")[0].performClick()
        composeRule.waitForText("Dustvalve Test Tone 1")
    }
}
