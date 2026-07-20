package com.dustvalve.next.android.e2e

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.waitForTag
import com.dustvalve.next.android.testing.ProviderStateRule
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

    /**
     * Both providers MUST start disabled - the two enable-dialog tests below
     * assert the dialog a link raises for a DISABLED provider. Earlier
     * classes in the unfiltered release pass turn these on; see
     * [ProviderStateRule].
     */
    @get:Rule(order = 2)
    val providerState = ProviderStateRule(bandcamp = false, youtube = false)

    @get:Rule(order = 3)
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

    // Providers are forced OFF by providerState: a YouTube deep link must
    // surface the enable-provider confirmation instead of crashing or
    // silently playing.
    //
    // Assert the DIALOG's tag, not its text. The provider name appears both
    // in the dialog and on the provider screen the link opens when the
    // provider is already enabled, so a substring assertion passes in either
    // state - which is exactly how leaked provider state stayed invisible
    // here while it broke SettingsPersistenceE2eTest.
    @Test
    fun youtubeDeepLink_withProviderDisabled_showsEnableDialog() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        sendView("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        composeRule.waitForTag(TestTags.PROVIDER_ENABLE_DIALOG, timeoutMs = 10_000)
        composeRule.onNodeWithTag(TestTags.PROVIDER_ENABLE_DIALOG).assertIsDisplayed()
        assertThat(composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.RESUMED)).isTrue()
    }

    @Test
    fun bandcampDeepLink_withProviderDisabled_showsEnableDialog() {
        composeRule.waitForTag(TestTags.BOTTOM_NAV)
        sendView("https://testartist.bandcamp.com/album/some-album")
        composeRule.waitForTag(TestTags.PROVIDER_ENABLE_DIALOG, timeoutMs = 10_000)
        composeRule.onNodeWithTag(TestTags.PROVIDER_ENABLE_DIALOG).assertIsDisplayed()
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
