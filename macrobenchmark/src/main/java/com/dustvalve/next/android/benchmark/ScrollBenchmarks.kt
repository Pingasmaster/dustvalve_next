/*
 * Scroll benchmarks for Dustvalve Next.
 *
 * Measures the frame timing of the main library LazyColumn (the heaviest
 * scrolling surface in the app), with and without the baseline profile.
 *
 * Run from the project root with:
 *
 *   ./gradlew :macrobenchmark:pixel6Api33AndroidTest
 */
package com.dustvalve.next.android.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.dustvalve.next.android"

@RunWith(AndroidJUnit4::class)
class ScrollBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollNone() = scroll(CompilationMode.None())

    @Test
    fun scrollBaselineProfile() =
        scroll(
            CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        )

    private fun scroll(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
        ) {
            // Cold start the main activity, then wait until the main UI
            // is ready.
            startActivityAndWait()
            device.waitForIdle()
            device.wait(
                Until.gone(By.res("$PACKAGE_NAME:id/progress_bar_loader")),
                5_000,
            )

            // Find the main scrollable surface (the library tab LazyColumn)
            // and fling through it. UiAutomator's scroll API performs a
            // real fling, which is exactly the gesture we want to time.
            val scrollable = By.scrollable(true)
            check(device.hasObject(scrollable)) {
                "Expected at least one scrollable surface in the library tab."
            }
            val surface = device.findObject(scrollable)
            repeat(times = 8) { surface.scroll(Direction.DOWN, 1.0f) }
            repeat(times = 8) { surface.scroll(Direction.UP, 1.0f) }
        }
    }
}
