package com.dustvalve.next.android.shippedsmoke

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.dustvalve.next.android"
private const val LAUNCH_TIMEOUT_MS = 60_000L
private const val UI_TIMEOUT_MS = 30_000L

/**
 * Smoke test for the APK as SHIPPED - :app's release variant with only
 * proguard-rules.pro applied (see this module's build.gradle.kts for why the
 * :app androidTest lane cannot cover this).
 *
 * Deliberately shallow. It is not trying to re-test behaviour the E2E suites
 * already cover; it is trying to catch the failure mode those suites are
 * structurally blind to - a missing keep rule that makes the real APK die on
 * launch or on first touching a reflective subsystem. Every assertion here
 * should hold for any healthy build, so a failure means R8 removed or renamed
 * something the shipped app needs.
 *
 * Addressing is by resource id, which Compose exposes from testTag because
 * MainActivity sets testTagsAsResourceId. Tags are string constants and are
 * not affected by obfuscation.
 */
@RunWith(AndroidJUnit4::class)
class ShippedReleaseSmokeTest {

    private lateinit var device: UiDevice

    /**
     * ONE test, one launch, walked end to end - not several tests each cold
     * starting the app. The first CI run proved a single launch works
     * (coldStart reached a composed nav bar); it was the SECOND test's
     * relaunch that never came back to the foreground. Re-launching between
     * tests adds flake surface and buys no coverage a smoke test wants, so
     * the whole path is asserted in sequence instead.
     */
    private fun launchApp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Pre-grant so a runtime permission dialog cannot sit on top of the
        // UI we are about to assert on. Tolerate failure: the permission may
        // already be granted, and that is not what this test is about.
        runCatching { device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS") }

        device.pressHome()
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        assertNotNull("no launch intent for $PACKAGE - is the release APK installed?", intent)
        context.startActivity(intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))

        assertTrue(
            "$PACKAGE never reached the foreground - the shipped APK most likely crashed on launch",
            device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), LAUNCH_TIMEOUT_MS),
        )
    }

    /**
     * Cold start reaches a composed nav bar, then Settings opens.
     *
     * The launch covers Application onCreate, the Hilt object graph and first
     * composition - the R8 casualties that abort before anything is drawn.
     * Opening Settings then drives the reflective subsystems that break under
     * R8 without a keep rule: the Hilt graph resolves SettingsDataStore,
     * which reads DataStore and decodes kotlinx-serialization payloads to
     * populate the screen.
     */
    @Test
    fun shippedApk_coldStarts_andOpensSettings() {
        launchApp()

        assertTrue(
            "bottom nav never rendered in the shipped APK",
            device.wait(Until.hasObject(By.res("bottom_nav")), UI_TIMEOUT_MS),
        )

        assertTrue(
            "settings tab never appeared",
            device.wait(Until.hasObject(By.res("bottom_nav_item_settings")), UI_TIMEOUT_MS),
        )
        device.findObject(By.res("bottom_nav_item_settings")).click()

        assertTrue(
            "settings list never rendered - a DataStore or serialization keep rule is likely missing",
            device.wait(Until.hasObject(By.res("settings_list")), UI_TIMEOUT_MS),
        )
        assertTrue(
            "$PACKAGE left the foreground while opening Settings",
            device.hasObject(By.pkg(PACKAGE).depth(0)),
        )
    }
}
