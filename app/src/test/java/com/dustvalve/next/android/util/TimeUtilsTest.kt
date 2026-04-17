package com.dustvalve.next.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeUtilsTest {

    @Test fun `formatDuration handles zero`() {
        assertThat(TimeUtils.formatDuration(0f)).isEqualTo("0:00")
    }

    @Test fun `formatDuration handles negative as zero`() {
        assertThat(TimeUtils.formatDuration(-5f)).isEqualTo("0:00")
    }

    @Test fun `formatDuration under a minute pads seconds`() {
        assertThat(TimeUtils.formatDuration(5f)).isEqualTo("0:05")
        assertThat(TimeUtils.formatDuration(59f)).isEqualTo("0:59")
    }

    @Test fun `formatDuration at minute boundary`() {
        assertThat(TimeUtils.formatDuration(60f)).isEqualTo("1:00")
    }

    @Test fun `formatDuration multi minute examples`() {
        assertThat(TimeUtils.formatDuration(225f)).isEqualTo("3:45")
        assertThat(TimeUtils.formatDuration(3599f)).isEqualTo("59:59")
    }

    @Test fun `formatDuration past an hour`() {
        assertThat(TimeUtils.formatDuration(3600f)).isEqualTo("60:00")
        assertThat(TimeUtils.formatDuration(3661f)).isEqualTo("61:01")
    }

    @Test fun `formatDuration truncates fractional seconds`() {
        assertThat(TimeUtils.formatDuration(59.9f)).isEqualTo("0:59")
    }

    @Test fun `formatDuration handles NaN as zero`() {
        assertThat(TimeUtils.formatDuration(Float.NaN)).isEqualTo("0:00")
    }

    @Test fun `formatDurationMs handles zero`() {
        assertThat(TimeUtils.formatDurationMs(0L)).isEqualTo("0:00")
    }

    @Test fun `formatDurationMs handles negative as zero`() {
        assertThat(TimeUtils.formatDurationMs(-5000L)).isEqualTo("0:00")
    }

    @Test fun `formatDurationMs known values`() {
        assertThat(TimeUtils.formatDurationMs(225_000L)).isEqualTo("3:45")
        assertThat(TimeUtils.formatDurationMs(999L)).isEqualTo("0:00")
        assertThat(TimeUtils.formatDurationMs(1_000L)).isEqualTo("0:01")
    }

    @Test fun `formatRelativeTime just now for recent`() {
        val now = System.currentTimeMillis()
        assertThat(TimeUtils.formatRelativeTime(now)).isEqualTo("just now")
        assertThat(TimeUtils.formatRelativeTime(now - 59_000L)).isEqualTo("just now")
    }

    @Test fun `formatRelativeTime future timestamp shows just now`() {
        val future = System.currentTimeMillis() + 60_000L
        assertThat(TimeUtils.formatRelativeTime(future)).isEqualTo("just now")
    }

    @Test fun `formatRelativeTime minutes`() {
        val now = System.currentTimeMillis()
        assertThat(TimeUtils.formatRelativeTime(now - 60_000L)).isEqualTo("1m ago")
        assertThat(TimeUtils.formatRelativeTime(now - 59L * 60_000L)).isEqualTo("59m ago")
    }

    @Test fun `formatRelativeTime hours`() {
        val now = System.currentTimeMillis()
        assertThat(TimeUtils.formatRelativeTime(now - 60L * 60_000L)).isEqualTo("1h ago")
        assertThat(TimeUtils.formatRelativeTime(now - 23L * 60L * 60_000L)).isEqualTo("23h ago")
    }

    @Test fun `formatRelativeTime days`() {
        val now = System.currentTimeMillis()
        assertThat(TimeUtils.formatRelativeTime(now - 24L * 60L * 60_000L)).isEqualTo("1d ago")
        assertThat(TimeUtils.formatRelativeTime(now - 6L * 24L * 60L * 60_000L)).isEqualTo("6d ago")
    }

    @Test fun `formatRelativeTime weeks`() {
        val now = System.currentTimeMillis()
        assertThat(TimeUtils.formatRelativeTime(now - 7L * 24L * 60L * 60_000L)).isEqualTo("1w ago")
        assertThat(TimeUtils.formatRelativeTime(now - 29L * 24L * 60L * 60_000L)).isEqualTo("4w ago")
    }

    @Test fun `formatRelativeTime months`() {
        val now = System.currentTimeMillis()
        assertThat(TimeUtils.formatRelativeTime(now - 30L * 24L * 60L * 60_000L)).isEqualTo("1mo ago")
        assertThat(TimeUtils.formatRelativeTime(now - 364L * 24L * 60L * 60_000L)).isEqualTo("12mo ago")
    }

    @Test fun `formatRelativeTime years`() {
        val now = System.currentTimeMillis()
        assertThat(TimeUtils.formatRelativeTime(now - 365L * 24L * 60L * 60_000L)).isEqualTo("1y ago")
        assertThat(TimeUtils.formatRelativeTime(now - 730L * 24L * 60L * 60_000L)).isEqualTo("2y ago")
    }
}
