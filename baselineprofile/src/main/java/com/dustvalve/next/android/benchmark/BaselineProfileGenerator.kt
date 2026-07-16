/*
 * Baseline profile generator for Dustvalve Next.
 *
 * Collects the critical-user-journey (CUJ) hot paths so the ART ahead-of-time
 * compiler can pre-compile them and we get a faster cold start and smoother
 * first-time use of the app. Profile is regenerated automatically as part of
 * the release build (`baselineProfile { automaticGenerationDuringBuild = true }`)
 * and committed via the `.github/workflows/baseline-profile.yml` job.
 */
package com.dustvalve.next.android.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.dustvalve.next.android"

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * Walks the most-frequent user flows of Dustvalve Next and records
     * which code paths the JIT touches. Those classes/methods get baked
     * into the baseline-prof.txt shipped with the release APK and the
     * AOT compiler will pre-compile them on first boot.
     *
     * CUJs covered:
     * - cold_start_mainactivity
     * - warm_resume_playback
     * - scroll_library_tab
     * - navigate_to_settings_about
     * - trigger_search_for_updates
     */
    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 5,
            stableIterations = 3,
        ) {
            // 1. Cold start the main activity (the realistic first-launch flow
            //    triggers AppUpdateController.checkSilently() from
            //    DustvalveNextApplication.onCreate - do not stub it).
            pressHome()
            startActivityAndWait()

            // Wait for the main UI to settle.
            device.waitForIdle()
            device.wait(
                Until.gone(By.res("$PACKAGE_NAME:id/progress_bar_loader")),
                5_000,
            )

            // 2. Scroll the library tab - the heaviest LazyColumn in the app.
            val scrollable = By.scrollable(true)
            if (device.hasObject(scrollable)) {
                val surface = device.findObject(scrollable)
                repeat(times = 3) { surface.scroll(Direction.DOWN, 1.0f) }
                repeat(times = 3) { surface.scroll(Direction.UP, 1.0f) }
            }

            // 3. Warm-resume playback - start the PlaybackService via the
            //    "play" affordance on the first library row.
            runCatching {
                val firstRow = By.res("$PACKAGE_NAME:id/playlist_more_options")
                if (device.hasObject(firstRow)) {
                    device.findObject(firstRow).click()
                    device.wait(Until.hasObject(By.text("Play")), 1_000)
                    runCatching { device.findObject(By.text("Play")).click() }
                    device.pressBack()
                }
            }

            // 4. Navigate to Settings -> About.
            val settingsEntry = By.text("Settings")
            if (device.hasObject(settingsEntry)) {
                device.findObject(settingsEntry).click()
                device.waitForIdle()
                val settingsScroll = By.scrollable(true)
                if (device.hasObject(settingsScroll)) {
                    val surface = device.findObject(settingsScroll)
                    repeat(times = 3) { surface.scroll(Direction.DOWN, 1.0f) }
                }
            }

            // 5. Trigger "Search for updates" in Settings -> About to exercise
            //    the AppUpdateController.checkManually() flow and the
            //    GitHub Releases IO/parsing path.
            runCatching {
                val about = By.text("About")
                if (device.hasObject(about)) {
                    device.findObject(about).click()
                    device.waitForIdle()
                }
                val check = By.text("Search for updates")
                if (device.hasObject(check)) {
                    device.findObject(check).click()
                    device.waitForIdle()
                    // Let the GitHub Releases request complete.
                    Thread.sleep(2_000)
                }
            }

            // Return to home for a clean iteration end state.
            pressHome()
        }
    }
}
