@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryImplTest : DbTestBase() {

    private fun repo() = PlaylistRepositoryImpl(
        database = db,
        playlistDao = db.playlistDao(),
        trackDao = db.trackDao(),
        favoriteDao = db.favoriteDao(),
        downloadRepository = mockk<DownloadRepository>(relaxed = true),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun track(id: String) = TrackEntity(
        id = id, albumId = "a", title = id, artist = "x",
        trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
        albumTitle = "", source = "bandcamp",
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
}
