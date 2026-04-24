package com.dustvalve.next.android.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DustvalveAlbumScraperTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveAlbumScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveAlbumScraper(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    @Test fun `scrapeAlbum rejects non-https url`() = runTest {
        try {
            scraper.scrapeAlbum("http://example.com/album/foo")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Invalid")
        }
    }

    @Test fun `scrapeAlbum parses TralbumData script`() = runTest {
        val albumUrl = setup.url("/album/foo")
        val html = """
            <html>
              <script>var TralbumData = {
                "url":"$albumUrl",
                "current":{"title":"My Album","artist":"The Band","band_id":1,"release_date":"2020-01-01","about":"A record"},
                "trackinfo":[
                  {"id":1,"title":"Track One","track_num":1,"duration":225.0,"file":{"mp3-128":"https://s/1.mp3"}},
                  {"id":2,"title":"Track Two","track_num":2,"duration":200.0,"file":{"mp3-128":"https://s/2.mp3"}}
                ],
                "art_id":42,
                "item_type":"album",
                "album_url":null
              };</script>
              <a class="tag">rock</a><a class="tag">indie</a>
            </html>
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(html))

        val album = scraper.scrapeAlbum(albumUrl)

        assertThat(album.title).isEqualTo("My Album")
        assertThat(album.artist).isEqualTo("The Band")
        assertThat(album.releaseDate).isEqualTo("2020-01-01")
        assertThat(album.about).isEqualTo("A record")
        assertThat(album.artUrl).isEqualTo("https://f4.bcbits.com/img/a42_10.jpg")
        assertThat(album.tags).containsExactly("rock", "indie").inOrder()
        assertThat(album.tracks).hasSize(2)
        assertThat(album.tracks[0].title).isEqualTo("Track One")
        assertThat(album.tracks[0].streamUrl).isEqualTo("https://s/1.mp3")
        assertThat(album.tracks[1].trackNumber).isEqualTo(2)
    }

    @Test fun `scrapeAlbum parses data-tralbum attribute fallback`() = runTest {
        val albumUrl = setup.url("/album/foo")
        val json = """{&quot;url&quot;:&quot;$albumUrl&quot;,&quot;current&quot;:{&quot;title&quot;:&quot;Via Attr&quot;,&quot;artist&quot;:&quot;Band&quot;,&quot;band_id&quot;:1},&quot;trackinfo&quot;:[],&quot;art_id&quot;:1,&quot;item_type&quot;:&quot;album&quot;}"""
        val html = """<html><div data-tralbum="$json"></div></html>"""
        setup.server.enqueue(MockResponse().setBody(html))

        val album = scraper.scrapeAlbum(albumUrl)
        assertThat(album.title).isEqualTo("Via Attr")
    }

    @Test fun `scrapeAlbum throws when TralbumData missing`() = runTest {
        setup.server.enqueue(MockResponse().setBody("<html>no data here</html>"))
        try {
            scraper.scrapeAlbum(setup.url("/album/missing"))
            error("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("TralbumData")
        }
    }

    @Test fun `scrapeAlbum http error throws IOException`() = runTest {
        setup.server.enqueue(MockResponse().setResponseCode(404))
        try {
            scraper.scrapeAlbum(setup.url("/album/404"))
            error("expected IOException")
        } catch (e: IOException) {
            assertThat(e.message).contains("404")
        }
    }

    @Test fun `scrapeAlbum track-type redirects to album_url`() = runTest {
        val trackUrl = setup.url("/track/one")
        val resolvedAlbumUrl = setup.url("/album/foo")
        val trackHtml = """<html><script>var TralbumData = {
            "url":"$trackUrl","current":{"title":"T","artist":"A","band_id":1},
            "trackinfo":[],"art_id":1,"item_type":"track","album_url":"$resolvedAlbumUrl"
        };</script></html>"""
        val albumHtml = """<html><script>var TralbumData = {
            "url":"$resolvedAlbumUrl","current":{"title":"Redirected Album","artist":"A","band_id":1},
            "trackinfo":[],"art_id":2,"item_type":"album"
        };</script></html>"""
        setup.server.enqueue(MockResponse().setBody(trackHtml))
        setup.server.enqueue(MockResponse().setBody(albumHtml))

        val album = scraper.scrapeAlbum(trackUrl)
        assertThat(album.title).isEqualTo("Redirected Album")
        assertThat(album.artUrl).isEqualTo("https://f4.bcbits.com/img/a2_10.jpg")
    }

    @Test fun `scrapeAlbum redirect limit enforced`() = runTest {
        val url = setup.url("/track/loop")
        val trackHtml = """<html><script>var TralbumData = {
            "url":"$url","current":{"title":"T","artist":"A","band_id":1},
            "trackinfo":[],"art_id":1,"item_type":"track","album_url":"$url"
        };</script></html>"""
        // Enqueue enough redirects to exhaust the counter
        repeat(5) { setup.server.enqueue(MockResponse().setBody(trackHtml)) }

        try {
            scraper.scrapeAlbum(url, maxRedirects = 1)
            error("expected IOException")
        } catch (e: IOException) {
            assertThat(e.message).contains("redirects")
        }
    }

    @Test fun `scrapeAlbum uses band_url when provided`() = runTest {
        val albumUrl = setup.url("/album/foo")
        val html = """<html><script>var TralbumData = {
            "url":"$albumUrl",
            "current":{"title":"Album","artist":"A","band_id":1,"band_url":"https://other.bandcamp.com"},
            "trackinfo":[],"art_id":1,"item_type":"album"
        };</script></html>"""
        setup.server.enqueue(MockResponse().setBody(html))
        val album = scraper.scrapeAlbum(albumUrl)
        assertThat(album.artistUrl).isEqualTo("https://other.bandcamp.com")
    }

    @Test fun `scrapeAlbum stable id is deterministic`() = runTest {
        val albumUrl = setup.url("/album/stable")
        val html = """<html><script>var TralbumData = {
            "url":"$albumUrl","current":{"title":"A","artist":"X","band_id":1},
            "trackinfo":[],"art_id":1,"item_type":"album"
        };</script></html>"""
        setup.server.enqueue(MockResponse().setBody(html))
        setup.server.enqueue(MockResponse().setBody(html))

        val a = scraper.scrapeAlbum(albumUrl)
        val b = scraper.scrapeAlbum(albumUrl)
        assertThat(a.id).isEqualTo(b.id)
        assertThat(a.id).hasLength(32) // 16 hex bytes = 32 chars
    }

    /**
     * Regression: a.a. williams' `solstice` ships several tracks with
     * `"id": null` in the tralbumData JSON. Before this fix every such track
     * deserialized to `id = 0L`, producing duplicate Track.id values
     * (`<albumId>_0`) which crashed the track LazyColumn on-device with
     * `IllegalArgumentException: Key "..._0" was already used`.
     */
    @Test fun `scrapeAlbum gives unique Track ids even when Bandcamp ships null track ids`() = runTest {
        val albumUrl = setup.url("/album/solstice")
        val html = """
            <html>
              <script>var TralbumData = {
                "url":"$albumUrl",
                "current":{"title":"Solstice","artist":"a.a. williams","band_id":1},
                "trackinfo":[
                  {"id":999820091,"title":"Poison","track_num":1,"duration":0.0},
                  {"id":58739464, "title":"Wolves","track_num":2,"duration":0.0},
                  {"id":null,     "title":"Little By Little","track_num":3,"duration":0.0},
                  {"id":null,     "title":"Outlines","track_num":5,"duration":0.0},
                  {"id":null,     "title":"I've Seen Enough","track_num":6,"duration":0.0},
                  {"id":null,     "title":"The Veil","track_num":7,"duration":0.0}
                ],
                "art_id":42,"item_type":"album"
              };</script>
            </html>
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(html))

        val album = scraper.scrapeAlbum(albumUrl)

        assertThat(album.tracks).hasSize(6)
        val ids = album.tracks.map { it.id }
        assertThat(ids.toSet()).hasSize(6)
        // The null-id tracks must fall back to a positional key (not "_0" / "_null").
        val nullIdTrackKeys = album.tracks.drop(2).map { it.id }
        for (key in nullIdTrackKeys) {
            assertThat(key).doesNotContain("_0")
            assertThat(key).doesNotContain("_null")
        }
    }
}
