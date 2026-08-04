package com.dustvalve.next.android.data.repository

import app.cash.turbine.test
import com.dustvalve.next.android.data.local.db.DbTestBase
import com.dustvalve.next.android.data.local.db.entity.AlbumEntity
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.model.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryImplTest : DbTestBase() {

    private fun repo() = LibraryRepositoryImpl(
        database = db,
        trackDao = db.trackDao(),
        favoriteDao = db.favoriteDao(),
        recentTrackDao = db.recentTrackDao(),
    )

    private fun albumRow(id: String, title: String) = AlbumEntity(
        id = id, url = "https://x.bandcamp.com/album/$id", title = title, artist = "Artist",
        artistUrl = "https://x.bandcamp.com", artUrl = "https://f4.bcbits.com/$id.jpg",
        releaseDate = null, about = null, tags = "",
    )

    @Test fun `getFavoriteAlbums maps join fields 1-to-1 and preserves pinned-then-addedAt ordering`() = runTest {
        db.albumDao().insert(albumRow("a1", "First"))
        db.albumDao().insert(albumRow("a2", "Second"))
        db.albumDao().insert(albumRow("a3", "Third"))
        db.favoriteDao().insert(FavoriteEntity(id = "a1", type = "album", addedAt = 1L, isPinned = true, shapeKey = "hex"))
        db.favoriteDao().insert(FavoriteEntity(id = "a2", type = "album", addedAt = 2L))
        db.favoriteDao().insert(FavoriteEntity(id = "a3", type = "album", addedAt = 3L))
        // A track favorite with the same table must not leak in.
        db.favoriteDao().insert(FavoriteEntity(id = "t1", type = "track", addedAt = 9L))

        repo().getFavoriteAlbums().test {
            val items = awaitItem()
            // Pinned first, then addedAt DESC (the SQL ORDER BY, relocated untouched).
            assertThat(items.map { it.id }).containsExactly("a1", "a3", "a2").inOrder()
            val pinned = items.first()
            assertThat(pinned.addedAt).isEqualTo(1L)
            assertThat(pinned.isPinned).isTrue()
            assertThat(pinned.shapeKey).isEqualTo("hex")
            assertThat(pinned.albumTitle).isEqualTo("First")
            assertThat(pinned.albumArtist).isEqualTo("Artist")
            assertThat(pinned.albumArtUrl).isEqualTo("https://f4.bcbits.com/a1.jpg")
            assertThat(pinned.albumUrl).isEqualTo("https://x.bandcamp.com/album/a1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `getFavoriteArtists maps join fields 1-to-1 including nullable image url`() = runTest {
        db.artistDao().insert(
            ArtistEntity(id = "ar1", name = "Name", url = "https://y.bandcamp.com", imageUrl = null, bio = null, location = null),
        )
        db.favoriteDao().insert(FavoriteEntity(id = "ar1", type = "artist", addedAt = 5L))

        repo().getFavoriteArtists().test {
            val item = awaitItem().single()
            assertThat(item.id).isEqualTo("ar1")
            assertThat(item.addedAt).isEqualTo(5L)
            assertThat(item.isPinned).isFalse()
            assertThat(item.shapeKey).isNull()
            assertThat(item.artistName).isEqualTo("Name")
            assertThat(item.artistImageUrl).isNull()
            assertThat(item.artistUrl).isEqualTo("https://y.bandcamp.com")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `toggleTrackFavorite flips the row and returns the new state`() = runTest {
        val repo = repo()

        assertThat(repo.toggleTrackFavorite("t1")).isTrue()
        val row = db.favoriteDao().getAllSync().single()
        assertThat(row.id).isEqualTo("t1")
        assertThat(row.type).isEqualTo("track")

        assertThat(repo.toggleTrackFavorite("t1")).isFalse()
        assertThat(db.favoriteDao().getAllSync()).isEmpty()
    }

    @Test fun `addToRecent inserts the track row when missing and records the play`() = runTest {
        val track = Track(
            id = "t1", albumId = "a", title = "T", artist = "x",
            trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
            albumTitle = "",
        )

        repo().addToRecent(track)

        assertThat(db.trackDao().getById("t1")).isNotNull()
        assertThat(db.recentTrackDao().getAllSync().single().trackId).isEqualTo("t1")
    }

    @Test fun `addToRecent does not clobber an existing richer track row`() = runTest {
        db.trackDao().insertAll(
            listOf(
                TrackEntity(
                    id = "t1", albumId = "a", title = "Rich title", artist = "x",
                    trackNumber = 1, duration = 1f, streamUrl = "https://s", artUrl = "",
                    albumTitle = "", source = "bandcamp",
                ),
            ),
        )
        val track = Track(
            id = "t1", albumId = "a", title = "Poor title", artist = "x",
            trackNumber = 1, duration = 1f, streamUrl = null, artUrl = "",
            albumTitle = "",
        )

        repo().addToRecent(track)

        // The impl only inserts when getById returns null, so the stored row wins.
        assertThat(db.trackDao().getById("t1")?.title).isEqualTo("Rich title")
        assertThat(db.recentTrackDao().getAllSync().single().trackId).isEqualTo("t1")
    }
}
