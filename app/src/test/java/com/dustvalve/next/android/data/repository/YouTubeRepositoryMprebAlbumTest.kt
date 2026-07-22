@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao
import com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeChannelParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeVisitorDataFetcher
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicAlbumResolver
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicVisitorDataFetcher
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * End-to-end repository-level regression for MPREb album URLs (real
 * clients + real parsers against MockWebServer; only the DAOs and the
 * unrelated YTM repository are mocked).
 *
 * YTM album search results emit `playlist?list=MPREb_...` URLs, but
 * `MPREb_` is an album BROWSE id - `/browse VLMPREb_...` is invalid and
 * every album open used to fail. The repository must first resolve the
 * album to its `audioPlaylistId` (OLAK5uy_...) via the YT Music browse
 * endpoint, then run the normal playlist path with the real id.
 */
class YouTubeRepositoryMprebAlbumTest {

    private lateinit var server: MockWebServer
    private lateinit var videoCache: YouTubeVideoCacheDao
    private lateinit var playlistCache: YouTubePlaylistCacheDao
    private lateinit var repo: YouTubeRepositoryImpl

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val dispatcher = UnconfinedTestDispatcher()

        val ytVisitor = mockk<YouTubeVisitorDataFetcher>()
        coEvery { ytVisitor.get() } returns YouTubeVisitorDataFetcher.VisitorConfig(
            visitorData = "VD_WEB",
            clientVersion = "2.20260421.00.00",
        )
        justRun { ytVisitor.invalidate() }
        val ytClient = TestableYouTubeClient(OkHttpClient(), ytVisitor, server.url("/").toString(), dispatcher)

        val ytmVisitor = mockk<YouTubeMusicVisitorDataFetcher>()
        coEvery { ytmVisitor.get() } returns YouTubeMusicVisitorDataFetcher.VisitorConfig(
            visitorData = "VD_MUSIC",
            clientVersion = "1.20260417.03.00",
        )
        justRun { ytmVisitor.invalidate() }
        val ytmClient = TestableYtmClient(OkHttpClient(), ytmVisitor, server.url("/").toString(), dispatcher)

        videoCache = mockk(relaxed = true)
        playlistCache = mockk(relaxed = true)
        coEvery { videoCache.getById(any()) } returns null
        coEvery { videoCache.getByIds(any()) } returns emptyList()
        coEvery { playlistCache.getById(any()) } returns null

        repo = YouTubeRepositoryImpl(
            client = ytClient,
            playerParser = YouTubePlayerParser(),
            searchParser = YouTubeSearchParser(),
            playlistParser = YouTubePlaylistParser(),
            channelParser = YouTubeChannelParser(),
            nextParser = YouTubeNextParser(),
            videoCache = videoCache,
            playlistCache = playlistCache,
            youTubeMusicRepository = mockk(relaxed = true),
            albumResolver = YouTubeMusicAlbumResolver(ytmClient),
            ioDispatcher = dispatcher,
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `MPREb playlist URL resolves the album then browses the real audio playlist`() = runTest {
        // 1st request: YTM /browse for the MPREb album -> carries the
        // audioPlaylistId somewhere in the response tree.
        server.enqueue(
            MockResponse().setBody(
                """
                {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{
                  "musicPlaylistShelfRenderer":{"playlistId":"OLAK5uy_realAudio01"}
                }}},
                 "header":{"musicDetailHeaderRenderer":{
                   "menu":{"menuRenderer":{"items":[
                     {"watchPlaylistEndpoint":{"audioPlaylistId":"OLAK5uy_realAudio01"}}
                   ]}}
                 }}}
                """.trimIndent(),
            ),
        )
        // 2nd request: MWEB /browse VLOLAK5uy_... -> playlist page.
        server.enqueue(
            MockResponse().setBody(
                """
                {"header":{"pageHeaderRenderer":{"pageTitle":"Album One"}},
                 "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
                   "sectionListRenderer":{"contents":[
                     {"itemSectionRenderer":{"contents":[
                       {"playlistVideoListRenderer":{"contents":[
                         {"playlistVideoRenderer":{
                           "videoId":"vidalbum0001",
                           "title":{"runs":[{"text":"Track One"}]},
                           "shortBylineText":{"runs":[{"text":"The Artist"}]},
                           "lengthSeconds":"200",
                           "thumbnail":{"thumbnails":[{"url":"https://t/1","width":100}]}
                         }}
                       ]}}
                     ]}}
                   ]}
                 }}}]}}}
                """.trimIndent(),
            ),
        )

        val (tracks, title) = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=MPREb_albumBrowse1")

        assertThat(title).isEqualTo("Album One")
        assertThat(tracks.map { it.id }).containsExactly("yt_vidalbum0001")
        assertThat(tracks.first().title).isEqualTo("Track One")

        assertThat(server.requestCount).isEqualTo(2)
        val albumRequest = server.takeRequest()
        assertThat(albumRequest.path).isEqualTo("/browse?prettyPrint=false")
        assertThat(albumRequest.headers["X-YouTube-Client-Name"]).isEqualTo("67") // WEB_REMIX
        assertThat(albumRequest.body.readUtf8()).contains("\"browseId\":\"MPREb_albumBrowse1\"")

        val playlistRequest = server.takeRequest()
        assertThat(playlistRequest.path).isEqualTo("/browse?prettyPrint=false")
        assertThat(playlistRequest.headers["X-YouTube-Client-Name"]).isEqualTo("2") // MWEB
        assertThat(playlistRequest.body.readUtf8()).contains("\"browseId\":\"VLOLAK5uy_realAudio01\"")

        // Snapshot cached under BOTH the real playlist id and the MPREb
        // alias, so re-opening the album URL becomes a direct cache hit.
        coVerify { playlistCache.insert(match { it.playlistId == "OLAK5uy_realAudio01" }) }
        coVerify { playlistCache.insert(match { it.playlistId == "MPREb_albumBrowse1" }) }
        // Bulk video write stays non-destructive (M17).
        coVerify { videoCache.insertAllIgnore(any()) }
    }

    @Test fun `album browse without an audioPlaylistId fails with a descriptive error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))

        val ex = runCatching {
            repo.getPlaylistTracks("https://www.youtube.com/playlist?list=MPREb_albumBrowse2")
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("MPREb_albumBrowse2")
        assertThat(server.requestCount).isEqualTo(1)
    }

    private class TestableYouTubeClient(
        sharedOkHttpClient: OkHttpClient,
        visitorDataFetcher: YouTubeVisitorDataFetcher,
        private val mockBaseUrl: String,
        dispatcher: CoroutineDispatcher,
    ) : YouTubeInnertubeClient(sharedOkHttpClient, visitorDataFetcher, dispatcher) {
        override val baseUrlWww: String get() = mockBaseUrl.trimEnd('/')
        override val baseUrlM: String get() = mockBaseUrl.trimEnd('/')
    }

    private class TestableYtmClient(
        sharedOkHttpClient: OkHttpClient,
        visitorDataFetcher: YouTubeMusicVisitorDataFetcher,
        private val mockBaseUrl: String,
        dispatcher: CoroutineDispatcher,
    ) : YouTubeMusicInnertubeClient(sharedOkHttpClient, visitorDataFetcher, dispatcher) {
        override val baseUrl: String get() = mockBaseUrl.trimEnd('/')
    }
}
