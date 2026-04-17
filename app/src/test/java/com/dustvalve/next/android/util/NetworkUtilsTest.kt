package com.dustvalve.next.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkUtilsTest {

    @Test fun `isValidHttpsUrl accepts bandcamp album`() {
        assertThat(NetworkUtils.isValidHttpsUrl("https://artist.bandcamp.com/album/foo")).isTrue()
    }

    @Test fun `isValidHttpsUrl rejects http`() {
        assertThat(NetworkUtils.isValidHttpsUrl("http://bandcamp.com/")).isFalse()
    }

    @Test fun `isValidHttpsUrl rejects missing scheme`() {
        assertThat(NetworkUtils.isValidHttpsUrl("bandcamp.com/foo")).isFalse()
    }

    @Test fun `isValidHttpsUrl rejects host without dot`() {
        assertThat(NetworkUtils.isValidHttpsUrl("https://localhost/")).isFalse()
    }

    @Test fun `isValidHttpsUrl rejects garbage`() {
        assertThat(NetworkUtils.isValidHttpsUrl("not a url")).isFalse()
        assertThat(NetworkUtils.isValidHttpsUrl("")).isFalse()
        assertThat(NetworkUtils.isValidHttpsUrl("https://")).isFalse()
    }

    @Test fun `isDustvalveDomain matches bare bandcamp`() {
        assertThat(NetworkUtils.isDustvalveDomain("https://bandcamp.com/foo")).isTrue()
    }

    @Test fun `isDustvalveDomain matches subdomains`() {
        assertThat(NetworkUtils.isDustvalveDomain("https://artist.bandcamp.com/album/foo")).isTrue()
        assertThat(NetworkUtils.isDustvalveDomain("https://some-band.bandcamp.com/")).isTrue()
    }

    @Test fun `isDustvalveDomain rejects lookalike`() {
        assertThat(NetworkUtils.isDustvalveDomain("https://evilbandcamp.com/")).isFalse()
        assertThat(NetworkUtils.isDustvalveDomain("https://bandcamp.com.evil.com/")).isFalse()
    }

    @Test fun `isDustvalveDomain rejects http`() {
        assertThat(NetworkUtils.isDustvalveDomain("http://bandcamp.com/")).isFalse()
    }

    @Test fun `extractArtistSlug from subdomain`() {
        assertThat(NetworkUtils.extractArtistSlug("https://foobar.bandcamp.com/album/x"))
            .isEqualTo("foobar")
    }

    @Test fun `extractArtistSlug from path`() {
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/artistname"))
            .isEqualTo("artistname")
    }

    @Test fun `extractArtistSlug returns null for known non-artist paths`() {
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/search?q=foo")).isNull()
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/api/fan/x")).isNull()
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/login")).isNull()
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/discover")).isNull()
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/signup")).isNull()
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/tag/rock")).isNull()
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/help")).isNull()
    }

    @Test fun `extractArtistSlug returns null for empty path on bandcamp root`() {
        assertThat(NetworkUtils.extractArtistSlug("https://bandcamp.com/")).isNull()
    }

    @Test fun `extractArtistSlug returns null for malformed url`() {
        assertThat(NetworkUtils.extractArtistSlug("not a url")).isNull()
    }

    @Test fun `buildSearchUrl encodes query`() {
        val url = NetworkUtils.buildSearchUrl("hello world", 1, null)
        assertThat(url).isEqualTo("https://bandcamp.com/search?q=hello+world&page=1")
    }

    @Test fun `buildSearchUrl includes item type`() {
        val url = NetworkUtils.buildSearchUrl("foo", 2, "a")
        assertThat(url).isEqualTo("https://bandcamp.com/search?q=foo&page=2&item_type=a")
    }

    @Test fun `buildSearchUrl handles special chars`() {
        val url = NetworkUtils.buildSearchUrl("foo & bar", 3, null)
        assertThat(url).contains("q=foo+%26+bar")
        assertThat(url).contains("page=3")
    }

    @Test fun `buildArtUrl for positive id`() {
        assertThat(NetworkUtils.buildArtUrl(12345L)).isEqualTo("https://f4.bcbits.com/img/a12345_10.jpg")
    }

    @Test fun `buildArtUrl for zero id`() {
        assertThat(NetworkUtils.buildArtUrl(0L)).isEqualTo("https://f4.bcbits.com/img/a0_10.jpg")
    }

    @Test fun `sanitizeFileName keeps safe chars`() {
        assertThat(NetworkUtils.sanitizeFileName("abc_123.mp3")).isEqualTo("abc_123.mp3")
        assertThat(NetworkUtils.sanitizeFileName("a-b-c")).isEqualTo("a-b-c")
    }

    @Test fun `sanitizeFileName replaces unsafe chars`() {
        assertThat(NetworkUtils.sanitizeFileName("foo/bar"))
            .isEqualTo("foo_bar")
        assertThat(NetworkUtils.sanitizeFileName("foo bar baz"))
            .isEqualTo("foo_bar_baz")
        assertThat(NetworkUtils.sanitizeFileName("a:b*c?d"))
            .isEqualTo("a_b_c_d")
    }

    @Test fun `sanitizeFileName empty returns unnamed`() {
        assertThat(NetworkUtils.sanitizeFileName("")).isEqualTo("unnamed")
    }

    @Test fun `sanitizeFileName all-unsafe returns unnamed`() {
        assertThat(NetworkUtils.sanitizeFileName("///")).isEqualTo("unnamed")
        assertThat(NetworkUtils.sanitizeFileName("   ")).isEqualTo("unnamed")
    }

    @Test fun `sanitizeFileName unicode replaced`() {
        assertThat(NetworkUtils.sanitizeFileName("café")).isEqualTo("caf_")
    }
}
