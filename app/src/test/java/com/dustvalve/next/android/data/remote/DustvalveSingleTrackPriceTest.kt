@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.AlbumPrice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Verifies the per-track ("Buy a single track") price plumbing. We feed real
 * captured album / track HTML into the scraper through a TLS test server and
 * assert the resulting [com.dustvalve.next.android.domain.model.Album.singleTrackPrice].
 * Album-page `defaultPrice` is the album PWYW suggestion and must NOT become
 * singleTrackPrice (live track pages often list ~1.50 while album defaultPrice
 * is ~9). The buy menu is filled later from track-page fan-out in
 * AlbumDetailViewModel.
 */
class DustvalveSingleTrackPriceTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveAlbumScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveAlbumScraper(setup.client, UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    private fun loadFixture(name: String): String = checkNotNull(this::class.java.classLoader)
        .getResourceAsStream("fixtures/bandcamp/$name")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("missing fixture fixtures/bandcamp/$name")

    @Test fun `single-track release does not surface a redundant per-track price`() = runTest {
        // moeshop's HARDCODED is its own /track/ release: scrape leaves
        // singleTrackPrice null (album price alone is enough for the buy CTA).
        setup.server.enqueue(MockResponse().setBody(loadFixture("track_moeshop_hardcoded.html")))
        val album = scraper.scrapeAlbum(setup.url("/track/hardcoded"))
        assertThat(album.price).isEqualTo(AlbumPrice(amount = 1.5, currency = "USD"))
        assertThat(album.singleTrackPrice).isNull()
    }

    @Test fun `multi-track album does not map album defaultPrice to singleTrackPrice`() = runTest {
        // Radiohead's "In Rainbows": album 9.99 GBP, album-page defaultPrice
        // 9.0 GBP (PWYW suggestion). Real per-track sale price on /track/15-step
        // is 1.5 - mapping defaultPrice here would show a false "Buy a single
        // track (GBP 9.00)". Scrape must leave singleTrackPrice null.
        setup.server.enqueue(MockResponse().setBody(loadFixture("album_radiohead_in_rainbows.html")))
        val album = scraper.scrapeAlbum(setup.url("/album/in-rainbows"))
        assertThat(album.price).isEqualTo(AlbumPrice(amount = 9.99, currency = "GBP"))
        assertThat(album.singleTrackPrice).isNull()
    }

    @Test fun `fetchTrackPrice reads the track-page defaultPrice`() = runTest {
        // Live track pages advertise the real per-track sale price.
        setup.server.enqueue(MockResponse().setBody(loadFixture("track_moeshop_hardcoded.html")))
        val price = scraper.fetchTrackPrice(setup.url("/track/hardcoded"), "USD")
        assertThat(price).isEqualTo(AlbumPrice(amount = 1.5, currency = "USD"))
    }
}
