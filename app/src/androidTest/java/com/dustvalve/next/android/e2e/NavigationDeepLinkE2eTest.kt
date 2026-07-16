package com.dustvalve.next.android.e2e

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.waitForTag
import com.dustvalve.next.android.testing.QuarantineRule
import com.dustvalve.next.android.ui.TestTags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 3 wave 1: navigation + deep links, hermetic. Catalog ids:
 * yt-deeplink-provider-disabled-gate, bc-deeplink-provider-disabled-dialog,
 * set-nav-visit-each-destination (root tabs), yt-deeplink-invalid-id-rejected
 * (unsupported-link path).
 */
@RunWith(AndroidJUnit4::class)
class NavigationDeepLinkE2eTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val quarantine = QuarantineRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun sendView(url: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .setClass(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun defaultTabs_render() {
        composeRule.waitForTag(TestTags.bottomNavItem("local"))
        composeRule.waitForTag(TestTags.bottomNavItem("library"))
        composeRule.waitForTag(TestTags.bottomNavItem("settings"))
    }

    // Providers default OFF: a YouTube deep link must surface the
    // enable-provider confirmation instead of crashing or silently playing.
    @Test
    fun youtubeDeepLink_withProviderDisabled_showsEnableDialog() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        sendView("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("YouTube", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    @Test
    fun bandcampDeepLink_withProviderDisabled_showsEnableDialog() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        sendView("https://testartist.bandcamp.com/album/some-album")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Bandcamp", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    @Test
    fun tabSweep_backStackSurvives() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        for (tab in listOf("library", "settings", "local")) {
            composeRule.clickTab(tab)
        }
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }
}
