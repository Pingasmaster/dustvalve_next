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
 * - `i.ytimg.com` `/vi/` named ladders -> `hq720` (reliable max; `maxresdefault` 404s)
 * - YouTube query strings (`sqp`, `rs`, ...) stripped: they rotate per request
 * - SoundCloud size tokens -> `-t500x500`
 * - Google CDN `=sN` -> `s800`; `=wN-hM` scales so the long edge is 800
 *   (aspect preserved, one cache key). `s0` / `w0-h0` means "original" and
 *   for some YTM artist avatars the CDN never finishes the body, so those
 *   become `s800` instead.
 */
object ThumbnailUrls {

    fun canonicalize(url: String): String {
        if (url.isBlank()) return url
        var out = NetworkUtils.upgradeBandcampArtUrl(url)
        out = bumpYoutube(out)
        out = bumpSoundCloud(out)
        return out
    }

    /**
     * YouTube / YT Music CDN size rewrite only. Bandcamp is handled by
     * [NetworkUtils.upgradeBandcampArtUrl] inside [canonicalize].
     */
    fun bumpYoutube(url: String): String {
        val ytimg = url.contains("ytimg.com")
        if (ytimg) {
            var out = url.substringBefore('?')
            // Video thumbs share one host so i.ytimg vs i9.ytimg is one key.
            // Playlist `/s_p/` heroes stay on the host Innertube sent: those
            // paths are not interchangeable across iN.
            out = out.replace(Regex("""^https://i\d+\.ytimg\.com/vi"""), "https://i.ytimg.com/vi")
            out = out.replace("/vi_webp/", "/vi/")
            val viNamed = Regex(
                """/vi/([^/]+)/(hq[123]|default|mqdefault|sddefault|hqdefault|maxresdefault)""" +
                    """(?:_custom_\d+|_\d+)?\.(jpg|webp)""",
            )
            if (viNamed.containsMatchIn(out)) {
                return viNamed.replace(out) { "/vi/${it.groupValues[1]}/hq720.jpg" }
            }
            return out
        }

        var out = url
        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            out = out.substringBefore('?')
        }
        // Cap Google CDN size tokens at a finite long edge. `s0` / `w0-h0`
        // ask for the original, which for some yt3 artist avatars (YTM
        // "blackbear"-style headers) never complete - Coil spins forever.
        // 800px is sharp on a phone and is one stable disk-cache key.
        // Landscape banners keep their aspect (w800-h450, not a forced
        // square) so the CDN is not asked to invent an 800x800 original.
        val candidates = listOf(
            Regex("""=s(\d+)(-[^=&]*)?""") to { m: MatchResult ->
                "=s$GOOGLE_CDN_EDGE${m.groupValues[2]}"
            },
            Regex("""=w(\d+)-c-h(\d+)(-[^=&]*)?""") to { m: MatchResult ->
                scaleWhToken(
                    width = m.groupValues[1],
                    height = m.groupValues[2],
                    flags = m.groupValues[3],
                    interleaved = true,
                )
            },
            Regex("""=w(\d+)-h(\d+)(-[^=&]*)?""") to { m: MatchResult ->
                scaleWhToken(
                    width = m.groupValues[1],
                    height = m.groupValues[2],
                    flags = m.groupValues[3],
                    interleaved = false,
                )
            },
        )
        for ((pattern, replacement) in candidates) {
            val rewritten = pattern.replace(out, replacement)
            if (rewritten != out) return rewritten
        }
        return out
    }

    /**
     * Scale a `wN-hM` token so max(N,M) == [GOOGLE_CDN_EDGE]. Zero (original)
     * collapses to `s800` because that form preserves aspect and is finite.
     */
    private fun scaleWhToken(width: String, height: String, flags: String, interleaved: Boolean): String {
        val w = width.toIntOrNull() ?: 0
        val h = height.toIntOrNull() ?: 0
        if (w <= 0 || h <= 0) {
            return "=s$GOOGLE_CDN_EDGE$flags"
        }
        val (nw, nh) = scaleToEdge(w, h, GOOGLE_CDN_EDGE)
        return if (interleaved) "=w$nw-c-h$nh$flags" else "=w$nw-h$nh$flags"
    }

    private fun scaleToEdge(width: Int, height: Int, edge: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= 0) return edge to edge
        val scaledW = (width.toLong() * edge / longest).toInt().coerceAtLeast(1)
        val scaledH = (height.toLong() * edge / longest).toInt().coerceAtLeast(1)
        return scaledW to scaledH
    }

    /**
     * SoundCloud artwork size tokens (`-large`, `-t67x67`, `-original`, ...)
     * collapse to `-t500x500` so search tiles and player heroes share one key.
     */
    fun bumpSoundCloud(url: String): String {
        if (!url.contains("sndcdn.com")) return url
        val withoutQuery = url.substringBefore('?')
        val size = Regex(
            """-(original|crop|t\d+x\d+|large|small|tiny|badge|mini)\.(jpg|png|webp)""",
            RegexOption.IGNORE_CASE,
        )
        return size.replace(withoutQuery) { "-t500x500.${it.groupValues[2].lowercase()}" }
    }

    /** Finite Google CDN edge used as the single Coil disk-cache size token. */
    const val GOOGLE_CDN_EDGE = 800
}
