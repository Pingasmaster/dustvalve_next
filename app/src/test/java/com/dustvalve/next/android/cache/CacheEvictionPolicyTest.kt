package com.dustvalve.next.android.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.entity.CacheEntryEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheEvictionPolicyTest {

    private lateinit var database: DustvalveNextDatabase
    private lateinit var diskCache: DiskCacheManager
    private lateinit var settings: SettingsDataStore
    private lateinit var policy: CacheEvictionPolicy

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(ctx, DustvalveNextDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        diskCache = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        policy = CacheEvictionPolicy(database, database.cacheEntryDao(), diskCache, settings)
    }

    @After fun tearDown() {
        database.close()
    }

    private fun entry(
        key: String, type: String, size: Long,
        access: Long, isDownload: Boolean = false,
        path: String? = "/tmp/$key"
    ) = CacheEntryEntity(key, type, size, access, isDownload, path)

    @Test fun `evict zero or negative is no-op`() = runTest {
        database.cacheEntryDao().insert(entry("a", "audio", 100, 1L))
        policy.evict(0L)
        policy.evict(-10L)
        assertThat(database.cacheEntryDao().getAll()).hasSize(1)
    }

    @Test fun `evict removes audio first in lastAccessed order`() = runTest {
        val dao = database.cacheEntryDao()
        dao.insert(entry("a_old", "audio", 100, 1L))
        dao.insert(entry("a_new", "audio", 100, 5L))
        dao.insert(entry("i_old", "image", 100, 2L))
        every { diskCache.deleteFile(any()) } returns true

        policy.evict(150L)

        val remaining = dao.getAll().map { it.key }.toSet()
        assertThat(remaining).doesNotContain("a_old")
        assertThat(remaining).doesNotContain("a_new")
        assertThat(remaining).contains("i_old")
    }

    @Test fun `evict falls through to images then metadata`() = runTest {
        val dao = database.cacheEntryDao()
        dao.insert(entry("audio1", "audio", 100, 1L))
        dao.insert(entry("img1", "image", 200, 2L))
        dao.insert(entry("img2", "image", 200, 3L))
        dao.insert(entry("meta1", "metadata", 500, 4L))
        every { diskCache.deleteFile(any()) } returns true

        policy.evict(700L)

        val remaining = dao.getAll().map { it.key }
        assertThat(remaining).isEmpty() // all four deleted to reach 700 bytes
    }

    @Test fun `evict stops once target is met`() = runTest {
        val dao = database.cacheEntryDao()
        dao.insert(entry("audio1", "audio", 1000, 1L))
        dao.insert(entry("audio2", "audio", 1000, 2L))
        dao.insert(entry("audio3", "audio", 1000, 3L))
        every { diskCache.deleteFile(any()) } returns true

        policy.evict(1L)

        assertThat(dao.getAll().map { it.key }).containsExactly("audio2", "audio3")
    }

    @Test fun `evict never touches user downloads`() = runTest {
        val dao = database.cacheEntryDao()
        dao.insert(entry("dl", "audio", 10_000L, access = 1L, isDownload = true))
        // No non-download candidates available; evict must not delete the download.
        policy.evict(5_000L)
        assertThat(dao.getAll().map { it.key }).containsExactly("dl")
    }

    @Test fun `evict with no candidates is harmless`() = runTest {
        policy.evict(1000L)
        assertThat(database.cacheEntryDao().getAll()).isEmpty()
    }

    @Test fun `file delete failure doesn't fail the operation`() = runTest {
        val dao = database.cacheEntryDao()
        dao.insert(entry("k", "audio", 100, 1L))
        every { diskCache.deleteFile(any()) } throws RuntimeException("disk error")

        policy.evict(50L)

        assertThat(dao.getAll()).isEmpty()
    }

    @Test fun `getEvictableSize sums candidate bytes`() = runTest {
        val dao = database.cacheEntryDao()
        dao.insert(entry("a", "audio", 100, 1L))
        dao.insert(entry("b", "image", 200, 2L))
        dao.insert(entry("c", "metadata", 50, 3L))
        dao.insert(entry("d", "audio", 999, 4L, isDownload = true)) // should be excluded
        assertThat(policy.getEvictableSize()).isEqualTo(350L)
    }
}
