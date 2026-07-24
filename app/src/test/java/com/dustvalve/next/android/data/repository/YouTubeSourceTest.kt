package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
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

    @Test fun `id is 'youtube'`() {
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

    @Test fun `getCollection mix first page encodes delivered ids into the cursor`() = runTest {
        val mixUrl = "https://www.youtube.com/playlist?list=RDdQw4w9WgXcQ"
        coEvery {
            ytRepo.getMixPage(mixUrl = mixUrl, cursor = null, seenVideoIds = emptySet())
        } returns Triple(
            listOf(track("vid00000001"), track("vid00000002")),
            "My Mix",
            YouTubePlaylistParser.MixContinuation(lastVideoId = "vid00000002", playlistIndex = 2, params = "PARAMS_X"),
        )

        val collection = source.getCollection(mixUrl)

        assertThat(collection.name).isEqualTo("My Mix")
        assertThat(collection.hasMore).isTrue()
        // Opaque to callers, but structurally: continuation head + seen ids.
        assertThat(collection.continuation)
            .isEqualTo("vid00000002:2:PARAMS_X|vid00000001,vid00000002")
    }

    @Test fun `getCollection mix second page decodes cursor into continuation and seen ids`() = runTest {
        val mixUrl = "https://www.youtube.com/playlist?list=RDdQw4w9WgXcQ"
        coEvery {
            ytRepo.getMixPage(
                mixUrl = mixUrl,
                cursor = YouTubePlaylistParser.MixContinuation("vid00000002", 2, "PARAMS_X"),
                seenVideoIds = setOf("vid00000001", "vid00000002"),
            )
        } returns Triple(listOf(track("vid00000003")), "My Mix", null)

        val collection = source.getCollection(
            url = mixUrl,
            continuation = "vid00000002:2:PARAMS_X|vid00000001,vid00000002",
        )

        assertThat(collection.tracks.map { it.id }).containsExactly("yt_vid00000003")
        assertThat(collection.continuation).isNull()
        assertThat(collection.hasMore).isFalse()
    }

    @Test fun `getCollection mix decodes a cursor with empty params as null params`() = runTest {
        val mixUrl = "https://www.youtube.com/playlist?list=RDdQw4w9WgXcQ"
        coEvery {
            ytRepo.getMixPage(
                mixUrl = mixUrl,
                cursor = YouTubePlaylistParser.MixContinuation("vid00000002", 2, null),
                seenVideoIds = setOf("vid00000001"),
            )
        } returns Triple(emptyList(), "My Mix", null)

        val collection = source.getCollection(url = mixUrl, continuation = "vid00000002:2:|vid00000001")
        assertThat(collection.tracks).isEmpty()
    }

    @Test fun `getCollection mix tolerates a legacy cursor without delimiter`() = runTest {
        val mixUrl = "https://www.youtube.com/playlist?list=RDabcdefghijk"
        coEvery {
            ytRepo.getMixPage(mixUrl = mixUrl, cursor = null, seenVideoIds = emptySet())
        } returns Triple(listOf(track("a1234567890")), "Mix", null)

        val collection = source.getCollection(url = mixUrl, continuation = "some-legacy-token")
        assertThat(collection.tracks).hasSize(1)
    }

    @Test fun `getCollection mix caps remembered ids at the 200 most recent`() = runTest {
        val mixUrl = "https://www.youtube.com/playlist?list=RDabcdefghijk"
        val priorIds = (1..200).map { "vid%08d".format(it) }
        val cursor = "vid00000200:200:|" + priorIds.joinToString(",")
        coEvery {
            ytRepo.getMixPage(mixUrl = mixUrl, cursor = any(), seenVideoIds = any())
        } returns Triple(
            listOf(track("newvid00001")),
            "Mix",
            YouTubePlaylistParser.MixContinuation("newvid00001", 201, null),
        )

        val collection = source.getCollection(url = mixUrl, continuation = cursor)

        val next = collection.continuation as String
        val ids = next.substringAfter("|").split(",")
        assertThat(ids).hasSize(200)
        assertThat(ids.last()).isEqualTo("newvid00001")
        // Oldest id rotated out.
        assertThat(ids.first()).isEqualTo("vid00000002")
        assertThat(ids).doesNotContain("vid00000001")
    }

    private fun result(name: String, type: SearchResultType) = SearchResult(
        type = type,
        name = name,
        url = "https://x",
        imageUrl = null,
        artist = null,
        album = null,
        genre = null,
        releaseDate = null,
    )

    private fun track(id: String) = Track(
        id = "yt_$id", albumId = "", title = id, artist = "",
        trackNumber = 0, duration = 0f, streamUrl = null, artUrl = "",
        albumTitle = "", source = TrackSource.YOUTUBE,
    )
}
