package com.dustvalve.next.android.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test

class DustvalveArtistScraperTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveArtistScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveArtistScraper(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    @Test fun `rejects http url`() = runTest {
        try {
            scraper.scrapeArtist("http://foo.bandcamp.com")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Invalid")
        }
    }

    @Test fun `parses name bio location image and albums`() = runTest {
        val html = """
            <html>
              <body>
                <p id="band-name-location">
                  <span class="title">My Band</span>
                  <span class="location">Berlin</span>
                </p>
                <div class="band-photo"><img src="https://cdn/img/band.jpg"/></div>
                <div class="signed-out-artists-bio-text">Some bio text</div>
                <div id="music-grid">
                  <div class="music-grid-item">
                    <a href="/album/one">
                      <img src="https://cdn/img/album1.jpg"/>
                      <p class="title">Album One</p>
                    </a>
                  </div>
                  <div class="music-grid-item">
                    <a href="/album/two">
                      <img src="/img/0.gif" data-original="https://cdn/img/album2.jpg"/>
                      <p class="title">Album Two</p>
                    </a>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(html))

        val artist = scraper.scrapeArtist(setup.url(""))
        assertThat(artist.name).isEqualTo("My Band")
        assertThat(artist.location).isEqualTo("Berlin")
        assertThat(artist.bio).isEqualTo("Some bio text")
        assertThat(artist.imageUrl).isEqualTo("https://cdn/img/band.jpg")
        assertThat(artist.albums).hasSize(2)
        assertThat(artist.albums[0].title).isEqualTo("Album One")
        assertThat(artist.albums[0].artUrl).isEqualTo("https://cdn/img/album1.jpg")
        assertThat(artist.albums[1].title).isEqualTo("Album Two")
        // Lazy-loaded album uses data-original, not the 0.gif placeholder
        assertThat(artist.albums[1].artUrl).isEqualTo("https://cdn/img/album2.jpg")
    }

    @Test fun `falls back to Unknown Artist when name missing`() = runTest {
        val html = """<html><body><div id="music-grid"></div></body></html>"""
        setup.server.enqueue(MockResponse().setBody(html))
        val artist = scraper.scrapeArtist(setup.url(""))
        assertThat(artist.name).isEqualTo("Unknown Artist")
        assertThat(artist.albums).isEmpty()
    }

    @Test fun `album redirect refetches music page`() = runTest {
        // First response is an album page (path /album/foo) — scraper should refetch /music
        val albumHtml = """<html><body></body></html>"""
        val musicHtml = """
            <html><body>
              <p id="band-name-location"><span class="title">Artist</span></p>
              <div id="music-grid"></div>
            </body></html>
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(albumHtml).addHeader("Content-Type", "text/html"))
        setup.server.enqueue(MockResponse().setBody(musicHtml))

        // Trigger the redirect path by requesting a URL that ends with /album/xxx.
        val artist = scraper.scrapeArtist(setup.url("/album/foo"))
        assertThat(artist.name).isEqualTo("Artist")
        // Two requests made: original + /music
        assertThat(setup.server.requestCount).isEqualTo(2)
    }

    @Test fun `data-client-items fills missing album art`() = runTest {
        val albumUrl = setup.url("/album/two")
        val clientItemsJson =
            """[{"type":"album","title":"Album Two","artist":"A","art_id":999,"page_url":"$albumUrl","id":2}]"""
        val encoded = clientItemsJson
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        val html = """
            <html><body>
              <p id="band-name-location"><span class="title">X</span></p>
              <ol data-client-items="$encoded"></ol>
              <div id="music-grid">
                <div class="music-grid-item">
                  <a href="/album/two">
                    <p class="title">Album Two</p>
                  </a>
                </div>
              </div>
            </body></html>
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(html))

        val artist = scraper.scrapeArtist(setup.url(""))
        assertThat(artist.albums).hasSize(1)
        assertThat(artist.albums[0].artUrl).isEqualTo("https://f4.bcbits.com/img/a999_10.jpg")
    }

    @Test fun `stable id deterministic`() = runTest {
        val html = """<html><body>
            <p id="band-name-location"><span class="title">N</span></p>
            <div id="music-grid"></div>
        </body></html>""".trimIndent()
        setup.server.enqueue(MockResponse().setBody(html))
        setup.server.enqueue(MockResponse().setBody(html))
        val a = scraper.scrapeArtist(setup.url(""))
        val b = scraper.scrapeArtist(setup.url(""))
        assertThat(a.id).isEqualTo(b.id)
        assertThat(a.id).hasLength(32)
    }
}
