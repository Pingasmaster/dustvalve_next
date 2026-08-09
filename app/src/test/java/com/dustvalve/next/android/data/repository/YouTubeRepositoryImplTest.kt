@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.innertube.PlayerStreamInfo
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeChannelParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicAlbumResolver
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicArtistParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private lateinit var albumResolver: YouTubeMusicAlbumResolver
    private lateinit var ytmClient: YouTubeMusicInnertubeClient
    private lateinit var ytmArtistParser: YouTubeMusicArtistParser
    private lateinit var playlistCacheMock: com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao
    private lateinit var repo: YouTubeRepositoryImpl

    private val empty: JsonElement = buildJsonObject {}

    @Before fun setUp() {
        client = mockk()
        playerParser = mockk()
        searchParser = mockk()
        playlistParser = mockk()
        every { playlistParser.isSeedlessMixId(any()) } answers {
            val id = firstArg<String>()
            id.startsWith("RDGMEM") || id.startsWith("RDEM") || id.startsWith("RDCLAK")
        }
        channelParser = mockk()
        nextParser = mockk()
        albumResolver = mockk()
        ytmClient = mockk()
        ytmArtistParser = mockk()
        // Cache DAOs explicitly return null on lookup so the existing tests
        // (which assert the network/parser path) hit the cache-miss branch.
        val videoCacheMock = mockk<com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao>(relaxed = true)
        playlistCacheMock = mockk(relaxed = true)
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
            albumResolver = albumResolver,
            ytmClient = ytmClient,
            ytmArtistParser = ytmArtistParser,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test fun `getStreamUrl extracts videoId and dispatches to player`() = runTest {
        coEvery { client.player("dQw4w9WgXcQ") } returns empty
        every { playerParser.parsePlayerStreamInfo(empty) } returns PlayerStreamInfo(
            streamUrl = "https://stream/x",
            format = AudioFormat.OPUS,
            bitrate = 128000,
            mimeType = "audio/webm; codecs=\"opus\"",
        )

        val url = repo.getStreamUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(url).isEqualTo("https://stream/x")
        coVerify { client.player("dQw4w9WgXcQ") }
    }

    @Test fun `getStreamUrl handles youtu_be short links`() = runTest {
        coEvery { client.player("abcdefghijk") } returns empty
        every { playerParser.parsePlayerStreamInfo(empty) } returns PlayerStreamInfo("u", AudioFormat.OPUS, 0, "")
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
            "https://x",
            AudioFormat.AAC,
            256000,
            "audio/mp4",
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
        // getTrackInfo now returns parsed.copy(albumUrl = resolved) - a new
        // instance rather than the parser's exact object - so compare fields
        // instead of identity.
        assertThat(out.id).isEqualTo(track.id)
        assertThat(out.title).isEqualTo(track.title)
        assertThat(out.artist).isEqualTo(track.artist)
        assertThat(out.source).isEqualTo(track.source)
        // YTM album lookup is stubbed to null -> empty albumUrl.
        assertThat(out.albumUrl).isEmpty()
    }

    @Test fun `getRecommendations calls next and parses`() = runTest {
        val results = listOf(
            SearchResult(SearchResultType.YOUTUBE_TRACK, "n", "u", null, null, null, null, null),
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
            coverUrl = "https://i.ytimg.com/vi/a/hq720.jpg",
        )

        val result = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=PLabc")
        assertThat(pid.captured).isEqualTo("VLPLabc")
        assertThat(result.title).isEqualTo("My Playlist")
        assertThat(result.tracks).hasSize(1)
        assertThat(result.coverUrl).isEqualTo("https://i.ytimg.com/vi/a/hq720.jpg")
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

    @Test fun `getPlaylistTracks resolves MPREb album ids to the audio playlist before browsing`() = runTest {
        // YTM album search results emit playlist?list=MPREb_... URLs; the
        // repository must resolve the album browse id to its OLAK5uy_ audio
        // playlist first - browsing "VLMPREb_..." is invalid.
        coEvery { albumResolver.resolveAudioPlaylistId("MPREb_album0001") } returns "OLAK5uy_realList001"
        coEvery { client.browse("VLOLAK5uy_realList001", null) } returns empty
        every { playlistParser.parse(empty, "OLAK5uy_realList001") } returns YouTubePlaylistParser.PlaylistPage(
            tracks = listOf(track("a1")),
            title = "Album Tracks",
            continuation = null,
        )

        val (tracks, title) = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=MPREb_album0001")

        assertThat(title).isEqualTo("Album Tracks")
        assertThat(tracks).hasSize(1)
        coVerify(exactly = 0) { client.browse("VLMPREb_album0001", any()) }
        // Snapshot cached under BOTH the real playlist id and the MPREb
        // alias, so re-opening the album URL is a direct cache hit.
        coVerify { playlistCacheMock.insert(match { it.playlistId == "OLAK5uy_realList001" }) }
        coVerify { playlistCacheMock.insert(match { it.playlistId == "MPREb_album0001" }) }
    }

    @Test fun `getPlaylistTracks fails cleanly when the MPREb album has no audio playlist`() = runTest {
        coEvery { albumResolver.resolveAudioPlaylistId("MPREb_album0002") } returns null
        val ex = runCatching {
            repo.getPlaylistTracks("https://www.youtube.com/playlist?list=MPREb_album0002")
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        coVerify(exactly = 0) { client.browse(any(), any()) }
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

        // Second page via continuation - MUST go out with the WEB client so
        // the response matches the WEB richGrid shape of page 1 (an MWEB
        // continuation parses to zero tracks, truncating channels to page 1).
        coEvery { client.browseContinuation("CHAN_C1", YouTubeClient.WebNoAuth) } returns empty
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

    @Test fun `getChannelVideos falls back to YTM artist page when Videos tab is empty`() = runTest {
        val topicId = "UCcccccccccccccccccccccc"
        coEvery { client.browse(topicId, any()) } returns empty
        every { channelParser.parse(empty, topicId) } returns YouTubeChannelParser.ChannelPage(
            tracks = emptyList(),
            channelName = "Radiohead - Topic",
            continuation = null,
            avatarUrl = "https://img/topic",
        )
        coEvery { ytmClient.browse(topicId) } returns empty
        every { ytmArtistParser.parse(empty, topicId) } returns YouTubeMusicArtistParser.ArtistPage(
            name = "Radiohead",
            avatarUrl = "https://img/ytm",
            songs = listOf(track("topsong0001")),
            albums = listOf(
                YouTubeMusicArtistParser.ArtistAlbum(
                    browseId = "MPREb_album0001",
                    title = "OK Computer",
                    artUrl = "https://img/ok",
                    year = "1997",
                ),
            ),
            linkedChannelId = "UCdddddddddddddddddddddd",
        )

        val result = repo.getChannelVideos("https://www.youtube.com/channel/$topicId")
        assertThat(result.channelName).isEqualTo("Radiohead")
        assertThat(result.avatarUrl).isEqualTo("https://img/ytm")
        assertThat(result.tracks.map { it.id }).containsExactly("yt_topsong0001")
        assertThat(result.albums).hasSize(1)
        assertThat(result.albums.first().browseId).isEqualTo("MPREb_album0001")
        assertThat(result.nextPage).isNull()
        coVerify(exactly = 0) { client.browse("UCdddddddddddddddddddddd", any()) }
    }

    @Test fun `getChannelVideos follows linked official channel when YTM has no songs`() = runTest {
        val topicId = "UCeeeeeeeeeeeeeeeeeeeeee"
        val linkedId = "UCffffffffffffffffffffff"
        coEvery { client.browse(topicId, any()) } returns empty
        every { channelParser.parse(empty, topicId) } returns YouTubeChannelParser.ChannelPage(
            tracks = emptyList(),
            channelName = "Artist - Topic",
            continuation = null,
            avatarUrl = "https://img/topic",
        )
        coEvery { ytmClient.browse(topicId) } returns empty
        every { ytmArtistParser.parse(empty, topicId) } returns YouTubeMusicArtistParser.ArtistPage(
            name = "Artist",
            avatarUrl = "https://img/ytm",
            songs = emptyList(),
            albums = emptyList(),
            linkedChannelId = linkedId,
        )
        coEvery { client.browse(linkedId, any()) } returns empty
        every { channelParser.parse(empty, linkedId) } returns YouTubeChannelParser.ChannelPage(
            tracks = listOf(track("official001")),
            channelName = "Artist",
            continuation = "LINK_C1",
            avatarUrl = "https://img/official",
        )

        val result = repo.getChannelVideos("https://www.youtube.com/channel/$topicId")
        assertThat(result.tracks.map { it.id }).containsExactly("yt_official001")
        assertThat(result.channelName).isEqualTo("Artist - Topic")
        assertThat(result.avatarUrl).isEqualTo("https://img/topic")
        assertThat(result.nextPage).isNotNull()
    }

    @Test fun `search filters track results when filter is songs`() = runTest {
        val mixed = listOf(
            SearchResult(SearchResultType.YOUTUBE_TRACK, "v", "u1", null, null, null, null, null),
            SearchResult(SearchResultType.YOUTUBE_PLAYLIST, "p", "u2", null, null, null, null, null),
            SearchResult(SearchResultType.YOUTUBE_ARTIST, "a", "u3", null, null, null, null, null),
        )
        coEvery { client.search(query = "q", params = "EgIQAQ%3D%3D") } returns empty
        every { searchParser.parse(empty) } returns YouTubeSearchParser.Page(mixed, "TOK")

        val (out, page) = repo.search("q", filter = "songs")
        assertThat(out).hasSize(1)
        assertThat(out.first().type).isEqualTo(SearchResultType.YOUTUBE_TRACK)
        assertThat(page).isEqualTo("TOK")
    }

    @Test fun `search continuation uses searchContinuation and parseContinuation`() = runTest {
        val page2 = listOf(
            SearchResult(SearchResultType.YOUTUBE_TRACK, "v2", "u2", null, null, null, null, null),
        )
        coEvery { client.searchContinuation("TOK1") } returns empty
        every { searchParser.parseContinuation(empty) } returns YouTubeSearchParser.Page(page2, "TOK2")

        val (out, page) = repo.search("q", filter = null, page = "TOK1")
        assertThat(out).hasSize(1)
        assertThat(page).isEqualTo("TOK2")
        coVerify(exactly = 0) { client.search(any(), any()) }
        coVerify { client.searchContinuation("TOK1") }
    }

    @Test fun `search sends playlist filter params for playlists chip`() = runTest {
        coEvery { client.search(query = "q", params = "EgIQAw%3D%3D") } returns empty
        every { searchParser.parse(empty) } returns YouTubeSearchParser.Page(emptyList(), null)

        repo.search("q", filter = "playlists")
        coVerify { client.search(query = "q", params = "EgIQAw%3D%3D") }
    }

    @Test fun `search sends channel filter params for artists chip`() = runTest {
        coEvery { client.search(query = "q", params = "EgIQAg%3D%3D") } returns empty
        every { searchParser.parse(empty) } returns YouTubeSearchParser.Page(emptyList(), null)

        repo.search("q", filter = "artists")
        coVerify { client.search(query = "q", params = "EgIQAg%3D%3D") }
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

    @Test fun `getPlaylistTracks routes RD mixes through getMixPage`() = runTest {
        every { playlistParser.extractMixSeedVideoId("RDdQw4w9WgXcQ") } returns "dQw4w9WgXcQ"
        coEvery { client.next(videoId = "dQw4w9WgXcQ", playlistId = "RDdQw4w9WgXcQ") } returns empty
        every {
            playlistParser.parseMix(empty, "RDdQw4w9WgXcQ", startIndex = 1, seenVideoIds = emptySet())
        } returns YouTubePlaylistParser.MixPage(
            tracks = listOf(track("mixvid00001")),
            title = "Mix Title",
            continuation = null,
        )

        val result = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=RDdQw4w9WgXcQ")
        assertThat(result.title).isEqualTo("Mix Title")
        assertThat(result.tracks.map { it.id }).containsExactly("yt_mixvid00001")
        coVerify(exactly = 0) { client.browse(any(), any()) }
    }

    @Test fun `getPlaylistTracks strips RDAMPL and browses inner OLAK playlist`() = runTest {
        coEvery { client.browse("VLOLAK5uy_albumradio01") } returns empty
        every { playlistParser.parse(empty, "OLAK5uy_albumradio01") } returns YouTubePlaylistParser.PlaylistPage(
            tracks = listOf(track("albumsong01")),
            title = "Album Radio Source",
            continuation = null,
        )

        val result = repo.getPlaylistTracks(
            "https://www.youtube.com/playlist?list=RDAMPLOLAK5uy_albumradio01",
        )
        assertThat(result.title).isEqualTo("Album Radio Source")
        assertThat(result.tracks.map { it.id }).containsExactly("yt_albumsong01")
        coVerify(exactly = 0) { client.next(any(), any(), any(), any()) }
    }

    @Test fun `getPlaylistTracks strips RDAMPL and keeps inner RDCLAK on mix path`() = runTest {
        every { playlistParser.extractMixSeedVideoId("RDCLAK5uy_inner000001") } returns null
        every { playlistParser.isSeedlessMixId("RDCLAK5uy_inner000001") } returns true
        coEvery { client.next(videoId = null, playlistId = "RDCLAK5uy_inner000001") } returns empty
        every {
            playlistParser.parseMix(empty, "RDCLAK5uy_inner000001", startIndex = 1, seenVideoIds = emptySet())
        } returns YouTubePlaylistParser.MixPage(
            tracks = listOf(track("claksong001")),
            title = "Curated Mix",
            continuation = null,
        )

        val result = repo.getPlaylistTracks(
            "https://www.youtube.com/playlist?list=RDAMPLRDCLAK5uy_inner000001",
        )
        assertThat(result.title).isEqualTo("Curated Mix")
        assertThat(result.tracks.map { it.id }).containsExactly("yt_claksong001")
    }

    @Test fun `getMixPage fails clearly for unsupported seedless RD ids`() = runTest {
        every { playlistParser.extractMixSeedVideoId("RDzzzzunsupported") } returns null
        every { playlistParser.isSeedlessMixId("RDzzzzunsupported") } returns false

        val ex = runCatching {
            repo.getMixPage("https://www.youtube.com/playlist?list=RDzzzzunsupported")
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("Unsupported Mix")
        coVerify(exactly = 0) { client.next(any(), any(), any(), any()) }
    }

    @Test fun `getPlaylistTracks fails when mix page is empty`() = runTest {
        every { playlistParser.extractMixSeedVideoId("RDempty00001") } returns "empty000001"
        coEvery { client.next(videoId = "empty000001", playlistId = "RDempty00001") } returns empty
        every {
            playlistParser.parseMix(empty, "RDempty00001", startIndex = 1, seenVideoIds = emptySet())
        } returns YouTubePlaylistParser.MixPage(tracks = emptyList(), title = "Empty Mix", continuation = null)

        val ex = runCatching {
            repo.getPlaylistTracks("https://www.youtube.com/playlist?list=RDempty00001")
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("no tracks")
    }

    @Test fun `getPlaylistTracks fails on unviewable alertRenderer`() = runTest {
        val alert = kotlinx.serialization.json.Json.parseToJsonElement(
            """
            {"alerts":[{"alertRenderer":{"type":"ERROR",
              "text":{"runs":[{"text":"This playlist type is unviewable."}]}}}]}
            """.trimIndent(),
        )
        coEvery { client.browse("VLprivatePL01") } returns alert
        val ex = runCatching {
            repo.getPlaylistTracks("https://www.youtube.com/playlist?list=privatePL01")
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("unviewable")
        coVerify(exactly = 0) { playlistParser.parse(any(), any()) }
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
