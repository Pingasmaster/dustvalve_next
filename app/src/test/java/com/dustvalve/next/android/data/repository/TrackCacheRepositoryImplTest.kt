package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.domain.model.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackCacheRepositoryImplTest : DbTestBase() {

    private fun repo() = TrackCacheRepositoryImpl(trackDao = db.trackDao(), favoriteDao = db.favoriteDao())

    private fun track(id: String, title: String = id) = Track(
        id = id, albumId = "a", title = title, artist = "x",
        trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
        albumTitle = "",
    )

    @Test fun `cacheTracks upserts in place so playlist memberships survive`() = runTest {
        val repo = repo()
        repo.cacheTracks(listOf(track("t1", title = "old")))
        db.playlistDao().insertPlaylist(PlaylistEntity(id = "p1", name = "My"))
        db.playlistDao().addTrackToPlaylist("p1", "t1")

        // Re-caching an already-present id must UPDATE, not DELETE+INSERT:
        // REPLACE would fire playlist_tracks' ON DELETE CASCADE and silently
        // wipe the membership (the @Upsert comment on TrackDao.insertAll).
        repo.cacheTracks(listOf(track("t1", title = "new")))

        assertThat(db.trackDao().getById("t1")?.title).isEqualTo("new")
        assertThat(db.playlistDao().getTracksInPlaylistSync("p1").map { it.id }).containsExactly("t1")
    }

    @Test fun `getTrack returns null for missing ids and decorates favorites`() = runTest {
        val repo = repo()
        assertThat(repo.getTrack("missing")).isNull()

        repo.cacheTracks(listOf(track("t1")))
        assertThat(repo.getTrack("t1")?.isFavorite).isFalse()

        db.favoriteDao().insert(FavoriteEntity(id = "t1", type = "track"))
        assertThat(repo.getTrack("t1")?.isFavorite).isTrue()
    }

    @Test fun `getTracks survives more than 900 ids and joins favorites`() = runTest {
        val repo = repo()
        val ids = (1..950).map { "t$it" }
        repo.cacheTracks(ids.map { track(it) })
        db.favoriteDao().insert(FavoriteEntity(id = "t7", type = "track"))

        val tracks = repo.getTracks(ids)

        assertThat(tracks).hasSize(950)
        assertThat(tracks.single { it.id == "t7" }.isFavorite).isTrue()
        assertThat(tracks.first { it.id == "t1" }.isFavorite).isFalse()
    }

    @Test fun `getTracks returns empty for empty input`() = runTest {
        assertThat(repo().getTracks(emptyList())).isEmpty()
    }
}
