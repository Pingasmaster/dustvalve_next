package com.dustvalve.next.android.e2e

import android.Manifest
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dustvalve.next.android.MainActivity
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.testing.Flows.clickTab
import com.dustvalve.next.android.testing.Flows.waitForText
import com.dustvalve.next.android.testing.QuarantineRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier 3 wave 1: settings toggles write through to DataStore and stick.
 * Catalog ids: set-theme-dark, set-source-bandcamp-enable,
 * set-persist-all-toggles-survive-restart (theme subset).
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceE2eTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val quarantine = QuarantineRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun settings(): SettingsDataStore = SettingsDataStore(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    // set-theme-dark: tapping the Dark toggle persists theme_mode="dark" and
    // the app recomposes without crashing.
    @Test
    fun darkTheme_togglePersists() {
        composeRule.clickTab("settings")
        composeRule.waitForText("Dark")
        composeRule.onAllNodesWithText("Dark")[0].performClick()
        composeRule.waitForIdle()

        val mode = runBlocking { settings().themeMode.first() }
        assertThat(mode).isEqualTo("dark")
    }

    // set-source-bandcamp-enable: enabling the provider from Settings adds
    // the tab to the bottom nav.
    @Test
    fun enablingBandcamp_addsTab() {
        composeRule.clickTab("settings")
        composeRule.waitForText("Bandcamp")
        composeRule.onAllNodesWithText("Bandcamp")[0].performClick()
        composeRule.waitForIdle()

        val enabled = runBlocking { settings().bandcampEnabled.first() }
        assertThat(enabled).isTrue()
        composeRule.clickTab("bandcamp")
    }
}
