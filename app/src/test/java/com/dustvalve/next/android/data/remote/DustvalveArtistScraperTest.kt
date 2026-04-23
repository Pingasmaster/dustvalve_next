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

    @Test fun `bio strips peekaboo show-more overlay (Angine de Poitrine fixture)`() = runTest {
        // Real captured /music page snippet from anginedepoitrine.bandcamp.com.
        // Bandcamp's bcTruncate JS plugin renders a "...more" overlay
        // (`peekaboo-link` + `peekaboo-ellipsis`) inside `<p id="bio-text">`;
        // a naive `.text()` includes both the hidden long-form text AND the
        // literal "...more" link tail, which surfaced in the artist screen.
        val classLoader = checkNotNull(this::class.java.classLoader)
        val html = checkNotNull(
            classLoader.getResourceAsStream("fixtures/bandcamp/artist_angine_de_poitrine.html")
        ).bufferedReader().use { it.readText() }
        setup.server.enqueue(MockResponse().setBody(html))

        val artist = scraper.scrapeArtist(setup.url(""))

        assertThat(artist.name).isEqualTo("Angine de Poitrine")
        val bio = checkNotNull(artist.bio)
        // The full bio (including the previously-hidden peekaboo-text portion)
        // must round-trip without the show-more overlay tail.
        assertThat(bio).contains("crise cardiaque")
        // The "...more" overlay must be gone — neither the literal word "more"
        // tail nor the leading ellipsis from peekaboo-ellipsis.
        assertThat(bio).doesNotContain("...more")
        assertThat(bio).doesNotContain("... more")
        assertThat(bio.trimEnd()).doesNotMatch(".*\\bmore\\s*$")
        assertThat(bio).doesNotContain("...")
    }

    @Test fun `scrapeArtist sends a desktop User-Agent to bandcamp (not the shared mobile UA)`() = runTest {
        // Bandcamp serves a heavily-stripped HTML to mobile UAs (no
        // band-name-location / band-photo / bio markup) — see the
        // `artist_taylor_moore_mobile_layout.html` fixture for proof. The
        // shared OkHttpClient injected app-wide carries a mobile UA via
        // NetworkModule's userAgentInterceptor, so the artist scraper
        // MUST override it. Assert the override survives the interceptor
        // chain by inspecting the request MockWebServer received.
        setup.server.enqueue(MockResponse().setBody("<html><body></body></html>"))
        scraper.scrapeArtist(setup.url(""))
        val recorded = setup.server.takeRequest()
        val ua = recorded.headers["User-Agent"] ?: ""
        // A "Mobile" UA fragment is the giveaway — Bandcamp gates the stripped
        // layout on its presence. We send a desktop Chrome string instead.
        assertThat(ua).doesNotContain("Mobile")
        assertThat(ua).contains("Chrome")
    }

    @Test fun `scrapes single-album artist (Taylor Moore Music) without dropping name+photo+bio`() = runTest {
        // Captured directly from https://taylormooremusic.bandcamp.com/ — that
        // root URL redirects to the artist's only album page, and our /music
        // re-fetch then redirects to the same album page. The rendered HTML
        // still carries band-name-location, band-photo, and the bio block —
        // so the scraper must extract them even when the album list is empty
        // (no `.music-grid-item` because the discography is the very page
        // we're parsing).
        val classLoader = checkNotNull(this::class.java.classLoader)
        val html = checkNotNull(
            classLoader.getResourceAsStream("fixtures/bandcamp/artist_taylor_moore_single_album.html")
        ).bufferedReader().use { it.readText() }
        setup.server.enqueue(MockResponse().setBody(html))
        // Our scraper might trigger the "/music" re-fetch on this URL because
        // its path matches /album/... — enqueue the same fixture again so the
        // re-fetch succeeds.
        setup.server.enqueue(MockResponse().setBody(html))

        val artist = scraper.scrapeArtist(setup.url("/album/single-thing"))

        assertThat(artist.name).isEqualTo("Taylor Moore")
        assertThat(artist.location).isEqualTo("Brooklyn, New York")
        assertThat(artist.imageUrl).isEqualTo("https://f4.bcbits.com/img/0044448040_21.jpg")
        assertThat(artist.bio).isNotNull()
        assertThat(artist.bio!!).isNotEmpty()
        // No music-grid in the page — the empty-state UI is acceptable, but
        // the band metadata must still be there.
        assertThat(artist.albums).isEmpty()
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
