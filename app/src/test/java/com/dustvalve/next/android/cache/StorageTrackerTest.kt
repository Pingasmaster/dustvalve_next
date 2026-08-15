@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.asset.StoragePaths
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.DownloadDao
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageTrackerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var downloadDao: DownloadDao
    private lateinit var settings: SettingsDataStore

    @get:Rule val tmp = TemporaryFolder()

    @Before fun setUp() {
        downloadDao = mockk()
        settings = mockk()
        // Start from clean cache dirs so directory sizes are deterministic.
        StoragePaths.imagesDir(context).listFiles()?.forEach { it.deleteRecursively() }
        StoragePaths.coversDir(context).listFiles()?.forEach { it.deleteRecursively() }
        StoragePaths.mediaCacheDir(context).deleteRecursively()
    }

    // Share runTest's scheduler so flowOn(ioDispatcher) doesn't mix schedulers.
    private fun TestScope.tracker() = StorageTracker(downloadDao, settings, context, UnconfinedTestDispatcher(testScheduler))

    private fun row(
        trackId: String,
        path: String,
        sizeBytes: Long,
        pinned: Boolean,
    ) = DownloadEntity(
        trackId = trackId,
        albumId = "al",
        filePath = path,
        sizeBytes = sizeBytes,
        pinned = pinned,
    )

    @Test fun `cache info splits pinned and unpinned sizes from on-disk files`() = runTest {
        val pinned = tmp.newFile("pinned.mp3").also { it.writeBytes(ByteArray(700)) }
        val unpinned = tmp.newFile("unpinned.mp3").also { it.writeBytes(ByteArray(300)) }
        coEvery { downloadDao.getAllSync() } returns listOf(
            row("p", pinned.absolutePath, sizeBytes = 9999, pinned = true),
            row("u", unpinned.absolutePath, sizeBytes = 9999, pinned = false),
        )
        every { settings.storageLimit } returns flowOf(10_000L)

        val info = tracker().getCacheInfo().first()

        assertThat(info.downloadSizeBytes).isEqualTo(700L)
        assertThat(info.audioSizeBytes).isEqualTo(300L) // unpinned + empty media cache
        assertThat(info.totalSizeBytes).isEqualTo(1000L)
        assertThat(info.limitBytes).isEqualTo(10_000L)
        assertThat(info.usagePercent).isWithin(0.01f).of(10f)
    }

    @Test fun `phantom sizeBytes do not inflate usage`() = runTest {
        coEvery { downloadDao.getAllSync() } returns listOf(
            row("ghost", "/missing/ghost.mp3", sizeBytes = 5_000_000L, pinned = true),
            row("blank", "", sizeBytes = 5_000_000L, pinned = false),
        )
        every { settings.storageLimit } returns flowOf(10_000L)

        val info = tracker().getCacheInfo().first()
        assertThat(info.totalSizeBytes).isEqualTo(0L)
        assertThat(info.downloadSizeBytes).isEqualTo(0L)
        assertThat(info.audioSizeBytes).isEqualTo(0L)
    }

    @Test fun `unlimited limit reports zero percent`() = runTest {
        val file = tmp.newFile("a.mp3").also { it.writeBytes(ByteArray(5_000)) }
        coEvery { downloadDao.getAllSync() } returns listOf(
            row("a", file.absolutePath, sizeBytes = 5_000L, pinned = false),
        )
        every { settings.storageLimit } returns flowOf(Long.MAX_VALUE)

        val info = tracker().getCacheInfo().first()
        assertThat(info.usagePercent).isEqualTo(0f)
    }

    @Test fun `usage percent is clamped at 100`() = runTest {
        val file = tmp.newFile("big.mp3").also { it.writeBytes(ByteArray(2_000)) }
        coEvery { downloadDao.getAllSync() } returns listOf(
            row("a", file.absolutePath, sizeBytes = 2_000L, pinned = false),
        )
        every { settings.storageLimit } returns flowOf(1_000L)

        val info = tracker().getCacheInfo().first()
        assertThat(info.usagePercent).isEqualTo(100f)
    }

    @Test fun `overage is zero within the limit`() = runTest {
        val file = tmp.newFile("small.mp3").also { it.writeBytes(ByteArray(500)) }
        coEvery { downloadDao.getAllSync() } returns listOf(
            row("a", file.absolutePath, sizeBytes = 500L, pinned = false),
        )
        coEvery { settings.getStorageLimitSync() } returns 1_000L
        assertThat(tracker().getOverageBytes()).isEqualTo(0L)
    }

    @Test fun `overage reports the excess beyond the limit`() = runTest {
        val file = tmp.newFile("over.mp3").also { it.writeBytes(ByteArray(1_500)) }
        coEvery { downloadDao.getAllSync() } returns listOf(
            row("a", file.absolutePath, sizeBytes = 1_500L, pinned = false),
        )
        coEvery { settings.getStorageLimitSync() } returns 1_000L
        assertThat(tracker().getOverageBytes()).isEqualTo(500L)
    }

    @Test fun `unlimited limit never reports overage`() = runTest {
        val file = tmp.newFile("huge.mp3").also { it.writeBytes(ByteArray(999)) }
        coEvery { downloadDao.getAllSync() } returns listOf(
            row("a", file.absolutePath, sizeBytes = 999_999L, pinned = false),
        )
        coEvery { settings.getStorageLimitSync() } returns Long.MAX_VALUE
        assertThat(tracker().getOverageBytes()).isEqualTo(0L)
    }
}
