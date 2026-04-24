package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.AlbumPrice
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

/**
 * Focused regression suite for [DustvalveAlbumScraper.extractAlbumPrice]. The
 * Bandcamp price hint drives the album-detail "Buy" CTA — silent regressions
 * here mean every album page would either crash or fall back to the generic
 * "Buy on Bandcamp" label, so we exhaustively cover real captured pages plus
 * synthesized edge-case fixtures (free album, name-your-price, malformed
 * JSON, etc).
 *
 * Pure unit test — no MockWebServer needed because [extractAlbumPrice] is a
 * pure function on the page HTML.
 */
class DustvalveAlbumPriceTest {

    private val scraper = DustvalveAlbumScraper(OkHttpClient())

    private fun load(name: String): String =
        checkNotNull(this::class.java.classLoader)
            .getResourceAsStream("fixtures/bandcamp/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("missing fixture fixtures/bandcamp/$name")

    // ── Real captured pages ──────────────────────────────────────────────

    @Test fun `Angine de Poitrine - Vol II returns 11_11 CAD`() {
        val price = scraper.extractAlbumPrice(load("album_angine_de_poitrine_vol_ii.html"))
        assertThat(price).isEqualTo(AlbumPrice(amount = 11.11, currency = "CAD"))
    }

    @Test fun `Radiohead - In Rainbows returns 9_99 GBP`() {
        val price = scraper.extractAlbumPrice(load("album_radiohead_in_rainbows.html"))
        assertThat(price).isEqualTo(AlbumPrice(amount = 9.99, currency = "GBP"))
    }

    @Test fun `Machine Girl - WLFGRL returns 8_00 USD`() {
        val price = scraper.extractAlbumPrice(load("album_machinegirl_wlfgrl.html"))
        assertThat(price).isEqualTo(AlbumPrice(amount = 8.0, currency = "USD"))
    }

    // ── Synthesized edge cases ───────────────────────────────────────────

    @Test fun `free album with offer price 0 returns null`() {
        assertThat(scraper.extractAlbumPrice(load("album_price_free_zero.html"))).isNull()
    }

    @Test fun `name your price (offer with no price) returns null`() {
        assertThat(scraper.extractAlbumPrice(load("album_price_name_your_price_no_minimum.html")))
            .isNull()
    }

    @Test fun `page without JSON-LD returns null`() {
        assertThat(scraper.extractAlbumPrice(load("album_price_no_jsonld.html"))).isNull()
    }

    @Test fun `malformed JSON-LD does not crash and returns null`() {
        assertThat(scraper.extractAlbumPrice(load("album_price_malformed_jsonld.html"))).isNull()
    }

    @Test fun `multiple offers - returns the first valid one`() {
        val price = scraper.extractAlbumPrice(load("album_price_multiple_offers_returns_first.html"))
        assertThat(price).isEqualTo(AlbumPrice(amount = 7.5, currency = "EUR"))
    }

    @Test fun `first offer invalid - falls back to next valid offer`() {
        val price = scraper.extractAlbumPrice(load("album_price_skips_first_invalid.html"))
        assertThat(price).isEqualTo(AlbumPrice(amount = 12.99, currency = "USD"))
    }

    @Test fun `JSON-LD as array of MusicRecording + MusicAlbum picks the album`() {
        val price = scraper.extractAlbumPrice(load("album_price_jsonld_is_array.html"))
        assertThat(price).isEqualTo(AlbumPrice(amount = 4.20, currency = "AUD"))
    }

    // ── Pathological inputs (defensive — never crash) ────────────────────

    @Test fun `empty html returns null`() {
        assertThat(scraper.extractAlbumPrice("")).isNull()
    }

    @Test fun `arbitrary HTML without ld+json returns null`() {
        assertThat(scraper.extractAlbumPrice("<html><body><h1>Hi</h1></body></html>")).isNull()
    }

    @Test fun `ld+json without MusicAlbum @type returns null`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicRecording","name":"loose track"}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html)).isNull()
    }

    @Test fun `MusicAlbum with no albumRelease returns null`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","name":"No releases"}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html)).isNull()
    }

    @Test fun `MusicAlbum with empty albumRelease array returns null`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","name":"Empty","albumRelease":[]}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html)).isNull()
    }

    @Test fun `negative price is treated as no-price`() {
        // Theoretically impossible but cheap to defend against.
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"offers":{"price":-1,"priceCurrency":"USD"}}
            ]}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html)).isNull()
    }

    @Test fun `missing currency returns null even if price present`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"offers":{"price":9.99}}
            ]}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html)).isNull()
    }

    @Test fun `blank currency returns null`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"offers":{"price":9.99,"priceCurrency":""}}
            ]}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html)).isNull()
    }

    @Test fun `price as string number is parsed`() {
        // JSON spec allows numbers; some Bandcamp pages emit them as strings.
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"offers":{"price":"15.50","priceCurrency":"NZD"}}
            ]}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html))
            .isEqualTo(AlbumPrice(amount = 15.5, currency = "NZD"))
    }

    @Test fun `non-numeric price is treated as no-price`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"offers":{"price":"contact us","priceCurrency":"USD"}}
            ]}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html)).isNull()
    }

    @Test fun `multiple lt-script gt with non-jsonld scripts in between`() {
        val html = """
            <script>var x = 1;</script>
            <script type="application/ld+json">
            {"@type":"WebSite","name":"Bandcamp"}
            </script>
            <script src="x.js"></script>
            <script type="application/ld+json">
            {"@type":"MusicAlbum","albumRelease":[
              {"offers":{"price":3.33,"priceCurrency":"JPY"}}
            ]}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html))
            .isEqualTo(AlbumPrice(amount = 3.33, currency = "JPY"))
    }

    // ── /track/ pages (single-track release: no separate /album/ page) ────

    @Test fun `moeshop - HARDCODED single-track page returns 1_50 USD`() {
        // Real /track/ page where the outer JSON-LD is MusicRecording (not
        // MusicAlbum) and the album block lives in inAlbum.albumRelease[].
        // Also exercises skipping the discography bundle (item_type == "b")
        // before returning the track offer.
        val price = scraper.extractAlbumPrice(load("track_moeshop_hardcoded.html"))
        assertThat(price).isEqualTo(AlbumPrice(amount = 1.5, currency = "USD"))
    }

    @Test fun `MusicRecording inAlbum bundle is skipped and album offer wins`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"MusicRecording","inAlbum":{"@type":"MusicAlbum","albumRelease":[
              {"additionalProperty":[{"name":"item_type","value":"b"}],
               "offers":{"price":99.0,"priceCurrency":"USD"}},
              {"additionalProperty":[{"name":"item_type","value":"t"}],
               "offers":{"price":2.5,"priceCurrency":"EUR"}}
            ]}}
            </script>
        """.trimIndent()
        assertThat(scraper.extractAlbumPrice(html))
            .isEqualTo(AlbumPrice(amount = 2.5, currency = "EUR"))
    }
}
