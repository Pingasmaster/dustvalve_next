package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.PurchaseInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DustvalveDownloadScraperTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var scraper: DustvalveDownloadScraper

    @Before fun setUp() {
        setup = TlsTestServer.start()
        scraper = DustvalveDownloadScraper(setup.client)
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    @Test fun `parses downloads from data-blob`() = runTest {
        val json = """{&quot;digital_items&quot;:[{&quot;title&quot;:&quot;A&quot;,&quot;item_type&quot;:&quot;album&quot;,&quot;downloads&quot;:{&quot;flac&quot;:{&quot;url&quot;:&quot;https://d/flac&quot;,&quot;size_mb&quot;:200.0,&quot;description&quot;:&quot;&quot;},&quot;mp3-320&quot;:{&quot;url&quot;:&quot;https://d/mp3&quot;,&quot;size_mb&quot;:60.0,&quot;description&quot;:&quot;&quot;},&quot;unknown-format&quot;:{&quot;url&quot;:&quot;https://d/other&quot;,&quot;size_mb&quot;:10.0,&quot;description&quot;:&quot;&quot;}}}]}"""
        val html = """<html><div data-blob="$json"></div></html>"""
        setup.server.enqueue(MockResponse().setBody(html))

        val urls = scraper.getDownloadUrls(PurchaseInfo(saleItemId = 1L, saleItemType = "a"))
        assertThat(urls).hasSize(2)
        assertThat(urls[AudioFormat.FLAC]).isEqualTo("https://d/flac")
        assertThat(urls[AudioFormat.MP3_320]).isEqualTo("https://d/mp3")
        // Unknown format key skipped
        assertThat(urls.values).doesNotContain("https://d/other")
    }

    @Test fun `throws when no digital items`() = runTest {
        val html = """<html><div data-blob='{"digital_items":[]}'></div></html>"""
        setup.server.enqueue(MockResponse().setBody(html))
        try {
            scraper.getDownloadUrls(PurchaseInfo(1L, "a"))
            error("expected IOException")
        } catch (e: IOException) {
            assertThat(e.message).contains("digital items")
        }
    }

    @Test fun `throws when blob missing`() = runTest {
        setup.server.enqueue(MockResponse().setBody("<html>nothing</html>"))
        try {
            scraper.getDownloadUrls(PurchaseInfo(1L, "a"))
            error("expected IOException")
        } catch (e: IOException) {
            assertThat(e.message).contains("download data")
        }
    }

    @Test fun `http error propagates`() = runTest {
        setup.server.enqueue(MockResponse().setResponseCode(500))
        try {
            scraper.getDownloadUrls(PurchaseInfo(1L, "a"))
            error("expected IOException")
        } catch (e: IOException) {
            assertThat(e.message).contains("500")
        }
    }

    @Test fun `skips entries without a url`() = runTest {
        val json = """{&quot;digital_items&quot;:[{&quot;title&quot;:&quot;A&quot;,&quot;item_type&quot;:&quot;album&quot;,&quot;downloads&quot;:{&quot;flac&quot;:{&quot;url&quot;:null,&quot;size_mb&quot;:0,&quot;description&quot;:&quot;&quot;},&quot;mp3-320&quot;:{&quot;url&quot;:&quot;&quot;,&quot;size_mb&quot;:0,&quot;description&quot;:&quot;&quot;}}}]}"""
        val html = """<html><div data-blob="$json"></div></html>"""
        setup.server.enqueue(MockResponse().setBody(html))
        val urls = scraper.getDownloadUrls(PurchaseInfo(1L, "a"))
        assertThat(urls).isEmpty()
    }
}
