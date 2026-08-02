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

        // Prefer the first rewrite that changes the URL so every size token
        // for the same image collapses to one disk-cache key.
        val candidates = listOf(
            Regex("""=s(\d+)(-[^=&]*)?""") to { m: MatchResult -> "=s0${m.groupValues[2]}" },
            Regex("""=w(\d+)-c-h(\d+)(-[^=&]*)?""") to { m: MatchResult ->
                "=w0-c-h0${m.groupValues[3]}"
            },
            Regex("""=w(\d+)-h(\d+)(-[^=&]*)?""") to { m: MatchResult ->
                "=w0-h0${m.groupValues[3]}"
            },
        )
        for ((pattern, replacement) in candidates) {
            val rewritten = pattern.replace(url, replacement)
            if (rewritten != url) return rewritten
        }
        return url
    }
}
