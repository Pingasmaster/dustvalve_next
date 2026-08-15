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

    @Test fun `buildArtUrl for zero id returns empty to avoid a0 404`() {
        assertThat(NetworkUtils.buildArtUrl(0L)).isEmpty()
    }

    @Test fun `upgradeBandcampArtUrl promotes all size tiers to full-original _0`() {
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_2.jpg"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_5.jpg"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f4.bcbits.com/img/a99_10.jpg"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
    }

    @Test fun `upgradeBandcampArtUrl collapses host and query variants to one key`() {
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://f1.bcbits.com/img/a99_2.jpg?cb=1"))
            .isEqualTo("https://f4.bcbits.com/img/a99_0.jpg")
        assertThat(NetworkUtils.upgradeBandcampArtUrl("https://cdn.bcbits.com/img/a99_5.jpg"))
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

        val ytQuery = "https://i.ytimg.com/vi/abc/sddefault.jpg?sqp=xx"
        assertThat(ThumbnailUrls.canonicalize(ytQuery))
            .isEqualTo("https://i.ytimg.com/vi/abc/hq720.jpg")

        val sc = "https://i1.sndcdn.com/artworks-xyz-large.jpg?t=1"
        val scCanon = ThumbnailUrls.canonicalize(sc)
        assertThat(scCanon).isEqualTo("https://i1.sndcdn.com/artworks-xyz-t500x500.jpg")
        assertThat(ThumbnailUrls.canonicalize("https://i1.sndcdn.com/artworks-xyz-t67x67.jpg"))
            .isEqualTo(scCanon)

        val googleOrig = "https://yt3.googleusercontent.com/abc=s0-c-k-c0x00ffffff-no-rj"
        assertThat(ThumbnailUrls.canonicalize(googleOrig))
            .isEqualTo("https://yt3.googleusercontent.com/abc=s800-c-k-c0x00ffffff-no-rj")
        val landscape = "https://lh3.googleusercontent.com/hero=w1440-h600"
        assertThat(ThumbnailUrls.canonicalize(landscape))
            .isEqualTo("https://lh3.googleusercontent.com/hero=w800-h333")
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

    @Test fun `isLiteralDisallowedHost catches loopback and RFC1918 literals`() {
        assertThat(NetworkUtils.isLiteralDisallowedHost("127.0.0.1")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("localhost")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("10.0.0.1")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("192.168.1.1")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("172.16.5.5")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("169.254.1.1")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("100.64.0.1")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("fc00::1")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("fe80::1")).isTrue()
        assertThat(NetworkUtils.isLiteralDisallowedHost("example.com")).isFalse()
        assertThat(NetworkUtils.isLiteralDisallowedHost("bandcamp.com")).isFalse()
    }

    @Test fun `sanitizeImportedMediaUrl blanks non-https and unknown hosts`() {
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "http://f4.bcbits.com/img/a1_0.jpg",
                "bandcamp",
            ),
        ).isEmpty()
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "https://evil.example/stream.mp3",
                "bandcamp",
            ),
        ).isEmpty()
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "https://f4.bcbits.com/img/a1_0.jpg",
                "bandcamp",
            ),
        ).isEqualTo("https://f4.bcbits.com/img/a1_0.jpg")
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "https://i.ytimg.com/vi/abc/hqdefault.jpg",
                "youtube",
            ),
        ).isEqualTo("https://i.ytimg.com/vi/abc/hqdefault.jpg")
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "https://f4.bcbits.com/img/a1_0.jpg",
                "youtube",
            ),
        ).isEmpty()
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "https://127.0.0.1/secret",
                "bandcamp",
            ),
        ).isEmpty()
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "https://sndcdn.com/foo.mp3",
                "soundcloud",
            ),
        ).isEqualTo("https://sndcdn.com/foo.mp3")
        assertThat(
            NetworkUtils.sanitizeImportedMediaUrl(
                "https://sndcdn.com/foo.mp3",
                "local",
            ),
        ).isEmpty()
    }

    @Test fun `requirePublicRemoteUrl rejects loopback`() {
        val ex = runCatching {
            NetworkUtils.requirePublicRemoteUrl("https://127.0.0.1/x")
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(ex!!.message).contains("private")
    }
}
