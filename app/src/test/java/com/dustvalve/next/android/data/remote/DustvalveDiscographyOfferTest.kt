package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.AlbumPrice
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

/**
 * Regression suite for the Bandcamp "buy full discography" plumbing. Two
 * separate scrapers carry it:
 *
 *   - [DustvalveAlbumScraper.extractDiscographyOffer] reads the bundle
 *     entry (additionalProperty.item_type == "b") from the album/track
 *     JSON-LD so the album viewer's split-button can show the bundle as a
 *     menu option without re-scraping the artist page.
 *
 *   - [DustvalveArtistScraper.extractMeetsBuyFullDiscography] reads the
 *     `meets_buy_full_discography_criteria` flag from data-band on the
 *     artist page, so the artist viewer can show a discography button
 *     without poking each album.
 */
class DustvalveDiscographyOfferTest {

    private val albumScraper = DustvalveAlbumScraper(OkHttpClient())
    private val artistScraper = DustvalveArtistScraper(OkHttpClient())

    private fun load(name: String): String =
        checkNotNull(this::class.java.classLoader)
            .getResourceAsStream("fixtures/bandcamp/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("missing fixture fixtures/bandcamp/$name")

    @Test fun `moeshop HARDCODED track page surfaces 25_49 USD discography bundle`() {
        val offer = albumScraper.extractDiscographyOffer(load("track_moeshop_hardcoded.html"))
        assertThat(offer).isNotNull()
        offer!!
        assertThat(offer.price).isEqualTo(AlbumPrice(amount = 25.49, currency = "USD"))
        assertThat(offer.url).contains("#b104210103-buy")
        assertThat(offer.name).isEqualTo("full digital discography (19 releases)")
    }

    @Test fun `extractDiscographyOffer returns null when no bundle entry present`() {
        // Plain album page with only the album release, no item_type=b entry.
        val offer = albumScraper.extractDiscographyOffer(load("album_radiohead_in_rainbows.html"))
        assertThat(offer).isNull()
    }

    @Test fun `extractDiscographyOffer skips bundle without a usable price`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"additionalProperty":[{"name":"item_type","value":"b"}],
               "offers":{"price":0,"priceCurrency":"USD"},"@id":"x"}
            ]}
            </script>
        """.trimIndent()
        assertThat(albumScraper.extractDiscographyOffer(html)).isNull()
    }

    @Test fun `extractDiscographyOffer falls back to release @id when offer url missing`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"additionalProperty":[{"name":"item_type","value":"b"}],
               "@id":"https://x.bandcamp.com/album/y#b1-buy",
               "name":"full digital discography (3 releases)",
               "offers":{"price":12.0,"priceCurrency":"USD"}}
            ]}
            </script>
        """.trimIndent()
        val offer = albumScraper.extractDiscographyOffer(html)
        assertThat(offer).isNotNull()
        offer!!
        assertThat(offer.url).isEqualTo("https://x.bandcamp.com/album/y#b1-buy")
        assertThat(offer.name).isEqualTo("full digital discography (3 releases)")
    }

    @Test fun `moeshop artist root page reports meets_buy_full_discography_criteria true`() {
        val flag = artistScraper.extractMeetsBuyFullDiscography(load("artist_moeshop_root.html"))
        assertThat(flag).isTrue()
    }

    @Test fun `extractMeetsBuyFullDiscography returns false when flag missing`() {
        val flag = artistScraper.extractMeetsBuyFullDiscography(
            load("artist_taylor_moore_single_album.html")
        )
        // Taylor Moore's data-band JSON does not include this flag; absence
        // must read as "no offer", not as "true by accident from a near
        // substring match".
        assertThat(flag).isFalse()
    }
}
