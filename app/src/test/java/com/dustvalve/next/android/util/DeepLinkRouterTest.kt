package com.dustvalve.next.android.util

import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.ui.navigation.NavDestination
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeepLinkRouterTest {

    // --- detect(): provider + type classification ---------------------------

    @Test fun `detect youtube watch is video`() {
        val d = DeepLinkRouter.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ")!!
        assertThat(d.provider).isEqualTo(MusicProvider.YOUTUBE)
        assertThat(d.type).isEqualTo(LinkResourceType.VIDEO)
    }

    @Test fun `detect youtube music watch is song`() {
        val d = DeepLinkRouter.detect("https://music.youtube.com/watch?v=dQw4w9WgXcQ")!!
        assertThat(d.type).isEqualTo(LinkResourceType.SONG)
    }

    @Test fun `detect scheme-less youtu_be`() {
        val d = DeepLinkRouter.detect("youtu.be/dQw4w9WgXcQ")!!
        assertThat(d.type).isEqualTo(LinkResourceType.VIDEO)
        assertThat((d.action as DeepLinkAction.PlayYouTubeVideo).videoUrl)
            .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test fun `detect youtu_be strips si and keeps id`() {
        val d = DeepLinkRouter.detect("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf&t=30")!!
        assertThat((d.action as DeepLinkAction.PlayYouTubeVideo).videoUrl)
            .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test fun `detect embed live v forms`() {
        for (u in listOf(
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "https://www.youtube.com/v/dQw4w9WgXcQ",
        )) {
            assertThat(DeepLinkRouter.detect(u)?.type).isEqualTo(LinkResourceType.VIDEO)
        }
    }

    @Test fun `detect nocookie embed`() {
        assertThat(DeepLinkRouter.detect("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ")?.type)
            .isEqualTo(LinkResourceType.VIDEO)
    }

    @Test fun `detect country tld`() {
        assertThat(DeepLinkRouter.detect("https://youtube.de/watch?v=dQw4w9WgXcQ")?.type)
            .isEqualTo(LinkResourceType.VIDEO)
    }

    @Test fun `detect playlist olak album-as-playlist`() {
        val d = DeepLinkRouter.detect("https://music.youtube.com/playlist?list=OLAK5uy_abc123")!!
        assertThat(d.type).isEqualTo(LinkResourceType.PLAYLIST)
        val nav = (d.action as DeepLinkAction.Navigate).destination as NavDestination.CollectionDetail
        assertThat(nav.url).isEqualTo("https://www.youtube.com/playlist?list=OLAK5uy_abc123")
    }

    @Test fun `detect browse VL playlist strips wrapper`() {
        val d = DeepLinkRouter.detect("https://music.youtube.com/browse/VLPL12345")!!
        assertThat(d.type).isEqualTo(LinkResourceType.PLAYLIST)
        val nav = (d.action as DeepLinkAction.Navigate).destination as NavDestination.CollectionDetail
        assertThat(nav.url).isEqualTo("https://www.youtube.com/playlist?list=PL12345")
    }

    @Test fun `detect browse UC artist`() {
        val d = DeepLinkRouter.detect("https://music.youtube.com/browse/UCxEqaQWosMHaTih1tgzDqug")!!
        assertThat(d.type).isEqualTo(LinkResourceType.ARTIST)
    }

    @Test fun `detect bandcamp album type and provider`() {
        val d = DeepLinkRouter.detect("https://artist.bandcamp.com/album/the-album")!!
        assertThat(d.provider).isEqualTo(MusicProvider.BANDCAMP)
        assertThat(d.type).isEqualTo(LinkResourceType.ALBUM)
    }

    @Test fun `detect bandcamp track type`() {
        assertThat(DeepLinkRouter.detect("https://artist.bandcamp.com/track/the-track")?.type)
            .isEqualTo(LinkResourceType.TRACK)
    }

    // --- normalization / unwrapping ----------------------------------------

    @Test fun `google url wrapper unwrapped`() {
        val d = DeepLinkRouter.detect(
            "https://www.google.com/url?q=https%3A%2F%2Fyoutu.be%2FdQw4w9WgXcQ&sa=D",
        )!!
        assertThat(d.type).isEqualTo(LinkResourceType.VIDEO)
    }

    @Test fun `consent redirect unwrapped`() {
        val d = DeepLinkRouter.detect(
            "https://consent.youtube.com/m?continue=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DdQw4w9WgXcQ",
        )!!
        assertThat(d.type).isEqualTo(LinkResourceType.VIDEO)
    }

    @Test fun `attribution link unwrapped`() {
        val d = DeepLinkRouter.detect(
            "https://www.youtube.com/attribution_link?a=x&u=%2Fwatch%3Fv%3DdQw4w9WgXcQ%26feature%3Dshare",
        )!!
        assertThat(d.type).isEqualTo(LinkResourceType.VIDEO)
    }

    // --- negative cases -----------------------------------------------------

    @Test fun `daily blog is not a resource`() {
        assertThat(DeepLinkRouter.detect("https://daily.bandcamp.com/")).isNull()
        assertThat(DeepLinkRouter.detect("https://daily.bandcamp.com/features/some-article")).isNull()
    }

    @Test fun `bandcamp fan profile on apex is null`() {
        assertThat(DeepLinkRouter.detect("https://bandcamp.com/somefan")).isNull()
    }

    @Test fun `looksLikeUrl heuristic`() {
        assertThat(DeepLinkRouter.looksLikeUrl("music.example.com/album/x")).isTrue()
        assertThat(DeepLinkRouter.looksLikeUrl("just some text")).isFalse()
    }

    @Test fun `youtu_be short link`() {
        val r = DeepLinkRouter.route("https://youtu.be/dQw4w9WgXcQ")
        assertThat(r).isInstanceOf(DeepLinkAction.PlayYouTubeVideo::class.java)
        assertThat((r as DeepLinkAction.PlayYouTubeVideo).videoUrl)
            .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test fun `youtube watch url`() {
        val r = DeepLinkRouter.route("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(r).isInstanceOf(DeepLinkAction.PlayYouTubeVideo::class.java)
    }

    @Test fun `youtube watch with list param still plays video`() {
        val r = DeepLinkRouter.route("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLfoo")
        assertThat(r).isInstanceOf(DeepLinkAction.PlayYouTubeVideo::class.java)
    }

    @Test fun `youtube shorts`() {
        val r = DeepLinkRouter.route("https://www.youtube.com/shorts/dQw4w9WgXcQ")
        assertThat(r).isInstanceOf(DeepLinkAction.PlayYouTubeVideo::class.java)
    }

    @Test fun `youtube playlist standalone`() {
        val r = DeepLinkRouter.route("https://www.youtube.com/playlist?list=PL12345")
        assertThat(r).isInstanceOf(DeepLinkAction.Navigate::class.java)
        val nav = (r as DeepLinkAction.Navigate).destination as NavDestination.CollectionDetail
        assertThat(nav.sourceId).isEqualTo("youtube")
    }

    @Test fun `youtube music domain`() {
        val r = DeepLinkRouter.route("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(r).isInstanceOf(DeepLinkAction.PlayYouTubeVideo::class.java)
    }

    @Test fun `youtube channel`() {
        val r = DeepLinkRouter.route("https://www.youtube.com/channel/UCabc123")
        assertThat(r).isInstanceOf(DeepLinkAction.Navigate::class.java)
        val nav = (r as DeepLinkAction.Navigate).destination as NavDestination.ArtistDetail
        assertThat(nav.sourceId).isEqualTo("youtube")
    }

    @Test fun `youtube at username`() {
        val r = DeepLinkRouter.route("https://www.youtube.com/@someuser")
        assertThat(r).isInstanceOf(DeepLinkAction.Navigate::class.java)
        val nav = (r as DeepLinkAction.Navigate).destination as NavDestination.ArtistDetail
        assertThat(nav.sourceId).isEqualTo("youtube")
    }

    @Test fun `youtube non 11 char id rejected`() {
        assertThat(DeepLinkRouter.route("https://youtu.be/short")).isNull()
    }

    @Test fun `http scheme accepted`() {
        assertThat(DeepLinkRouter.route("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
            .isInstanceOf(DeepLinkAction.PlayYouTubeVideo::class.java)
    }

    @Test fun `ftp scheme rejected`() {
        assertThat(DeepLinkRouter.route("ftp://youtu.be/dQw4w9WgXcQ")).isNull()
    }

    @Test fun `malformed url returns null`() {
        assertThat(DeepLinkRouter.route("not a url")).isNull()
        assertThat(DeepLinkRouter.route("")).isNull()
    }

    @Test fun `unknown domain returns null`() {
        assertThat(DeepLinkRouter.route("https://example.com/foo")).isNull()
    }

    @Test fun `bandcamp artist root`() {
        val r = DeepLinkRouter.route("https://some-artist.bandcamp.com/")
        assertThat(r).isInstanceOf(DeepLinkAction.Navigate::class.java)
        assertThat((r as DeepLinkAction.Navigate).destination)
            .isInstanceOf(NavDestination.ArtistDetail::class.java)
    }

    @Test fun `bandcamp album`() {
        val r = DeepLinkRouter.route("https://some-artist.bandcamp.com/album/my-album")
        assertThat(r).isInstanceOf(DeepLinkAction.Navigate::class.java)
        assertThat((r as DeepLinkAction.Navigate).destination)
            .isInstanceOf(NavDestination.AlbumDetail::class.java)
    }

    @Test fun `bandcamp track`() {
        val r = DeepLinkRouter.route("https://some-artist.bandcamp.com/track/my-track")
        assertThat(r).isInstanceOf(DeepLinkAction.Navigate::class.java)
        assertThat((r as DeepLinkAction.Navigate).destination)
            .isInstanceOf(NavDestination.AlbumDetail::class.java)
    }

    @Test fun `bandcamp bare root not an artist`() {
        assertThat(DeepLinkRouter.route("https://bandcamp.com/")).isNull()
        assertThat(DeepLinkRouter.route("https://www.bandcamp.com/")).isNull()
    }

    @Test fun `bandcamp trailing slash normalized`() {
        val r = DeepLinkRouter.route("https://some-artist.bandcamp.com/album/my-album/")
        assertThat(r).isInstanceOf(DeepLinkAction.Navigate::class.java)
    }
}
