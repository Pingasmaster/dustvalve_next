package com.dustvalve.next.android.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DustvalveDiscoverScraperTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveDiscoverScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveDiscoverScraper(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    @Test fun `parses album results and filters non-album`() = runTest {
        val body = """
            {
              "results": [
                {"result_type":"a","title":"Album 1","band_name":"Band 1","primary_image":{"image_id":11},"item_url":"https://x.bandcamp.com/album/1","band_url":"https://x.bandcamp.com","id":1},
                {"result_type":"s","title":"Skip me","band_name":"B","item_url":"https://x.bandcamp.com/album/2","id":2},
                {"result_type":"a","title":"Album 3","band_name":"Band 3","item_image_id":33,"item_url":"https://y.bandcamp.com/album/3","id":3},
                {"result_type":"a","title":"NoImage","band_name":"B","item_url":"https://z.bandcamp.com/album/4","id":4}
              ],
              "result_count": 4,
              "cursor": "next-cursor"
            }
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(body))

        val r = scraper.discover(genre = "rock", cursor = null)
        assertThat(r.cursor).isEqualTo("next-cursor")
        assertThat(r.albums).hasSize(3)
        assertThat(r.albums.map { it.title }).containsExactly("Album 1", "Album 3", "NoImage").inOrder()
        assertThat(r.albums[0].artUrl).isEqualTo("https://f4.bcbits.com/img/a11_10.jpg")
        assertThat(r.albums[1].artUrl).isEqualTo("https://f4.bcbits.com/img/a33_10.jpg")
        assertThat(r.albums[2].artUrl).isEqualTo("") // no image id
    }

    @Test fun `uses album_artist when present`() = runTest {
        val body = """{
            "results":[{"result_type":"a","title":"T","band_name":"Band","album_artist":"Actual Artist","item_url":"https://x.bandcamp.com/album/1","id":1}],
            "result_count":1,"cursor":null
        }""".trimIndent()
        setup.server.enqueue(MockResponse().setBody(body))
        val r = scraper.discover()
        assertThat(r.albums.single().artist).isEqualTo("Actual Artist")
    }

    @Test fun `api special error throws`() = runTest {
        setup.server.enqueue(MockResponse().setBody("""{"__api_special__":true}"""))
        try {
            scraper.discover()
            error("expected IOException")
        } catch (e: IOException) {
            assertThat(e.message).contains("Dustvalve API error")
        }
    }

    @Test fun `http error throws`() = runTest {
        setup.server.enqueue(MockResponse().setResponseCode(503))
        try {
            scraper.discover()
            error("expected IOException")
        } catch (e: IOException) {
            assertThat(e.message).contains("503")
        }
    }

    @Test fun `genre and cursor forwarded in request body`() = runTest {
        setup.server.enqueue(MockResponse().setBody("""{"results":[],"result_count":0,"cursor":null}"""))
        scraper.discover(genre = "metal", cursor = "abc")
        val req = setup.server.takeRequest()
        val body = req.body.readUtf8()
        assertThat(body).contains("\"metal\"")
        assertThat(body).contains("\"cursor\":\"abc\"")
    }
}
