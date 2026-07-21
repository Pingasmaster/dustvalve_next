package com.dustvalve.next.android.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** The app's real settings store, addressed from the instrumentation process. */
fun testSettings(): SettingsDataStore = SettingsDataStore(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

/**
 * Forces the provider flags to a known state before the activity launches.
 *
 * Provider flags live in DataStore, which survives between tests in the same
 * install, and the release lane runs the WHOLE suite in one unfiltered
 * instrumentation pass (the debug lanes split it by @LiveNetwork, so they
 * never see the interaction). That makes provider state leak across classes:
 * LiveProvidersE2eTest turns both providers on, and any later test that
 * assumed them off silently tested something else. It cost a full CI
 * investigation once - SettingsPersistenceE2eTest's "enable Bandcamp" tap
 * landed on an already-enabled switch and DISABLED it, so the tab under
 * assertion disappeared and the failure looked like a release-only
 * minification bug.
 *
 * Convention: any E2E class whose behaviour depends on provider state
 * declares that state here rather than inheriting whatever ran before it.
 * Ordered ahead of composeRule by callers so MainActivity starts from the
 * declared state instead of racing a write against a live nav bar.
 */
class ProviderStateRule(
    private val bandcamp: Boolean = false,
    private val youtube: Boolean = false,
) : ExternalResource() {
    override fun before() {
        runBlocking {
            testSettings().setBandcampEnabled(bandcamp)
            testSettings().setYoutubeEnabled(youtube)
        }
    }
}
