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

    @Test fun `buildArtUrl for positive id`() {
        assertThat(NetworkUtils.buildArtUrl(12345L)).isEqualTo("https://f4.bcbits.com/img/a12345_0.jpg")
    }

    @Test fun `buildArtUrl for zero id`() {
        assertThat(NetworkUtils.buildArtUrl(0L)).isEqualTo("https://f4.bcbits.com/img/a0_0.jpg")
    }

    @Test fun `upgradeBandcampArtUrl promotes all size tiers to full-original _0`() {
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_2.jpg"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_5.jpg"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_10.jpg"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
    }

    @Test fun `upgradeBandcampArtUrl leaves full-original alone and rewrites PNG _1 to JPEG _0`() {
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_0.jpg"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_1.png"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
    }

    @Test fun `upgradeBandcampArtUrl leaves non-bcbits urls alone`() {
        val url = "https://cdn.example.com/img/a99_2.jpg"
        assertThat(NetworkUtils.upgradeBandcampArtUrl(url)).isEqualTo(url)
    }

    @Test fun `ThumbnailUrls canonicalize is idempotent for youtube and bandcamp`() {
        val yt = "https://i.ytimg.com/vi/abc/hqdefault.jpg"
        val ytCanon = ThumbnailUrls.canonicalize(yt)
        assertThat(ytCanon).isEqualTo("https://i.ytimg.com/vi/abc/hq720.jpg")
        assertThat(ThumbnailUrls.canonicalize(ytCanon)).isEqualTo(ytCanon)

        val bc = "https://f4.bcbits.com/img/a42_2.jpg"
        val bcCanon = ThumbnailUrls.canonicalize(bc)
        assertThat(bcCanon).isEqualTo("https://f4.bcbits.com/img/a42_0.jpg")
        assertThat(ThumbnailUrls.canonicalize(bcCanon)).isEqualTo(bcCanon)
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
