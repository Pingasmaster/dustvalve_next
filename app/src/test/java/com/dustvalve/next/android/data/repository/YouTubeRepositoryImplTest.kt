package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeChannelParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser
import com.dustvalve.next.android.data.remote.youtube.innertube.PlayerStreamInfo
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import org.junit.Before
import org.junit.Test

class YouTubeRepositoryImplTest {

    private lateinit var client: YouTubeInnertubeClient
    private lateinit var playerParser: YouTubePlayerParser
    private lateinit var searchParser: YouTubeSearchParser
    private lateinit var playlistParser: YouTubePlaylistParser
    private lateinit var channelParser: YouTubeChannelParser
    private lateinit var nextParser: YouTubeNextParser
    private lateinit var repo: YouTubeRepositoryImpl

    private val empty: JsonElement = buildJsonObject {}

    @Before fun setUp() {
        client = mockk()
        playerParser = mockk()
        searchParser = mockk()
        playlistParser = mockk()
        channelParser = mockk()
        nextParser = mockk()
        // Cache DAOs explicitly return null on lookup so the existing tests
        // (which assert the network/parser path) hit the cache-miss branch.
        val videoCacheMock = mockk<com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao>(relaxed = true)
        val playlistCacheMock = mockk<com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao>(relaxed = true)
        coEvery { videoCacheMock.getById(any()) } returns null
        coEvery { videoCacheMock.getByIds(any()) } returns emptyList()
        coEvery { playlistCacheMock.getById(any()) } returns null
        val ytmRepoMock = mockk<com.dustvalve.next.android.domain.repository.YouTubeMusicRepository>(relaxed = true)
        coEvery { ytmRepoMock.lookupAlbumPlaylistForVideo(any()) } returns null
        repo = YouTubeRepositoryImpl(
            client, playerParser, searchParser, playlistParser, channelParser, nextParser,
            videoCache = videoCacheMock,
            playlistCache = playlistCacheMock,
            youTubeMusicRepository = ytmRepoMock,
        )
    }

    @Test fun `getStreamUrl extracts videoId and dispatches to player`() = runTest {
        coEvery { client.player("dQw4w9WgXcQ") } returns empty
        every { playerParser.parsePlayerStreamInfo(empty) } returns PlayerStreamInfo(
            streamUrl = "https://stream/x", format = AudioFormat.OPUS, bitrate = 128000, mimeType = "audio/webm; codecs=\"opus\"",
        )

        val url = repo.getStreamUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(url).isEqualTo("https://stream/x")
        coVerify { client.player("dQw4w9WgXcQ") }
    }

    @Test fun `getStreamUrl handles youtu_be short links`() = runTest {
        coEvery { client.player("abcdefghijk") } returns empty
        every { playerParser.parsePlayerStreamInfo(empty) } returns PlayerStreamInfo(
            "u", AudioFormat.OPUS, 0, "")
        repo.getStreamUrl("https://youtu.be/abcdefghijk")
        coVerify { client.player("abcdefghijk") }
    }

    @Test fun `getStreamUrl throws on malformed URL`() = runTest {
        val ex = runCatching { repo.getStreamUrl("not a youtube url") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `getDownloadableStream returns url and format`() = runTest {
        coEvery { client.player("vidvidvid12") } returns empty
        every { playerParser.parsePlayerStreamInfo(empty) } returns PlayerStreamInfo(
            "https://x", AudioFormat.AAC, 256000, "audio/mp4"
        )

        val (url, fmt) = repo.getDownloadableStream("https://www.youtube.com/watch?v=vidvidvid12")
        assertThat(url).isEqualTo("https://x")
        assertThat(fmt).isEqualTo(AudioFormat.AAC)
    }

    @Test fun `getTrackInfo dispatches and parses Track`() = runTest {
        val track = Track(
            id = "yt_v",
            albumId = "yt_album_v",
            title = "T",
            artist = "A",
            artistUrl = "",
            trackNumber = 0,
            duration = 0f,
            streamUrl = "https://www.youtube.com/watch?v=v",
            artUrl = "",
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )
        coEvery { client.player("vidVidVid12") } returns empty
        every { playerParser.parseTrack(empty, "vidVidVid12") } returns track

        val out = repo.getTrackInfo("https://www.youtube.com/watch?v=vidVidVid12")
        // getTrackInfo now returns parsed.copy(albumUrl = resolved) — a new
        // instance rather than the parser's exact object — so compare fields
        // instead of identity.
        assertThat(out.id).isEqualTo(track.id)
        assertThat(out.title).isEqualTo(track.title)
        assertThat(out.artist).isEqualTo(track.artist)
        assertThat(out.source).isEqualTo(track.source)
        // YTM album lookup is stubbed to null → empty albumUrl.
        assertThat(out.albumUrl).isEmpty()
    }

    @Test fun `getRecommendations calls next and parses`() = runTest {
        val results = listOf(
            SearchResult(SearchResultType.YOUTUBE_TRACK, "n", "u", null, null, null, null, null)
        )
        coEvery { client.next("vidvidvid12") } returns empty
        every { nextParser.parse(empty) } returns results

        val out = repo.getRecommendations("https://www.youtube.com/watch?v=vidvidvid12")
        assertThat(out).isSameInstanceAs(results)
    }

    @Test fun `getPlaylistTracks browses VL playlist and returns title plus tracks`() = runTest {
        val pid = slot<String>()
        coEvery { client.browse(capture(pid), null) } returns empty
        every { playlistParser.parse(empty, "PLabc") } returns YouTubePlaylistParser.PlaylistPage(
            tracks = listOf(track("a")),
            title = "My Playlist",
            continuation = null,
        )

        val (tracks, title) = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=PLabc")
        assertThat(pid.captured).isEqualTo("VLPLabc")
        assertThat(title).isEqualTo("My Playlist")
        assertThat(tracks).hasSize(1)
    }

    @Test fun `getPlaylistTracks paginates through continuations`() = runTest {
        coEvery { client.browse("VLPLxyz", null) } returns empty
        every { playlistParser.parse(empty, "PLxyz") } returns YouTubePlaylistParser.PlaylistPage(
            tracks = listOf(track("p1")),
            title = "T",
            continuation = "C1",
        )
        coEvery { client.browseContinuation("C1") } returns empty
        every { playlistParser.parseContinuation(empty, "PLxyz", 2) } returns YouTubePlaylistParser.PlaylistPage(
            tracks = listOf(track("p2"), track("p3")),
            title = null,
            continuation = null,
        )

        val (tracks, _) = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=PLxyz")
        assertThat(tracks).hasSize(3)
    }

    @Test fun `getChannelVideos uses videos tab params on first call`() = runTest {
        val params = slot<String>()
        coEvery { client.browse("UCaaaaaaaaaaaaaaaaaaaaaa", capture(params)) } returns empty
        every { channelParser.parse(empty, "UCaaaaaaaaaaaaaaaaaaaaaa") } returns YouTubeChannelParser.ChannelPage(
            tracks = listOf(track("c1")),
            channelName = "Some Channel",
            continuation = null,
        )

        val (tracks, name, page) = repo.getChannelVideos(
            "https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa",
        )
        assertThat(tracks).hasSize(1)
        assertThat(name).isEqualTo("Some Channel")
        assertThat(page).isNull() // no continuation in first response
        assertThat(params.captured).isEqualTo("EgZ2aWRlb3PyBgQKAjoA")
    }

    @Test fun `getChannelVideos pagination uses browseContinuation on subsequent call`() = runTest {
        // First page
        coEvery { client.browse("UCbbbbbbbbbbbbbbbbbbbbbb", any()) } returns empty
        every { channelParser.parse(empty, "UCbbbbbbbbbbbbbbbbbbbbbb") } returns YouTubeChannelParser.ChannelPage(
            tracks = listOf(track("a"), track("b")),
            channelName = "C",
            continuation = "CHAN_C1",
        )
        val (_, _, page1) = repo.getChannelVideos("https://www.youtube.com/channel/UCbbbbbbbbbbbbbbbbbbbbbb")
        assertThat(page1).isNotNull()

        // Second page via continuation
        coEvery { client.browseContinuation("CHAN_C1") } returns empty
        every { channelParser.parseContinuation(empty, "UCbbbbbbbbbbbbbbbbbbbbbb", "C", 3) } returns
            YouTubeChannelParser.ChannelPage(
                tracks = listOf(track("c")),
                channelName = "C",
                continuation = null,
            )

        val (tracks2, name2, page2) = repo.getChannelVideos(
            "https://www.youtube.com/channel/UCbbbbbbbbbbbbbbbbbbbbbb",
            page = page1,
        )
        assertThat(tracks2).hasSize(1)
        assertThat(name2).isEqualTo("C")
        assertThat(page2).isNull()
    }

    @Test fun `search filters track results when filter is songs`() = runTest {
        val mixed = listOf(
            SearchResult(SearchResultType.YOUTUBE_TRACK, "v", "u1", null, null, null, null, null),
            SearchResult(SearchResultType.YOUTUBE_PLAYLIST, "p", "u2", null, null, null, null, null),
            SearchResult(SearchResultType.YOUTUBE_ARTIST, "a", "u3", null, null, null, null, null),
        )
        coEvery { client.search(query = "q", params = null) } returns empty
        every { searchParser.parse(empty) } returns YouTubeSearchParser.Page(mixed, "TOK")

        val (out, page) = repo.search("q", filter = "songs")
        assertThat(out).hasSize(1)
        assertThat(out.first().type).isEqualTo(SearchResultType.YOUTUBE_TRACK)
        // Continuation surfacing not yet wired; repository returns null for now.
        assertThat(page).isNull()
    }

    @Test fun `search returns all when no filter`() = runTest {
        val mixed = listOf(
            SearchResult(SearchResultType.YOUTUBE_TRACK, "v", "u1", null, null, null, null, null),
            SearchResult(SearchResultType.YOUTUBE_ARTIST, "a", "u3", null, null, null, null, null),
        )
        coEvery { client.search(query = "q", params = null) } returns empty
        every { searchParser.parse(empty) } returns YouTubeSearchParser.Page(mixed, null)

        val (out, _) = repo.search("q", filter = null)
        assertThat(out).hasSize(2)
    }

    private fun track(id: String) = Track(
        id = "yt_$id",
        albumId = "yt_album_$id",
        title = id,
        artist = "x",
        artistUrl = "",
        trackNumber = 0,
        duration = 0f,
        streamUrl = "https://www.youtube.com/watch?v=$id",
        artUrl = "",
        albumTitle = "",
        source = TrackSource.YOUTUBE,
    )
}
