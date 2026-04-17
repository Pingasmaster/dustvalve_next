package com.dustvalve.next.android.data.local.db

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDaoTest : DbTestBase() {

    private fun track(id: String, art: String? = "https://art/$id") = TrackEntity(
        id = id, albumId = "a", title = id, artist = "x",
        trackNumber = 1, duration = 1f, streamUrl = null, artUrl = art ?: "",
        albumTitle = "", source = "bandcamp",
    )

    private fun userPlaylist(id: String = "p1", name: String = "My", pinned: Boolean = false, sortOrder: Int = 0) =
        PlaylistEntity(id = id, name = name, isPinned = pinned, sortOrder = sortOrder)

    private fun systemPlaylist(id: String, type: String) =
        PlaylistEntity(id = id, name = "sys", isSystem = true, systemType = type)

    @Test fun `addTrackToPlaylist assigns increasing positions`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll(listOf(track("t1"), track("t2"), track("t3")))
        dao.insertPlaylist(userPlaylist())

        dao.addTrackToPlaylist("p1", "t1")
        dao.addTrackToPlaylist("p1", "t2")
        dao.addTrackToPlaylist("p1", "t3")

        val tracks = dao.getTracksInPlaylistSync("p1").map { it.id }
        assertThat(tracks).containsExactly("t1", "t2", "t3").inOrder()
        assertThat(dao.getPlaylistTrackCount("p1")).isEqualTo(3)
    }

    @Test fun `addTrackToPlaylist is idempotent`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll(listOf(track("t1")))
        dao.insertPlaylist(userPlaylist())

        dao.addTrackToPlaylist("p1", "t1")
        dao.addTrackToPlaylist("p1", "t1")
        assertThat(dao.getPlaylistTrackCount("p1")).isEqualTo(1)
    }

    @Test fun `addTrackToPlaylist auto-sets icon from first track`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll(listOf(track("t1", art = "https://cover/1.jpg")))
        dao.insertPlaylist(userPlaylist())
        dao.addTrackToPlaylist("p1", "t1")

        val p = dao.getPlaylistById("p1")!!
        assertThat(p.iconUrl).isEqualTo("https://cover/1.jpg")
    }

    @Test fun `addTrackToPlaylist does not overwrite existing icon`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll(listOf(track("t1", art = "https://new.jpg")))
        dao.insertPlaylist(userPlaylist().copy(iconUrl = "https://existing.jpg"))
        dao.addTrackToPlaylist("p1", "t1")
        assertThat(dao.getPlaylistById("p1")!!.iconUrl).isEqualTo("https://existing.jpg")
    }

    @Test fun `removeTrackFromPlaylistAndUpdateCount shifts positions and count`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll((1..4).map { track("t$it") })
        dao.insertPlaylist(userPlaylist())
        (1..4).forEach { dao.addTrackToPlaylist("p1", "t$it") }

        dao.removeTrackFromPlaylistAndUpdateCount("p1", "t2")
        val remaining = dao.getTracksInPlaylistSync("p1").map { it.id }
        assertThat(remaining).containsExactly("t1", "t3", "t4").inOrder()
        assertThat(dao.getPlaylistTrackCount("p1")).isEqualTo(3)
    }

    @Test fun `removeTrackFromPlaylistAndUpdateCount no-op for missing track`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll(listOf(track("t1")))
        dao.insertPlaylist(userPlaylist())
        dao.addTrackToPlaylist("p1", "t1")
        dao.removeTrackFromPlaylistAndUpdateCount("p1", "ghost")
        assertThat(dao.getPlaylistTrackCount("p1")).isEqualTo(1)
    }

    @Test fun `reorderTrack move down`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll((1..4).map { track("t$it") })
        dao.insertPlaylist(userPlaylist())
        (1..4).forEach { dao.addTrackToPlaylist("p1", "t$it") }

        // Move t1 (pos 0) to position 2 → new order: t2, t3, t1, t4
        dao.reorderTrack("p1", "t1", fromPosition = 0, toPosition = 2)
        val ids = dao.getTracksInPlaylistSync("p1").map { it.id }
        assertThat(ids).containsExactly("t2", "t3", "t1", "t4").inOrder()
    }

    @Test fun `reorderTrack move up`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll((1..4).map { track("t$it") })
        dao.insertPlaylist(userPlaylist())
        (1..4).forEach { dao.addTrackToPlaylist("p1", "t$it") }

        // Move t4 (pos 3) to position 1 → new order: t1, t4, t2, t3
        dao.reorderTrack("p1", "t4", fromPosition = 3, toPosition = 1)
        val ids = dao.getTracksInPlaylistSync("p1").map { it.id }
        assertThat(ids).containsExactly("t1", "t4", "t2", "t3").inOrder()
    }

    @Test fun `reorderTrack same position no-op`() = runTest {
        val dao = db.playlistDao()
        db.trackDao().insertAll(listOf(track("t1"), track("t2")))
        dao.insertPlaylist(userPlaylist())
        dao.addTrackToPlaylist("p1", "t1")
        dao.addTrackToPlaylist("p1", "t2")
        dao.reorderTrack("p1", "t1", 0, 0)
        val ids = dao.getTracksInPlaylistSync("p1").map { it.id }
        assertThat(ids).containsExactly("t1", "t2").inOrder()
    }

    @Test fun `system playlist cannot be renamed deleted or had appearance changed`() = runTest {
        val dao = db.playlistDao()
        dao.insertPlaylist(systemPlaylist("sys1", "downloads"))
        assertThat(dao.renamePlaylist("sys1", "new")).isEqualTo(0)
        assertThat(dao.updatePlaylistAppearance("sys1", "new", "circle", null)).isEqualTo(0)
        assertThat(dao.deletePlaylist("sys1")).isEqualTo(0)
        val p = dao.getPlaylistById("sys1")
        assertThat(p?.name).isEqualTo("sys")
    }

    @Test fun `getAllPlaylists orders pinned first then sortOrder then createdAt desc`() = runTest {
        val dao = db.playlistDao()
        val now = System.currentTimeMillis()
        dao.insertPlaylist(userPlaylist("p_pinned", pinned = true, sortOrder = 5).copy(createdAt = now))
        dao.insertPlaylist(userPlaylist("p_low", sortOrder = 0).copy(createdAt = now - 1000))
        dao.insertPlaylist(userPlaylist("p_high", sortOrder = 10).copy(createdAt = now - 500))
        dao.insertPlaylist(userPlaylist("p_new", sortOrder = 0).copy(createdAt = now))

        dao.getAllPlaylists().test {
            val list = awaitItem().map { it.id }
            assertThat(list.first()).isEqualTo("p_pinned") // pinned first
            // Among non-pinned: sortOrder ASC, then createdAt DESC
            val remaining = list.drop(1)
            // sortOrder 0 entries come before sortOrder 10
            assertThat(remaining.indexOf("p_new")).isLessThan(remaining.indexOf("p_high"))
            assertThat(remaining.indexOf("p_low")).isLessThan(remaining.indexOf("p_high"))
            // Within sortOrder=0, newer createdAt first
            assertThat(remaining.indexOf("p_new")).isLessThan(remaining.indexOf("p_low"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `deletePlaylist removes user playlist`() = runTest {
        val dao = db.playlistDao()
        dao.insertPlaylist(userPlaylist())
        assertThat(dao.deletePlaylist("p1")).isEqualTo(1)
        assertThat(dao.getPlaylistById("p1")).isNull()
    }
}
