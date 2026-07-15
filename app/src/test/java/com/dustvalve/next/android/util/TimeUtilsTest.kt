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

    @Test fun `formatDuration never groups long minute counts`() {
        // 100_000 s = 1666:40 — grouping separators (1,666 / 1.666) would
        // break the timer format in locales that group thousands.
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertThat(TimeUtils.formatDuration(100_000f)).isEqualTo("1666:40")
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
