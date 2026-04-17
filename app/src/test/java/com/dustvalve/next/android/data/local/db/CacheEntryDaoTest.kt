package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.entity.CacheEntryEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheEntryDaoTest : DbTestBase() {

    private fun e(
        key: String, type: String, size: Long,
        access: Long = 0L, isDownload: Boolean = false,
        path: String? = "/tmp/$key",
    ) = CacheEntryEntity(key, type, size, access, isDownload, path)

    @Test fun `total size sums all entries`() = runTest {
        val dao = db.cacheEntryDao()
        dao.insert(e("a", "audio", 100))
        dao.insert(e("b", "image", 200))
        dao.insert(e("c", "metadata", 50))
        dao.insert(e("d", "audio", 300, isDownload = true))
        assertThat(dao.getTotalSize()).isEqualTo(650L)
    }

    @Test fun `non-download size excludes downloads`() = runTest {
        val dao = db.cacheEntryDao()
        dao.insert(e("a", "audio", 100))
        dao.insert(e("b", "audio", 500, isDownload = true))
        assertThat(dao.getNonDownloadTotalSize()).isEqualTo(100L)
    }

    @Test fun `getEvictionCandidates excludes downloads and orders oldest first`() = runTest {
        val dao = db.cacheEntryDao()
        dao.insert(e("new", "audio", 10, access = 100L))
        dao.insert(e("dl", "audio", 10, access = 1L, isDownload = true))
        dao.insert(e("oldest", "image", 10, access = 1L))
        dao.insert(e("mid", "metadata", 10, access = 50L))

        val cands = dao.getEvictionCandidates().map { it.key }
        assertThat(cands).containsExactly("oldest", "mid", "new").inOrder()
    }

    @Test fun `updateLastAccessed changes value`() = runTest {
        val dao = db.cacheEntryDao()
        dao.insert(e("k", "audio", 1, access = 1L))
        dao.updateLastAccessed("k", 999L)
        assertThat(dao.getByKey("k")!!.lastAccessed).isEqualTo(999L)
    }

    @Test fun `deleteNonDownloads preserves downloads`() = runTest {
        val dao = db.cacheEntryDao()
        dao.insert(e("u", "audio", 1))
        dao.insert(e("d", "audio", 1, isDownload = true))
        dao.deleteNonDownloads()
        assertThat(dao.getAll().map { it.key }).containsExactly("d")
    }

    @Test fun `deleteByKey removes single entry`() = runTest {
        val dao = db.cacheEntryDao()
        dao.insert(e("k1", "audio", 1))
        dao.insert(e("k2", "audio", 1))
        dao.deleteByKey("k1")
        assertThat(dao.getAll().map { it.key }).containsExactly("k2")
    }
}
