package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.SearchResultType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test

class DustvalveSearchScraperTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveSearchScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveSearchScraper(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    @Test fun `youtube and spotify types return empty immediately`() = runTest {
        for (t in listOf(
            SearchResultType.LOCAL_TRACK,
            SearchResultType.YOUTUBE_TRACK, SearchResultType.YOUTUBE_ALBUM,
            SearchResultType.YOUTUBE_ARTIST, SearchResultType.YOUTUBE_PLAYLIST,
            SearchResultType.SPOTIFY_TRACK, SearchResultType.SPOTIFY_ALBUM,
            SearchResultType.SPOTIFY_ARTIST, SearchResultType.SPOTIFY_PLAYLIST,
        )) {
            assertThat(scraper.search("q", 1, t)).isEmpty()
        }
        assertThat(setup.server.requestCount).isEqualTo(0)
    }

    @Test fun `parses artist album and track results`() = runTest {
        val html = """
            <html><body>
              <li class="searchresult">
                <div class="art"><img src="//cdn.bcbits.com/img/album1.jpg"/></div>
                <div class="result-info">
                  <div class="itemtype">ALBUM</div>
                  <div class="heading"><a href="https://a.bandcamp.com/album/x">My Album</a></div>
                  <div class="subhead">by A Band</div>
                  <div class="released">released 2020</div>
                  <div class="genre">genre: rock</div>
                </div>
              </li>
              <li class="searchresult">
                <div class="art"><img src="https://cdn.bcbits.com/img/track1.jpg"/></div>
                <div class="result-info">
                  <div class="itemtype">TRACK</div>
                  <div class="heading"><a href="https://a.bandcamp.com/track/y">My Track</a></div>
                  <div class="subhead">by Some from Trouble Artist from Some Album</div>
                </div>
              </li>
              <li class="searchresult">
                <div class="art"><img src=""/></div>
                <div class="result-info">
                  <div class="itemtype">ARTIST</div>
                  <div class="heading"><a href="https://a.bandcamp.com">A Band</a></div>
                </div>
              </li>
              <li class="searchresult">
                <div class="result-info">
                  <div class="itemtype">ALBUM</div>
                  <div class="heading"><a href="/relative/path">Skipped</a></div>
                </div>
              </li>
            </body></html>
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(html))

        val results = scraper.search("foo", 1, null)

        // Four tiles in HTML; one filtered out (relative URL) → three results expected.
        assertThat(results).hasSize(3)

        val album = results.single { it.type == SearchResultType.ALBUM }
        assertThat(album.name).isEqualTo("My Album")
        assertThat(album.url).isEqualTo("https://a.bandcamp.com/album/x")
        assertThat(album.artist).isEqualTo("A Band")
        assertThat(album.imageUrl).isEqualTo("https://cdn.bcbits.com/img/album1.jpg") // protocol-relative expanded
        assertThat(album.releaseDate).isEqualTo("2020")
        assertThat(album.genre).isEqualTo("rock")

        val track = results.single { it.type == SearchResultType.TRACK }
        // "by Some from Trouble Artist from Some Album" → uses lastIndexOf " from "
        // so artist = "Some from Trouble Artist", album = "Some Album"
        assertThat(track.artist).isEqualTo("Some from Trouble Artist")
        assertThat(track.album).isEqualTo("Some Album")

        val artist = results.single { it.type == SearchResultType.ARTIST }
        assertThat(artist.name).isEqualTo("A Band")
        // Empty src skipped → imageUrl null
        assertThat(artist.imageUrl).isNull()
        assertThat(artist.artist).isNull()
    }

    @Test fun `http error throws IOException`() = runTest {
        setup.server.enqueue(MockResponse().setResponseCode(500))
        try {
            scraper.search("q", 1, null)
            error("expected IOException")
        } catch (e: java.io.IOException) {
            assertThat(e.message).contains("500")
        }
    }

    @Test fun `page parameter clamped into the request url`() = runTest {
        setup.server.enqueue(MockResponse().setBody("<html></html>"))
        scraper.search("q", page = 5000, type = null) // should clamp to 1000
        val req = setup.server.takeRequest()
        assertThat(req.path).contains("page=1000")
    }

    @Test fun `page below 1 clamps to 1`() = runTest {
        setup.server.enqueue(MockResponse().setBody("<html></html>"))
        scraper.search("q", page = 0, type = null)
        val req = setup.server.takeRequest()
        assertThat(req.path).contains("page=1")
    }

    @Test fun `query encoded into url`() = runTest {
        setup.server.enqueue(MockResponse().setBody("<html></html>"))
        scraper.search("hello world", 1, null)
        val req = setup.server.takeRequest()
        assertThat(req.path).contains("q=hello+world")
    }
}
