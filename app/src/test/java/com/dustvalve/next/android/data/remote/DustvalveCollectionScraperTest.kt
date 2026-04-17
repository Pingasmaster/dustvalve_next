package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test

class DustvalveCollectionScraperTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveCollectionScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveCollectionScraper(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    @Test fun `parses albums purchase info pagination`() = runTest {
        val body = """
            {
              "items": [
                {"item_type":"album","item_title":"Album 1","item_url":"https://x.bandcamp.com/album/1","item_art_id":11,"band_name":"B1","band_url":"https://x.bandcamp.com","sale_item_id":100,"sale_item_type":"a"},
                {"item_type":"track","item_title":"Track 1","item_url":"https://x.bandcamp.com/track/1","item_art_id":22,"band_name":"B1","band_url":"https://x.bandcamp.com"},
                {"item_type":"merch","item_title":"Shirt","item_url":"https://x.bandcamp.com/merch/s","band_name":"B1","band_url":"https://x.bandcamp.com"},
                {"item_type":"album","item_title":"Bad Url","item_url":"http://not-https.com/album","band_name":"B","band_url":""},
                {"item_type":"album","item_title":"Empty Url","item_url":"","band_name":"B","band_url":""}
              ],
              "more_available": true,
              "last_token": "token-xyz"
            }
        """.trimIndent()
        setup.server.enqueue(MockResponse().setBody(body))

        val result = scraper.getCollection(fanId = 42L)
        // merch, non-https, and empty url filtered out → 2 left
        assertThat(result.albums).hasSize(2)
        assertThat(result.albums.map { it.title }).containsExactly("Album 1", "Track 1").inOrder()
        assertThat(result.albums[0].artUrl).isEqualTo("https://f4.bcbits.com/img/a11_10.jpg")
        assertThat(result.albums[0].purchaseInfo).isEqualTo(PurchaseInfo(100L, "a"))
        assertThat(result.albums[1].purchaseInfo).isNull()
        assertThat(result.hasMore).isTrue()
        assertThat(result.lastToken).isEqualTo("token-xyz")

        val idAlbum1 = result.albums[0].id
        assertThat(result.purchaseInfo[idAlbum1]).isEqualTo(PurchaseInfo(100L, "a"))
    }

    @Test fun `default older_than_token sent when null`() = runTest {
        setup.server.enqueue(MockResponse().setBody("""{"items":[],"more_available":false}"""))
        scraper.getCollection(fanId = 1L)
        val req = setup.server.takeRequest()
        val body = req.body.readUtf8()
        assertThat(body).contains("\"older_than_token\":\"9999999999::a::\"")
    }

    @Test fun `custom token forwarded`() = runTest {
        setup.server.enqueue(MockResponse().setBody("""{"items":[],"more_available":false}"""))
        scraper.getCollection(fanId = 1L, olderThanToken = "custom")
        val body = setup.server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"older_than_token\":\"custom\"")
    }
}
