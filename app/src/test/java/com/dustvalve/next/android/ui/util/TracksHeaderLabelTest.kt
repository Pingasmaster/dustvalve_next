package com.dustvalve.next.android.ui.util

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders [tracksHeaderLabel] in a Compose harness so the resolved string
 * goes through the actual plurals / format chain (which differs slightly
 * between %1 and %1$s formatting on Android resources).
 */
@RunWith(AndroidJUnit4::class)
class TracksHeaderLabelTest {

    @get:Rule val rule = createComposeRule()

    private fun resolve(count: Int, durationSec: Long): String {
        var captured = ""
        rule.setContent { captured = tracksHeaderLabel(count, durationSec) }
        rule.waitForIdle()
        return captured
    }

    @Test fun `count and duration both render with centred dot separator`() {
        val s = resolve(count = 5, durationSec = 23 * 60L)
        assertThat(s).contains("5 tracks")
        assertThat(s).contains("23 min")
        assertThat(s).contains(" · ")
    }

    @Test fun `singular track count uses one form`() {
        val s = resolve(count = 1, durationSec = 4 * 60L)
        assertThat(s).contains("1 track ")
        assertThat(s).doesNotContain("1 tracks")
    }

    @Test fun `duration over an hour uses hr+min`() {
        val s = resolve(count = 12, durationSec = 75 * 60L)
        // 1 hr 15 min
        assertThat(s).contains("1 hr 15 min")
    }

    @Test fun `duration exactly on the hour drops trailing min`() {
        val s = resolve(count = 10, durationSec = 60 * 60L)
        assertThat(s).contains("1 hr")
        assertThat(s).doesNotContain("1 hr 0 min")
    }

    @Test fun `sub-minute total floors to 1 min so dot pattern stays uniform`() {
        val s = resolve(count = 1, durationSec = 30L)
        assertThat(s).contains(" · ")
        assertThat(s).contains("1 min")
    }

    @Test fun `zero duration drops the dot+duration suffix`() {
        val s = resolve(count = 7, durationSec = 0L)
        assertThat(s).contains("7 tracks")
        assertThat(s).doesNotContain(" · ")
    }

    @Test fun `zero count falls back to plain Tracks label`() {
        val s = resolve(count = 0, durationSec = 0L)
        assertThat(s).isEqualTo("Tracks")
    }
}
