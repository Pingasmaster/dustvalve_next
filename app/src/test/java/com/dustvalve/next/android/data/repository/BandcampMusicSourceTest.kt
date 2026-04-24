package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.SearchRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BandcampMusicSourceTest {

    private val searchRepo = mockk<SearchRepository>()
    private val artistRepo = mockk<ArtistRepository>()
    private val albumRepo = mockk<AlbumRepository>()
    private val source = BandcampMusicSource(searchRepo, artistRepo, albumRepo)

    @Test fun `provider is BANDCAMP with id 'bandcamp'`() {
        assertThat(source.provider).isEqualTo(MusicProvider.BANDCAMP)
        assertThat(source.id).isEqualTo("bandcamp")
    }

    @Test fun `capabilities cover search artist album but not tracks-feed or collection`() {
        assertThat(source.capabilities).containsExactly(
            SourceConcept.SEARCH, SourceConcept.ARTIST, SourceConcept.ALBUM,
        )
    }

    @Test fun `search passes through with no filter`() = runTest {
        val results = listOf(result(SearchResultType.TRACK, "Song"))
        coEvery { searchRepo.search(query = "foo", type = null) } returns results

        val actual = source.search("foo")
        assertThat(actual).isEqualTo(results)
    }

    @Test fun `search maps filter strings onto SearchResultType`() = runTest {
        coEvery { searchRepo.search(query = any(), type = any()) } returns emptyList()

        source.search("q", filter = "artists")
        source.search("q", filter = "albums")
        source.search("q", filter = "tracks")
        source.search("q", filter = "garbage")
        source.search("q", filter = null)

        coVerify { searchRepo.search(query = "q", type = SearchResultType.ARTIST) }
        coVerify { searchRepo.search(query = "q", type = SearchResultType.ALBUM) }
        coVerify { searchRepo.search(query = "q", type = SearchResultType.TRACK) }
        coVerify(exactly = 2) { searchRepo.search(query = "q", type = null) }
    }

    @Test fun `getArtist delegates to ArtistRepository`() = runTest {
        val artist = Artist(id = "a", name = "A", url = "https://x", imageUrl = null, bio = null, location = null, albums = emptyList())
        coEvery { artistRepo.getArtistDetail("https://x") } returns artist

        assertThat(source.getArtist("https://x")).isEqualTo(artist)
    }

    @Test fun `getAlbum delegates to AlbumRepository`() = runTest {
        val album = Album(
            id = "al", url = "https://x", title = "T", artist = "A", artistUrl = "",
            artUrl = "", releaseDate = null, about = null, tracks = emptyList(), tags = emptyList(),
        )
        coEvery { albumRepo.getAlbumDetail("https://x") } returns album

        assertThat(source.getAlbum("https://x")).isEqualTo(album)
    }

    @Test fun `getArtistTracks throws UnsupportedSourceOperation`() = runTest {
        val ex = runCatching { source.getArtistTracks("https://x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(UnsupportedSourceOperation::class.java)
        assertThat(ex!!.message).contains("bandcamp")
        assertThat(ex.message).contains("artist_tracks")
    }

    @Test fun `getCollection throws UnsupportedSourceOperation`() = runTest {
        val ex = runCatching { source.getCollection("https://x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(UnsupportedSourceOperation::class.java)
        assertThat(ex!!.message).contains("collection")
    }

    private fun result(type: SearchResultType, name: String) = SearchResult(
        type = type, name = name, url = "https://x",
        imageUrl = null, artist = null, album = null, genre = null, releaseDate = null,
    )
}
