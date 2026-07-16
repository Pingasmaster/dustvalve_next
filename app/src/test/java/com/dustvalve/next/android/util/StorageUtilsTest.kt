package com.dustvalve.next.android.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * StorageUtils delegates to the platform's Formatter (SI units, locale-aware
 * digits and unit names), so assertions pin the locale via resource qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], qualifiers = "en-rUS")
class StorageUtilsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `formatFileSize zero`() {
        assertThat(StorageUtils.formatFileSize(context, 0L)).isEqualTo("0 B")
    }

    @Test fun `formatFileSize negative clamps to zero`() {
        assertThat(StorageUtils.formatFileSize(context, -100L)).isEqualTo("0 B")
    }

    @Test fun `formatFileSize bytes`() {
        assertThat(StorageUtils.formatFileSize(context, 1L)).isEqualTo("1 B")
        assertThat(StorageUtils.formatFileSize(context, 899L)).isEqualTo("899 B")
    }

    @Test fun `formatFileSize kilobytes`() {
        assertThat(StorageUtils.formatFileSize(context, 1_000L)).isEqualTo("1.00 kB")
        assertThat(StorageUtils.formatFileSize(context, 1_500L)).isEqualTo("1.50 kB")
    }

    @Test fun `formatFileSize megabytes and gigabytes`() {
        assertThat(StorageUtils.formatFileSize(context, 1_000_000L)).isEqualTo("1.00 MB")
        assertThat(StorageUtils.formatFileSize(context, 2_500_000_000L)).isEqualTo("2.50 GB")
    }

    @Test fun `formatFileSize huge values`() {
        val result = StorageUtils.formatFileSize(context, Long.MAX_VALUE)
        assertThat(result).isNotEmpty()
    }

    @Test
    @Config(qualifiers = "de")
    fun `formatFileSize uses locale decimal separator`() {
        assertThat(StorageUtils.formatFileSize(context, 1_500L)).isEqualTo("1,50 kB")
    }
}
