package com.dustvalve.next.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class StorageUtilsTest {

    private var originalLocale: Locale = Locale.getDefault()

    // Force a known locale so "%.1f" decimal separator is predictable.
    @Before fun setUs() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After fun restore() {
        Locale.setDefault(originalLocale)
    }

    @Test fun `formatFileSize zero`() {
        assertThat(StorageUtils.formatFileSize(0L)).isEqualTo("0 B")
    }

    @Test fun `formatFileSize negative clamps to zero`() {
        assertThat(StorageUtils.formatFileSize(-100L)).isEqualTo("0 B")
    }

    @Test fun `formatFileSize under 1KB`() {
        assertThat(StorageUtils.formatFileSize(1L)).isEqualTo("1 B")
        assertThat(StorageUtils.formatFileSize(1023L)).isEqualTo("1023 B")
    }

    @Test fun `formatFileSize KB`() {
        assertThat(StorageUtils.formatFileSize(1024L)).isEqualTo("1.0 KB")
        assertThat(StorageUtils.formatFileSize(1024L * 1024L - 1L)).isEqualTo("1024.0 KB")
    }

    @Test fun `formatFileSize MB`() {
        assertThat(StorageUtils.formatFileSize(1024L * 1024L)).isEqualTo("1.0 MB")
        assertThat(StorageUtils.formatFileSize(1024L * 1024L * 1024L - 1L)).isEqualTo("1024.0 MB")
    }

    @Test fun `formatFileSize GB`() {
        assertThat(StorageUtils.formatFileSize(1024L * 1024L * 1024L)).isEqualTo("1.0 GB")
    }

    @Test fun `formatFileSize huge values`() {
        val result = StorageUtils.formatFileSize(Long.MAX_VALUE)
        assertThat(result).endsWith("GB")
    }
}
