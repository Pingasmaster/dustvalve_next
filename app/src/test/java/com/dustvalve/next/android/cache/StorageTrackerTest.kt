@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageTrackerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var downloadDao: DownloadDao
    private lateinit var settings: SettingsDataStore

    @Before fun setUp() {
        downloadDao = mockk()
        settings = mockk()
        // Start from clean cache dirs so directory sizes are deterministic.
        StoragePaths.imagesDir(context).listFiles()?.forEach { it.deleteRecursively() }
        StoragePaths.mediaCacheDir(context).deleteRecursively()
    }

    // Share runTest's scheduler so flowOn(ioDispatcher) doesn't mix schedulers.
    private fun TestScope.tracker() = StorageTracker(downloadDao, settings, context, UnconfinedTestDispatcher(testScheduler))

    @Test fun `cache info splits pinned and unpinned sizes`() = runTest {
        coEvery { downloadDao.getPinnedSize() } returns 700L
        coEvery { downloadDao.getTotalSize() } returns 1000L
        every { settings.storageLimit } returns flowOf(10_000L)

        val info = tracker().getCacheInfo().first()

        assertThat(info.downloadSizeBytes).isEqualTo(700L)
        assertThat(info.audioSizeBytes).isEqualTo(300L) // unpinned + empty media cache
        assertThat(info.totalSizeBytes).isEqualTo(1000L)
        assertThat(info.limitBytes).isEqualTo(10_000L)
        assertThat(info.usagePercent).isWithin(0.01f).of(10f)
    }

    @Test fun `unlimited limit reports zero percent`() = runTest {
        coEvery { downloadDao.getPinnedSize() } returns 0L
        coEvery { downloadDao.getTotalSize() } returns 5_000L
        every { settings.storageLimit } returns flowOf(Long.MAX_VALUE)

        val info = tracker().getCacheInfo().first()
        assertThat(info.usagePercent).isEqualTo(0f)
    }

    @Test fun `usage percent is clamped at 100`() = runTest {
        coEvery { downloadDao.getPinnedSize() } returns 0L
        coEvery { downloadDao.getTotalSize() } returns 2_000L
        every { settings.storageLimit } returns flowOf(1_000L)

        val info = tracker().getCacheInfo().first()
        assertThat(info.usagePercent).isEqualTo(100f)
    }

    @Test fun `overage is zero within the limit`() = runTest {
        coEvery { downloadDao.getTotalSize() } returns 500L
        coEvery { settings.getStorageLimitSync() } returns 1_000L
        assertThat(tracker().getOverageBytes()).isEqualTo(0L)
    }

    @Test fun `overage reports the excess beyond the limit`() = runTest {
        coEvery { downloadDao.getTotalSize() } returns 1_500L
        coEvery { settings.getStorageLimitSync() } returns 1_000L
        assertThat(tracker().getOverageBytes()).isEqualTo(500L)
    }

    @Test fun `unlimited limit never reports overage`() = runTest {
        coEvery { downloadDao.getTotalSize() } returns 999_999L
        coEvery { settings.getStorageLimitSync() } returns Long.MAX_VALUE
        assertThat(tracker().getOverageBytes()).isEqualTo(0L)
    }
}
