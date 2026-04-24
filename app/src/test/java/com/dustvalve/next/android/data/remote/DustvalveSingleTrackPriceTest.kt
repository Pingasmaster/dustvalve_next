package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.AlbumPrice
import com.dustvalve.next.android.domain.model.Album
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Verifies the per-track ("Buy a single track") price plumbing. We feed real
 * captured album / track HTML into the scraper through a TLS test server and
 * assert the resulting [Album.singleTrackPrice]. Single-track pages should
 * NOT surface a per-track price (it'd be redundant with the album price);
 * multi-track albums should surface bandcamp's `defaultPrice` only when it
 * differs from the album's headline price.
 */
class DustvalveSingleTrackPriceTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveAlbumScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveAlbumScraper(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    private fun loadFixture(name: String): String =
        checkNotNull(this::class.java.classLoader)
            .getResourceAsStream("fixtures/bandcamp/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("missing fixture fixtures/bandcamp/$name")

    @Test fun `single-track release does not surface a redundant per-track price`() = runTest {
        // moeshop's HARDCODED is its own /track/ release: defaultPrice ==
        // album price (both 1.50 USD), so singleTrackPrice should be null
        // and the "Buy a single track" menu option will not be offered.
        setup.server.enqueue(MockResponse().setBody(loadFixture("track_moeshop_hardcoded.html")))
        val album = scraper.scrapeAlbum(setup.url("/track/hardcoded"))
        assertThat(album.price).isEqualTo(AlbumPrice(amount = 1.5, currency = "USD"))
        assertThat(album.singleTrackPrice).isNull()
    }

    @Test fun `multi-track album with differing defaultPrice surfaces a per-track price`() = runTest {
        // Radiohead's "In Rainbows": album 9.99 GBP, defaultPrice 9.0 GBP.
        // Different -> singleTrackPrice exposed so the album viewer can show
        // a "Buy a single track (£9.00)" option.
        setup.server.enqueue(MockResponse().setBody(loadFixture("album_radiohead_in_rainbows.html")))
        val album = scraper.scrapeAlbum(setup.url("/album/in-rainbows"))
        assertThat(album.price).isEqualTo(AlbumPrice(amount = 9.99, currency = "GBP"))
        assertThat(album.singleTrackPrice).isEqualTo(AlbumPrice(amount = 9.0, currency = "GBP"))
    }

    @Test fun `defaultPrice equal to album price is suppressed`() = runTest {
        // moeshop track again — defaultPrice == album price (both 1.5). The
        // suppression rule lives in the scraper, not the UI, so we double
        // down on it here.
        setup.server.enqueue(MockResponse().setBody(loadFixture("track_moeshop_hardcoded.html")))
        val album = scraper.scrapeAlbum(setup.url("/track/hardcoded"))
        assertThat(album.singleTrackPrice).isNull()
    }
}
