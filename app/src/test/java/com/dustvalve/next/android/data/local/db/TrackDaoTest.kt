package com.dustvalve.next.android.data.local.db

import com.dustvalve.next.android.data.local.db.dao.deleteByIds
import com.dustvalve.next.android.data.local.db.dao.getByIds
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackDaoTest : DbTestBase() {

    private fun t(
        id: String, title: String = id, artist: String = "A",
        album: String = "Alb", source: String = "bandcamp",
    ) = TrackEntity(
        id = id, albumId = "al", title = title, artist = artist,
        trackNumber = 1, duration = 60f, streamUrl = null, artUrl = "",
        albumTitle = album, source = source,
    )

    @Test fun `insert and getById`() = runTest {
        val dao = db.trackDao()
        dao.insertAll(listOf(t("t1")))
        assertThat(dao.getById("t1")?.title).isEqualTo("t1")
        assertThat(dao.getById("missing")).isNull()
    }

    @Test fun `getByIds chunks across large id lists`() = runTest {
        val dao = db.trackDao()
        val ids = (1..1500).map { "id_$it" }
        dao.insertAll(ids.map { t(id = it) })

        val got = dao.getByIds(ids)
        assertThat(got).hasSize(1500)
    }

    @Test fun `deleteByIds chunks`() = runTest {
        val dao = db.trackDao()
        val ids = (1..1200).map { "x_$it" }
        dao.insertAll(ids.map { t(id = it) })

        dao.deleteByIds(ids)
        assertThat(dao.getByIds(ids)).isEmpty()
    }

    @Test fun `deleteByAlbumId removes matching rows only`() = runTest {
        val dao = db.trackDao()
        dao.insertAll(listOf(
            t("t1").copy(albumId = "a1"),
            t("t2").copy(albumId = "a1"),
            t("t3").copy(albumId = "a2"),
        ))
        dao.deleteByAlbumId("a1")
        assertThat(dao.getByAlbumId("a1")).isEmpty()
        assertThat(dao.getByAlbumId("a2")).hasSize(1)
    }

    @Test fun `searchLocalTracks matches title artist and album`() = runTest {
        val dao = db.trackDao()
        dao.insertAll(listOf(
            t("l1", title = "foobar", source = "local"),
            t("l2", artist = "Foo Bar", source = "local"),
            t("l3", album = "Foobar Album", source = "local"),
            t("l4", title = "unrelated", source = "local"),
            t("b1", title = "foobar", source = "bandcamp"),
        ))
        val r = dao.searchLocalTracks("foo").map { it.id }.toSet()
        assertThat(r).containsExactly("l1", "l2", "l3")
    }

    @Test fun `getLocalTrackIdsByFolderSync filters by folderUri`() = runTest {
        val dao = db.trackDao()
        dao.insertAll(listOf(
            t("l1", source = "local").copy(folderUri = "content://a"),
            t("l2", source = "local").copy(folderUri = "content://b"),
        ))
        assertThat(dao.getLocalTrackIdsByFolderSync("content://a")).containsExactly("l1")
    }
}
