package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadDaoTest : DbTestBase() {

    private fun d(
        trackId: String,
        albumId: String = "al",
        sizeBytes: Long = 100L,
        pinned: Boolean = true,
        downloadedAt: Long = 1_000L,
        lastAccessed: Long = downloadedAt,
    ) = DownloadEntity(
        trackId = trackId,
        albumId = albumId,
        filePath = "/x/$trackId.mp3",
        sizeBytes = sizeBytes,
        downloadedAt = downloadedAt,
        pinned = pinned,
        lastAccessed = lastAccessed,
    )

    @Test fun `insert replaces on conflict and getByTrackId reads back`() = runTest {
        val dao = db.downloadDao()
        dao.insert(d("t1", sizeBytes = 10))
        dao.insert(d("t1", sizeBytes = 20))
        assertThat(dao.getByTrackId("t1")?.sizeBytes).isEqualTo(20)
        assertThat(dao.getAllSync()).hasSize(1)
        assertThat(dao.getByTrackId("missing")).isNull()
    }

    @Test fun `getAllSync orders newest first`() = runTest {
        val dao = db.downloadDao()
        dao.insert(d("old", downloadedAt = 1))
        dao.insert(d("new", downloadedAt = 3))
        dao.insert(d("mid", downloadedAt = 2))
        assertThat(dao.getAllSync().map { it.trackId })
            .containsExactly("new", "mid", "old").inOrder()
    }

    @Test fun `total and pinned sizes aggregate correctly`() = runTest {
        val dao = db.downloadDao()
        assertThat(dao.getTotalSize()).isEqualTo(0)
        assertThat(dao.getPinnedSize()).isEqualTo(0)

        dao.insert(d("t1", sizeBytes = 100, pinned = true))
        dao.insert(d("t2", sizeBytes = 30, pinned = false))
        dao.insert(d("t3", sizeBytes = 7, pinned = false))

        assertThat(dao.getTotalSize()).isEqualTo(137)
        assertThat(dao.getPinnedSize()).isEqualTo(100)
    }

    @Test fun `eviction candidates are unpinned oldest-accessed first`() = runTest {
        val dao = db.downloadDao()
        dao.insert(d("pinned", pinned = true, lastAccessed = 1))
        dao.insert(d("cold", pinned = false, lastAccessed = 2))
        dao.insert(d("warm", pinned = false, lastAccessed = 9))

        val candidates = dao.getEvictionCandidates()
        assertThat(candidates.map { it.trackId }).containsExactly("cold", "warm").inOrder()
    }

    @Test fun `getByAlbumId and getDownloadedAlbumIds group by album`() = runTest {
        val dao = db.downloadDao()
        dao.insert(d("t1", albumId = "a1"))
        dao.insert(d("t2", albumId = "a1"))
        dao.insert(d("t3", albumId = "a2"))

        assertThat(dao.getByAlbumId("a1").map { it.trackId }).containsExactly("t1", "t2")
        assertThat(dao.getDownloadedAlbumIds().first()).containsExactly("a1", "a2")
    }

    @Test fun `delete removes a single track row`() = runTest {
        val dao = db.downloadDao()
        dao.insert(d("t1"))
        dao.insert(d("t2"))
        dao.delete("t1")
        assertThat(dao.getByTrackId("t1")).isNull()
        assertThat(dao.getAllTrackIds().first()).containsExactly("t2")
    }

    @Test fun `deleteAll empties the table`() = runTest {
        val dao = db.downloadDao()
        dao.insert(d("t1"))
        dao.deleteAll()
        assertThat(dao.getAllSync()).isEmpty()
    }
}
