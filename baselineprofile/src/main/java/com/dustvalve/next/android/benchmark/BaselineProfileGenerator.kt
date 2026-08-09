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
     *
     * Kept to one iteration so the GMD can flush profiles before LMK; heavier
     * playback / Settings CUJs belong on a real device / macrobenchmark.
     */
    @Test
    fun generateBaselineProfile() {
        // Single iteration: GMD (x86_64 + LMK) often kills the app mid-flush
        // when maxIterations>1 piles RSS across cold starts.
        rule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 1,
            stableIterations = 1,
            includeInStartupProfile = true,
        ) {
            // 1. Cold start the main activity.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            // Brief settle for first composition + ProfileInstaller hooks.
            Thread.sleep(1_000)
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 5_000)

            // 2. One short scroll on any LazyColumn already on screen.
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                runCatching {
                    scrollable.scroll(Direction.DOWN, 0.4f)
                    device.waitForIdle()
                }
            }

            // Return to home for a clean end state before profile flush.
            pressHome()
        }
    }
}
