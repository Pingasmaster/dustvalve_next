package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.SearchResultType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        scraper = DustvalveSearchScraper(setup.client, UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    @Test fun `non-bandcamp types return empty immediately`() = runTest {
        for (t in listOf(
            SearchResultType.LOCAL_TRACK,
            SearchResultType.YOUTUBE_TRACK,
            SearchResultType.YOUTUBE_ALBUM,
            SearchResultType.YOUTUBE_ARTIST,
            SearchResultType.YOUTUBE_PLAYLIST,
        )) {
            assertThat(scraper.search("q", 1, t)).isEmpty()
        }
        assertThat(setup.server.requestCount).isEqualTo(0)
    }

    @Test fun `parses artist album and track results from json`() = runTest {
        val body = """
            {"auto":{"results":[
              {"type":"a","name":"My Album","band_name":"A Band",
               "item_url_path":"https://a.bandcamp.com/album/x",
               "item_url_root":"https://a.bandcamp.com",
               "img":"https://cdn.bcbits.com/img/album1.jpg",
               "tag_names":["rock","indie"]},
              {"type":"t","name":"My Track","band_name":"A Band",
               "album_name":"My Album",
               "item_url_path":"https://a.bandcamp.com/track/y",
               "item_url_root":"https://a.bandcamp.com",
               "img":"https://cdn.bcbits.com/img/track1.jpg",
               "tag_names":[]},
              {"type":"b","name":"A Band",
               "item_url_root":"https://a.bandcamp.com",
               "img":"","tag_names":[]},
              {"type":"a","name":"Skipped relative",
               "item_url_path":"/relative/path","tag_names":[]},
              {"type":"x","name":"Unknown type",
               "item_url_path":"https://a.bandcamp.com/foo"}
            ]}}
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(body))

        val results = scraper.search("foo", 1, null)

        // Five items in JSON; one filtered (relative URL), one filtered (unknown type) -> 3 results.
        assertThat(results).hasSize(3)

        val album = results.single { it.type == SearchResultType.ALBUM }
        assertThat(album.name).isEqualTo("My Album")
        assertThat(album.url).isEqualTo("https://a.bandcamp.com/album/x")
        assertThat(album.artist).isEqualTo("A Band")
        assertThat(album.imageUrl).isEqualTo("https://cdn.bcbits.com/img/album1.jpg")
        assertThat(album.genre).isEqualTo("rock, indie")
        assertThat(album.releaseDate).isNull()

        val track = results.single { it.type == SearchResultType.TRACK }
        assertThat(track.artist).isEqualTo("A Band")
        assertThat(track.album).isEqualTo("My Album")
        assertThat(track.url).isEqualTo("https://a.bandcamp.com/track/y")

        val artist = results.single { it.type == SearchResultType.ARTIST }
        assertThat(artist.name).isEqualTo("A Band")
        assertThat(artist.url).isEqualTo("https://a.bandcamp.com")
        // Empty img string -> imageUrl null
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

    @Test fun `page above 1 returns empty without hitting server`() = runTest {
        val results = scraper.search("q", page = 2, type = null)
        assertThat(results).isEmpty()
        assertThat(setup.server.requestCount).isEqualTo(0)
    }

    @Test fun `posts search_text and search_filter for album-typed query`() = runTest {
        setup.server.enqueue(MockResponse().setBody("""{"auto":{"results":[]}}"""))
        scraper.search("hello world", 1, SearchResultType.ALBUM)
        val req = setup.server.takeRequest()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.path).isEqualTo("/api/bcsearch_public_api/1/autocomplete_elastic")
        val sent = req.body.readUtf8()
        assertThat(sent).contains("\"search_text\":\"hello world\"")
        assertThat(sent).contains("\"search_filter\":\"a\"")
        assertThat(sent).contains("\"full_page\":true")
    }

    @Test fun `null type sends empty search_filter`() = runTest {
        setup.server.enqueue(MockResponse().setBody("""{"auto":{"results":[]}}"""))
        scraper.search("q", 1, null)
        val sent = setup.server.takeRequest().body.readUtf8()
        assertThat(sent).contains("\"search_filter\":\"\"")
    }
}
