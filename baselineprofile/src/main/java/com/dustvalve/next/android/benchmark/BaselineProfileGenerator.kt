/*
 * Baseline profile generator for Dustvalve Next.
 *
 * Collects the critical-user-journey (CUJ) hot paths so the ART ahead-of-time
 * compiler can pre-compile them and we get a faster cold start and smoother
 * first-time use of the app.
 *
 * Default ./build.sh regenerates via:
 *
 *   ./gradlew :baselineprofile:pixel7aApi37FutureNonMinifiedReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile
 *
 * then scripts/install_baseline_profiles.sh copies baseline-prof.txt /
 * startup-prof.txt into app/src/release/ (AGP packages
 * src/<sourceSet>/baseline-prof.txt into the APK as assets/dexopt/baseline.prof).
 *
 * Target application id must be hardcoded: with
 * android.experimental.self-instrumenting=true, targetContext.packageName is
 * the *test* APK (com.dustvalve.next.android.baselineprofile), and passing
 * that to BaselineProfileRule.collect force-stops the instrumentation process.
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
     * - scroll_library_tab
     * - navigate_to_settings
     *
     * Kept lean so the GMD (arm64-via-translation) can flush profiles before
     * LMK; heavier playback CUJs belong on a real device / macrobenchmark.
     */
    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 3,
            stableIterations = 2,
            includeInStartupProfile = true,
        ) {
            // 1. Cold start the main activity.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            // Give first composition + ProfileInstaller hooks time to settle
            // before the rule kills the process to flush profiles.
            Thread.sleep(2_000)
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 5_000)

            // 2. Scroll any LazyColumn that is already on screen.
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                runCatching {
                    scrollable.scroll(Direction.DOWN, 0.5f)
                    device.waitForIdle()
                    scrollable.scroll(Direction.UP, 0.5f)
                    device.waitForIdle()
                }
            }

            // 3. Navigate to Settings if the tab is visible.
            runCatching {
                val settingsEntry = By.text("Settings")
                if (device.hasObject(settingsEntry)) {
                    device.findObject(settingsEntry).click()
                    device.waitForIdle()
                    val settingsScroll = device.findObject(By.scrollable(true))
                    if (settingsScroll != null) {
                        settingsScroll.scroll(Direction.DOWN, 0.5f)
                        device.waitForIdle()
                    }
                }
            }

            // Return to home for a clean iteration end state.
            pressHome()
        }
    }
}
