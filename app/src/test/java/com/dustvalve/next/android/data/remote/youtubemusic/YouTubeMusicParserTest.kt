package com.dustvalve.next.android.data.remote.youtubemusic

import com.dustvalve.next.android.domain.model.Shelf
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeMusicParserTest {

    private val parser = YouTubeMusicParser()

    @Test fun `parseHome happy path 3 carousels`() {
        val feed = parser.parseHome(Fixtures.load("home_3_carousels.json"))

        assertThat(feed.chips).isNotEmpty()
        assertThat(feed.chips.first().title).isNotEmpty()
        assertThat(feed.chips.first().params).isNotEmpty()

        assertThat(feed.shelves).hasSize(3)
        feed.shelves.forEach { shelf ->
            assertThat(shelf).isInstanceOf(Shelf.Tiles::class.java)
            assertThat((shelf as Shelf.Tiles).items).isNotEmpty()
        }
    }

    @Test fun `parseHome with tastebuilder shelf skips it silently`() {
        val feed = parser.parseHome(Fixtures.load("home_with_tastebuilder.json"))
        // Fixture has 2 carousels + 1 tastebuilder; we expect 2 parsed shelves.
        assertThat(feed.shelves).hasSize(2)
    }

    @Test fun `parseHome flattens itemSectionRenderer wrappers (Android-style)`() {
        // The android-music fixture wraps elementRenderer-only sections
        // in itemSectionRenderer; flattening should expand them, but no
        // shelves match our music renderers, so expected to throw with the
        // descriptive renderer-tree message.
        val ex = runCatching {
            parser.parseHome(Fixtures.load("home_itemsection_wrappers.json"))
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        // Either the message-renderer extraction OR the type-tree diagnostic.
        // (Android shells contain musicNotifierShelfRenderer / itemSectionRenderer wrapping elementRenderer.)
        assertThat(ex!!.message).containsMatch(
            "YouTube Music returned an empty home response \\(raw shelves: .*elementRenderer.*\\)"
        )
    }

    @Test fun `parseHome twoColumn fallback`() {
        val feed = parser.parseHome(Fixtures.load("home_two_column.json"))
        assertThat(feed.chips).hasSize(1)
        assertThat(feed.chips.first().title).isEqualTo("Energize")
        assertThat(feed.shelves).hasSize(1)
        val tiles = feed.shelves.first() as Shelf.Tiles
        assertThat(tiles.title).isEqualTo("Two-Column Test")
        assertThat(tiles.items.first().title).isEqualTo("Album Title")
        assertThat(tiles.items.first().subtitle).isEqualTo("Artist X")
        assertThat(tiles.items.first().thumbnailUrl).isEqualTo(
            "https://yt3.example/img=w544-h544-l90-rj"
        )
    }

    @Test fun `parseHome unrecognized layout throws unrecognized message`() {
        val ex = runCatching {
            parser.parseHome(Fixtures.load("home_unrecognized.json"))
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).isEqualTo("YouTube Music returned an unrecognized home response")
    }

    @Test fun `parseHome surfaces messageRenderer text`() {
        val ex = runCatching {
            parser.parseHome(Fixtures.load("home_message_renderer.json"))
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).isEqualTo(
            "YouTube Music: YouTube Music isn't available in your country — Try again later."
        )
    }
}
