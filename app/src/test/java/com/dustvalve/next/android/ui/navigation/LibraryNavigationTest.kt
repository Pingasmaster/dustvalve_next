package com.dustvalve.next.android.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryNavigationTest {

    @Test fun `artist url sniff covers youtube soundcloud and bandcamp`() {
        assertThat(sourceIdFromLibraryUrl("https://soundcloud.com/artist"))
            .isEqualTo("soundcloud")
        assertThat(sourceIdFromLibraryUrl("https://www.youtube.com/channel/UC1"))
            .isEqualTo("youtube")
        assertThat(sourceIdFromLibraryUrl("https://youtu.be/abc"))
            .isEqualTo("youtube")
        assertThat(sourceIdFromLibraryUrl("https://foo.bandcamp.com"))
            .isEqualTo("bandcamp")
    }

    @Test fun `library albums route youtube and soundcloud to CollectionDetail`() {
        assertThat(libraryAlbumDestination("https://www.youtube.com/playlist?list=PLx"))
            .isEqualTo(
                NavDestination.CollectionDetail(
                    url = "https://www.youtube.com/playlist?list=PLx",
                    sourceId = "youtube",
                ),
            )
        assertThat(libraryAlbumDestination("https://soundcloud.com/u/sets/mix"))
            .isEqualTo(
                NavDestination.CollectionDetail(
                    url = "https://soundcloud.com/u/sets/mix",
                    sourceId = "soundcloud",
                ),
            )
        assertThat(libraryAlbumDestination("https://foo.bandcamp.com/album/bar"))
            .isEqualTo(NavDestination.AlbumDetail("https://foo.bandcamp.com/album/bar"))
    }

    @Test fun `artist album destination follows the artist sourceId`() {
        assertThat(artistAlbumDestination("https://soundcloud.com/u/sets/a", "soundcloud"))
            .isEqualTo(
                NavDestination.CollectionDetail(
                    url = "https://soundcloud.com/u/sets/a",
                    sourceId = "soundcloud",
                ),
            )
        assertThat(artistAlbumDestination("https://foo.bandcamp.com/album/a", "bandcamp"))
            .isEqualTo(NavDestination.AlbumDetail("https://foo.bandcamp.com/album/a"))
    }

    @Test fun `soundcloud collection url requires sets segment`() {
        assertThat(isSoundCloudCollectionUrl("https://soundcloud.com/u/sets/mix")).isTrue()
        assertThat(isSoundCloudCollectionUrl("https://soundcloud.com/u/cool-track")).isFalse()
        assertThat(isSoundCloudCollectionUrl("https://foo.bandcamp.com/album/x")).isFalse()
    }
}
