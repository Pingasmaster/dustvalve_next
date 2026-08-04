@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryImplTest : DbTestBase() {

    // Extension on TestScope so the repository's ioDispatcher shares runTest's
    // scheduler. A bare UnconfinedTestDispatcher() mints its OWN scheduler, and
    // yield()ing across the two blows up as DispatchException mid-flow.
    private fun TestScope.repo() = PlaylistRepositoryImpl(
        database = db,
        playlistDao = db.playlistDao(),
        trackDao = db.trackDao(),
        favoriteDao = db.favoriteDao(),
        downloadRepository = mockk<DownloadRepository>(relaxed = true),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun track(id: String) = TrackEntity(
        id = id, albumId = "a", title = id, artist = "x",
        trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
        albumTitle = "", source = "bandcamp",
    )

    private fun domainTrack(id: String) = Track(
        id = id, albumId = "a", title = id, artist = "x",
        trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
        albumTitle = "",
    )

    @Test fun `favorite toggle re-emits user playlist tracks`() = runTest {
        db.trackDao().insertAll(listOf(track("t1")))
        db.playlistDao().insertPlaylist(PlaylistEntity(id = "p1", name = "My"))
        db.playlistDao().addTrackToPlaylist("p1", "t1")

        repo().getTracksInPlaylist("p1").test {
            assertThat(awaitItem().single().isFavorite).isFalse()

            // Regression: isFavorite used to come from a one-shot suspend
            // query inside map {}, so toggling a heart never re-emitted and
            // the UI kept the stale flag until the list itself changed.
            db.favoriteDao().insert(FavoriteEntity(id = "t1", type = "track"))
            assertThat(awaitItem().single().isFavorite).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `favorite toggle re-emits recent playlist tracks`() = runTest {
        db.trackDao().insertAll(listOf(track("t1")))
        db.recentTrackDao().insert(
            com.dustvalve.next.android.data.local.db.entity.RecentTrackEntity(trackId = "t1", playedAt = 1L),
        )

        repo().getTracksInPlaylist(
            com.dustvalve.next.android.domain.model.Playlist.ID_RECENT,
        ).test {
            assertThat(awaitItem().single().isFavorite).isFalse()

            db.favoriteDao().insert(FavoriteEntity(id = "t1", type = "track"))
            assertThat(awaitItem().single().isFavorite).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- importTracksAsPlaylist -----------------------------------------

    @Test fun `importTracksAsPlaylist imports tracks in order with no favorite by default`() = runTest {
        val playlist = repo().importTracksAsPlaylist("Imported", listOf(domainTrack("t1"), domainTrack("t2")))

        assertThat(playlist.name).isEqualTo("Imported")
        assertThat(db.trackDao().getAllSync().map { it.id }).containsExactly("t1", "t2")
        assertThat(db.playlistDao().getTracksInPlaylistSync(playlist.id).map { it.id })
            .containsExactly("t1", "t2")
            .inOrder()
        assertThat(db.favoriteDao().getAllSync()).isEmpty()
    }

    @Test fun `importTracksAsPlaylist writes the favorite row exactly when favoriteId is given`() = runTest {
        repo().importTracksAsPlaylist(
            "YT list",
            listOf(domainTrack("t1")),
            favoriteId = "https://youtube.com/playlist?list=x",
            favoriteType = FavoriteType.YOUTUBE_PLAYLIST,
        )

        val favorite = db.favoriteDao().getAllSync().single()
        assertThat(favorite.id).isEqualTo("https://youtube.com/playlist?list=x")
        assertThat(favorite.type).isEqualTo("youtube_playlist")
    }

    @Test fun `importTracksAsPlaylist returns an id usable for deletePlaylist`() = runTest {
        val repo = repo()
        val playlist = repo.importTracksAsPlaylist("Gone soon", listOf(domainTrack("t1")))

        assertThat(repo.deletePlaylist(playlist.id)).isTrue()
        assertThat(db.playlistDao().getPlaylistById(playlist.id)).isNull()
    }

    @Test fun `importTracksAsPlaylist failure inside the transaction persists nothing`() = runTest {
        // favoriteId without favoriteType blows up AFTER the track insert,
        // playlist create and track adds - the single surrounding transaction
        // (the relocated VM block) must roll all of them back.
        val thrown = runCatching {
            repo().importTracksAsPlaylist("Broken", listOf(domainTrack("t1")), favoriteId = "url", favoriteType = null)
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(db.trackDao().getAllSync()).isEmpty()
        assertThat(db.playlistDao().getAllPlaylists().first()).isEmpty()
        assertThat(db.playlistDao().getAllPlaylistTrackMappingsSync()).isEmpty()
        assertThat(db.favoriteDao().getAllSync()).isEmpty()
    }

    // --- playlistExistsByName / getPlaylistTrackMappings ----------------

    @Test fun `playlistExistsByName is a display-only boolean probe`() = runTest {
        db.playlistDao().insertPlaylist(PlaylistEntity(id = "p1", name = "Mix"))

        assertThat(repo().playlistExistsByName("Mix")).isTrue()
        assertThat(repo().playlistExistsByName("Other")).isFalse()
    }

    @Test fun `getPlaylistTrackMappings groups member track ids by playlist`() = runTest {
        db.trackDao().insertAll(listOf(track("t1"), track("t2"), track("t3")))
        db.playlistDao().insertPlaylist(PlaylistEntity(id = "p1", name = "One"))
        db.playlistDao().insertPlaylist(PlaylistEntity(id = "p2", name = "Two"))
        db.playlistDao().addTrackToPlaylist("p1", "t1")
        db.playlistDao().addTrackToPlaylist("p1", "t2")
        db.playlistDao().addTrackToPlaylist("p2", "t3")

        repo().getPlaylistTrackMappings().test {
            assertThat(awaitItem()).containsExactlyEntriesIn(
                mapOf("p1" to setOf("t1", "t2"), "p2" to setOf("t3")),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
