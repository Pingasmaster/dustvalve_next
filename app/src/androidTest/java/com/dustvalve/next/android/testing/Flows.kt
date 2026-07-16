package com.dustvalve.next.android.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.dustvalve.next.android.ui.TestTags

/** Shared drive-the-app helpers for smoke + E2E suites. */
object Flows {

    fun AndroidComposeTestRule<*, *>.waitForTag(tag: String, timeoutMs: Long = 15_000) {
        waitUntil(timeoutMs) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    fun AndroidComposeTestRule<*, *>.waitForText(text: String, timeoutMs: Long = 15_000) {
        waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    fun AndroidComposeTestRule<*, *>.clickTab(tab: String) {
        waitForTag(TestTags.bottomNavItem(tab))
        onNodeWithTag(TestTags.bottomNavItem(tab)).performClick()
        waitForIdle()
    }

    /**
     * Drives the REAL local-music enable flow: taps the Local tab's
     * "Enable local music" CTA (permission is pre-granted by the test rule),
     * which runs the app's own MediaStore scan - the only thing that pulls
     * the seeded tones into the DB. Merely setting the DataStore flag skips
     * the scan and leaves the library empty. No-op when the CTA is absent
     * (already enabled and scanned).
     */
    fun AndroidComposeTestRule<*, *>.enableLocalMusicViaCta(enableLabel: String) {
        clickTab("local")
        waitForIdle()
        val cta = onAllNodesWithText(enableLabel).fetchSemanticsNodes()
        if (cta.isNotEmpty()) {
            onAllNodesWithText(enableLabel)[0].performClick()
            waitForIdle()
        }
    }

    /**
     * Polls the full player's elapsed-time label until it moves off "0:00".
     * This is the pinned assertion for the v0.5.0 "stuck at 0:00" regression.
     */
    fun AndroidComposeTestRule<*, *>.waitForPositionPastZero(timeoutMs: Long = 15_000) {
        waitForTag(TestTags.PLAYER_POSITION)
        waitUntil(timeoutMs) {
            val node = onAllNodesWithTag(TestTags.PLAYER_POSITION).fetchSemanticsNodes().firstOrNull()
            val text = node?.config?.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { annotated -> annotated.text }
            text != null && text != "0:00" && text != "--:--"
        }
        onNodeWithTag(TestTags.PLAYER_POSITION).assertIsDisplayed()
    }
}
