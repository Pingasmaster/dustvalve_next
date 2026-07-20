package com.dustvalve.next.android.e2e

import android.Manifest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.waitForTag
import com.dustvalve.next.android.testing.QuarantineRule
import com.dustvalve.next.android.ui.TestTags
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Tier 3 wave 1: settings toggles write through to DataStore and stick.
 * Catalog ids: set-theme-dark, set-source-bandcamp-enable.
 * The settings screen is a LazyColumn: rows below the fold are not composed
 * until scrolled to, so every interaction scrolls first.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceE2eTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val quarantine = QuarantineRule()

    /**
     * Provider flags live in DataStore, which survives between tests in the
     * same install. The release lane runs the WHOLE suite in one unfiltered
     * instrumentation pass, so LiveProvidersE2eTest's @Before
     * (setBandcampEnabled(true)) and AppSmokeTest's per-test enables leak in
     * here. Starting with Bandcamp already on, the tap in
     * enablingBandcamp_addsTab TOGGLES IT OFF: the tab disappears and the
     * test times out waiting for it. The debug lanes never caught this
     * because their hermetic pass filters LiveProvidersE2eTest out
     * (notAnnotation=LiveNetwork), so nothing enabled Bandcamp first.
     *
     * Ordered ahead of composeRule so MainActivity launches from the known
     * state rather than racing a write against a live nav bar.
     */
    @get:Rule(order = 2)
    val providersReset: ExternalResource = object : ExternalResource() {
        override fun before() {
            runBlocking { settings().setBandcampEnabled(false) }
        }
    }

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun settings(): SettingsDataStore = SettingsDataStore(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    // set-theme-dark: tapping the Dark toggle persists theme_mode="dark" and
    // the app recomposes without crashing.
    @Test
    fun darkTheme_togglePersists() {
        val dark = com.dustvalve.next.android.testing.Flows.appString("settings_theme_dark")
        composeRule.clickTab("settings")
        composeRule.waitForTag(TestTags.SETTINGS_LIST)
        composeRule.onNodeWithTag(TestTags.SETTINGS_LIST)
            .performScrollToNode(hasText(dark))
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(dark)[0].performClick()

        composeRule.waitUntil(10_000) {
            runBlocking { settings().themeMode.first() } == "dark"
        }
        assertThat(runBlocking { settings().themeMode.first() }).isEqualTo("dark")
    }

    // set-source-bandcamp-enable: flipping the tagged source switch persists
    // and adds the tab to the bottom nav.
    @Test
    fun enablingBandcamp_addsTab() {
        composeRule.clickTab("settings")
        composeRule.waitForTag(TestTags.SETTINGS_LIST)
        composeRule.onNodeWithTag(TestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(TestTags.settingsSwitch("bandcamp")))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.settingsSwitch("bandcamp")).performClick()

        composeRule.waitUntil(10_000) {
            runBlocking { settings().bandcampEnabled.first() }
        }
        assertThat(runBlocking { settings().bandcampEnabled.first() }).isTrue()
        composeRule.clickTab("bandcamp")
    }
}
