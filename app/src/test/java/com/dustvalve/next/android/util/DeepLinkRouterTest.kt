package com.dustvalve.next.android.util

import com.dustvalve.next.android.ui.navigation.NavDestination
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeepLinkRouterTest {

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
