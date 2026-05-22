package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.ui.navigation.NavDestination
import com.dustvalve.next.android.util.DeepLinkAction
import com.dustvalve.next.android.util.LinkResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BandcampDomainSnifferTest {

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
}
