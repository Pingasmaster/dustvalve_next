@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.ui.navigation.NavDestination
import com.dustvalve.next.android.util.DeepLinkAction
import com.dustvalve.next.android.util.LinkResourceType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test

class BandcampDomainSnifferTest {

    private lateinit var setup: TlsTestServer.Setup
    private lateinit var sniffer: BandcampDomainSniffer

    @Before fun setUp() {
        setup = TlsTestServer.start()
        sniffer = BandcampDomainSniffer(setup.client, UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        setup.server.shutdown()
    }

    private val customAlbumHtml = """
        <html><head>
        <meta name="generator" content="Bandcamp">
        <meta property="og:url" content="https://disasterpeace.bandcamp.com/album/fez-ost">
        <meta property="og:type" content="album">
        <meta property="og:site_name" content="Disasterpeace">
        <script type="application/json" data-tralbum="{&quot;id&quot;:688853505}"></script>
        </head><body></body></html>
    """.trimIndent()

    @Test fun `custom domain album detected with canonical url`() {
        val d = BandcampDomainSniffer.parse(customAlbumHtml, "https://music.disasterpeace.com/album/fez-ost")!!
        assertThat(d.provider).isEqualTo(MusicProvider.BANDCAMP)
        assertThat(d.type).isEqualTo(LinkResourceType.ALBUM)
        val nav = (d.action as DeepLinkAction.Navigate).destination as NavDestination.AlbumDetail
        // Prefers the canonical *.bandcamp.com og:url so the scraper resolves it.
        assertThat(nav.url).isEqualTo("https://disasterpeace.bandcamp.com/album/fez-ost")
    }

    @Test fun `custom domain track detected`() {
        val html = """
            <head>
            <meta name="generator" content="Bandcamp">
            <meta property="og:url" content="https://artist.bandcamp.com/track/song-one">
            </head>
        """.trimIndent()
        val d = BandcampDomainSniffer.parse(html, "https://music.artist.com/track/song-one")!!
        assertThat(d.type).isEqualTo(LinkResourceType.TRACK)
    }

    @Test fun `data-tralbum without generator still detected`() {
        val html = """<head><script data-tralbum='{"id":1}'></script>
            <meta property="og:url" content="https://artist.bandcamp.com/album/x"></head>"""
        assertThat(BandcampDomainSniffer.parse(html, "https://x.com/album/x")?.type)
            .isEqualTo(LinkResourceType.ALBUM)
    }

    @Test fun `falls back to request url path when no og url`() {
        val html = """<head><meta name="generator" content="Bandcamp"></head>"""
        val d = BandcampDomainSniffer.parse(html, "https://music.artist.com/album/y")!!
        assertThat(d.type).isEqualTo(LinkResourceType.ALBUM)
        val nav = (d.action as DeepLinkAction.Navigate).destination as NavDestination.AlbumDetail
        assertThat(nav.url).isEqualTo("https://music.artist.com/album/y")
    }

    @Test fun `non-bandcamp page returns null`() {
        val html = """<head><meta name="generator" content="WordPress">
            <meta property="og:url" content="https://example.com/foo"></head>"""
        assertThat(BandcampDomainSniffer.parse(html, "https://example.com/foo")).isNull()
    }

    @Test fun `bandcamp root classified as artist`() {
        val html = """<head><meta name="generator" content="Bandcamp">
            <meta property="og:url" content="https://artist.bandcamp.com"></head>"""
        val d = BandcampDomainSniffer.parse(html, "https://music.artist.com/")!!
        assertThat(d.type).isEqualTo(LinkResourceType.ARTIST)
    }

    @Test fun `lookalike bandcamp host og url is not treated as canonical`() {
        // Regression: the canonical check used endsWith("bandcamp.com"), which
        // also accepted "evilbandcamp.com". Only bandcamp.com / *.bandcamp.com
        // may win over the URL we actually fetched.
        val html = """<head><meta name="generator" content="Bandcamp">
            <meta property="og:url" content="https://evilbandcamp.com/album/x"></head>"""
        val d = BandcampDomainSniffer.parse(html, "https://music.artist.com/album/y")!!
        val nav = (d.action as DeepLinkAction.Navigate).destination as NavDestination.AlbumDetail
        assertThat(nav.url).isEqualTo("https://music.artist.com/album/y")
    }

    @Test fun `sniff returns null instead of throwing for unparseable user input`() = runTest {
        // Regression: raw user text like "will i am" used to hit
        // Request.Builder().url() outside the try/catch and crash the process.
        assertThat(sniffer.sniff("will i am")).isNull()
        assertThat(sniffer.sniff("   ")).isNull()
        assertThat(sniffer.sniff("https://")).isNull()
        assertThat(setup.server.requestCount).isEqualTo(0)
    }

    @Test fun `sniff prepends https for scheme-less input and detects the page`() = runTest {
        setup.server.enqueue(MockResponse().setBody(customAlbumHtml))
        val schemeless = setup.url("/album/fez-ost").removePrefix("https://")
        val d = sniffer.sniff(schemeless)!!
        assertThat(d.type).isEqualTo(LinkResourceType.ALBUM)
        val nav = (d.action as DeepLinkAction.Navigate).destination as NavDestination.AlbumDetail
        assertThat(nav.url).isEqualTo("https://disasterpeace.bandcamp.com/album/fez-ost")
    }

    @Test fun `sniff bails out on non-html content type`() = runTest {
        setup.server.enqueue(
            MockResponse().setBody("pretend png bytes").setHeader("Content-Type", "image/png"),
        )
        assertThat(sniffer.sniff(setup.url("/photo.png"))).isNull()
    }

    @Test fun `sniff bails out when content length exceeds the cap`() = runTest {
        // setBody advertises Content-Length; anything over the prefix cap is
        // rejected before the body is read at all.
        val huge = customAlbumHtml + "x".repeat(400 * 1024)
        setup.server.enqueue(MockResponse().setBody(huge))
        assertThat(sniffer.sniff(setup.url("/album/huge"))).isNull()
    }

    @Test fun `sniff reads only a bounded prefix of huge chunked bodies`() = runTest {
        // Chunked transfer => unknown Content-Length. The markers live in the
        // first chunk; the tail past the cap must be ignored, not buffered.
        val huge = customAlbumHtml + "x".repeat(1024 * 1024)
        setup.server.enqueue(MockResponse().setChunkedBody(huge, 8 * 1024))
        val d = sniffer.sniff(setup.url("/album/fez-ost"))!!
        assertThat(d.provider).isEqualTo(MusicProvider.BANDCAMP)
        assertThat(d.type).isEqualTo(LinkResourceType.ALBUM)
    }
}
