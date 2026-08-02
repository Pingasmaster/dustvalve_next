package com.dustvalve.next.android.util

/**
 * Canonical full-quality artwork URLs.
 *
 * Every UI surface (search, queue, artist/album detail, player, etc.) must
 * resolve to the SAME https URL for a given piece of art so Coil's disk cache
 * downloads it once on first display and reuses it forever after.
 *
 * Policy:
 * - Bandcamp `bcbits.com/img/..._N.ext` -> `_0` (full-original JPEG tier)
 * - `i.ytimg.com` named ladders -> `hq720` (reliable max; `maxresdefault` 404s)
 * - Google CDN `=sN` -> `=s0` (original)
 * - Google CDN `=wN-hM` / `=wN-c-hM` -> `=w0-h0` / `=w0-c-h0` (original)
 */
object ThumbnailUrls {

    fun canonicalize(url: String): String {
        if (url.isBlank()) return url
        var out = NetworkUtils.upgradeBandcampArtUrl(url)
        out = bumpYoutube(out)
        return out
    }

    /**
     * YouTube / YT Music CDN size rewrite only. Bandcamp is handled by
     * [NetworkUtils.upgradeBandcampArtUrl] inside [canonicalize].
     */
    fun bumpYoutube(url: String): String {
        // Named i.ytimg.com / vi_webp ladders (jpg and webp).
        val named = Regex("""/(hq[123]|default|mqdefault|sddefault|hqdefault)\.(jpg|webp)""")
        if (named.containsMatchIn(url)) {
            return url.replace(named, "/hq720.$2")
        }
        val namedCustom = Regex("""/hqdefault(?:_custom_\d+|_\d+)\.(jpg|webp)""")
        if (namedCustom.containsMatchIn(url)) {
            return url.replace(namedCustom, "/hq720.$1")
        }

        // =sN -> =s0 (full original). Always rewrite so s48/s576/s1200 share
        // one cache key.
        val sRewritten = Regex("""=s(\d+)(-[^=&]*)?""").replace(url) { match ->
            "=s0${match.groupValues[2]}"
        }
        if (sRewritten != url) return sRewritten

        // =wN-c-hM -> =w0-c-h0 (preserve trailing crop/round flags).
        val whcRewritten = Regex("""=w(\d+)-c-h(\d+)(-[^=&]*)?""").replace(url) { match ->
            "=w0-c-h0${match.groupValues[3]}"
        }
        if (whcRewritten != url) return whcRewritten

        // =wN-hM -> =w0-h0.
        val whRewritten = Regex("""=w(\d+)-h(\d+)(-[^=&]*)?""").replace(url) { match ->
            "=w0-h0${match.groupValues[3]}"
        }
        if (whRewritten != url) return whRewritten

        return url
    }
}
