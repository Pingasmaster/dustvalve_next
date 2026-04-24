package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class YouTubeSourceTest {

    private val ytRepo = mockk<YouTubeRepository>()
    private val source = YouTubeSource(ytRepo)

    @Test fun `provider is YOUTUBE with id 'youtube'`() {
        assertThat(source.provider).isEqualTo(MusicProvider.YOUTUBE)
        assertThat(source.id).isEqualTo("youtube")
    }

    @Test fun `capabilities cover search artist artist_tracks and collection (no album)`() {
        assertThat(source.capabilities).containsExactly(
            SourceConcept.SEARCH,
            SourceConcept.ARTIST,
            SourceConcept.ARTIST_TRACKS,
            SourceConcept.COLLECTION,
        )
        assertThat(source.capabilities).doesNotContain(SourceConcept.ALBUM)
    }

    @Test fun `search unpacks the Pair and returns only the results`() = runTest {
        val expected = listOf(result("Song", SearchResultType.YOUTUBE_TRACK))
        coEvery { ytRepo.search(query = "q", filter = null, page = null) } returns (expected to null)

        assertThat(source.search("q")).isEqualTo(expected)
    }

    @Test fun `search passes through filter verbatim`() = runTest {
        coEvery { ytRepo.search(query = "q", filter = "songs", page = null) } returns (emptyList<SearchResult>() to null)
        source.search("q", filter = "songs")
        coVerify { ytRepo.search(query = "q", filter = "songs", page = null) }
    }

    @Test fun `getArtist returns metadata with empty albums`() = runTest {
        coEvery {
            ytRepo.getChannelVideos(channelUrl = "https://www.youtube.com/channel/UC", page = null)
        } returns Triple(emptyList<Track>(), "The Channel", null)

        val artist = source.getArtist("https://www.youtube.com/channel/UC")
        assertThat(artist.name).isEqualTo("The Channel")
        assertThat(artist.url).isEqualTo("https://www.youtube.com/channel/UC")
        assertThat(artist.albums).isEmpty()
    }

    @Test fun `getArtistTracks passes the opaque continuation through`() = runTest {
        val token = Any() // opaque page token
        val nextToken = Any()
        val tracks = listOf(track("1"), track("2"))
        coEvery {
            ytRepo.getChannelVideos(channelUrl = "https://www.youtube.com/channel/UC", page = token)
        } returns Triple(tracks, "Artist", nextToken)

        val collection = source.getArtistTracks(
            url = "https://www.youtube.com/channel/UC",
            continuation = token,
        )
        assertThat(collection.tracks).isEqualTo(tracks)
        assertThat(collection.continuation).isSameInstanceAs(nextToken)
        assertThat(collection.hasMore).isTrue()
    }

    @Test fun `getArtistTracks reports no more when continuation is null`() = runTest {
        coEvery {
            ytRepo.getChannelVideos(channelUrl = any(), page = null)
        } returns Triple(listOf(track("a")), "Artist", null)

        val collection = source.getArtistTracks("https://x", continuation = null)
        assertThat(collection.hasMore).isFalse()
        assertThat(collection.continuation).isNull()
    }

    @Test fun `getAlbum throws UnsupportedSourceOperation`() = runTest {
        val ex = runCatching { source.getAlbum("https://x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(UnsupportedSourceOperation::class.java)
        assertThat(ex!!.message).contains("youtube")
        assertThat(ex.message).contains("album")
    }

    @Test fun `getCollection delegates to getPlaylistTracks`() = runTest {
        val tracks = listOf(track("a"), track("b"))
        coEvery { ytRepo.getPlaylistTracks("https://pl") } returns (tracks to "My List")

        val collection = source.getCollection("https://pl")
        assertThat(collection.tracks).isEqualTo(tracks)
        assertThat(collection.name).isEqualTo("My List")
        assertThat(collection.continuation).isNull()
    }

    private fun result(name: String, type: SearchResultType) = SearchResult(
        type = type, name = name, url = "https://x",
        imageUrl = null, artist = null, album = null, genre = null, releaseDate = null,
    )

    private fun track(id: String) = Track(
        id = "yt_$id", albumId = "", title = id, artist = "",
        trackNumber = 0, duration = 0f, streamUrl = null, artUrl = "",
        albumTitle = "", source = TrackSource.YOUTUBE,
    )
}
