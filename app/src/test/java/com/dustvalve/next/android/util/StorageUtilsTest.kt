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

    @Test fun `bytesToMB basic conversion`() {
        assertThat(StorageUtils.bytesToMB(1024L * 1024L)).isEqualTo(1f)
        assertThat(StorageUtils.bytesToMB(0L)).isEqualTo(0f)
    }

    @Test fun `mbToBytes basic conversion`() {
        assertThat(StorageUtils.mbToBytes(1f)).isEqualTo(1024L * 1024L)
        assertThat(StorageUtils.mbToBytes(0f)).isEqualTo(0L)
    }

    @Test fun `gbToBytes basic conversion`() {
        assertThat(StorageUtils.gbToBytes(1f)).isEqualTo(1024L * 1024L * 1024L)
    }

    @Test fun `mb round trip`() {
        val mb = StorageUtils.mbToBytes(5f)
        assertThat(StorageUtils.bytesToMB(mb)).isEqualTo(5f)
    }
}
