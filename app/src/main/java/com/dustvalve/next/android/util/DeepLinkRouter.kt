package com.dustvalve.next.android.util

import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.ui.navigation.NavDestination
import java.net.URI
import java.net.URLDecoder

sealed interface DeepLinkAction {
    /** Navigate to a destination screen. */
    data class Navigate(val destination: NavDestination) : DeepLinkAction

    /** Fetch track info from YouTube and play it immediately. */
    data class PlayYouTubeVideo(val videoUrl: String) : DeepLinkAction
}

/** The kind of resource a compatible link points at — drives the inline chip label. */
enum class LinkResourceType { VIDEO, SONG, PLAYLIST, ALBUM, ARTIST, TRACK }

/** A pasted/opened link that resolved to something the app can open. */
data class DetectedLink(val provider: MusicProvider, val type: LinkResourceType, val action: DeepLinkAction)

/**
 * Pure, offline URL classifier for the platforms the app supports (YouTube, YouTube Music,
 * Bandcamp on *.bandcamp.com). [detect] is the single entry point used both for the inline
 * "open link" chip and for the first pass of opening a pasted link. Custom Bandcamp domains
 * are NOT handled here (no host signal) — see BandcampDomainSniffer for the on-Enter network path.
 */
object DeepLinkRouter {

    // 11-char YouTube video id. {11} is a quantifier (not a literal brace), so it is NOT escaped.
    private const val VID = "[a-zA-Z0-9_-]{11}"
    private val VIDEO_ID_RE = Regex(VID)

    private val YT_HOST_RE = Regex("""^(?:www\.|m\.)?youtube(?:-nocookie)?\.[a-z.]+$""")
    private const val YT_MUSIC_HOST = "music.youtube.com"
    private val YOUTU_BE_HOSTS = setOf("youtu.be", "www.youtu.be")
    private val BANDCAMP_HOST_RE = Regex("""^(?:[\w-]+\.)?bandcamp\.com$""", RegexOption.IGNORE_CASE)
    private val GOOGLE_HOST_RE = Regex("""^(?:www\.)?google\.[a-z.]+$""")

    // Path-based video-id forms: /shorts/ID, /embed/ID, /live/ID, /v/ID, /e/ID, /watch/ID.
    private val PATH_VIDEO_RE = Regex("""^/(?:shorts|embed|live|v|e|watch)/($VID)""")
    private val SCHEME_RE = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")
    private val V_PARAM_RE = Regex("(?:^|&)v=($VID)")
    private val LIST_PARAM_RE = Regex("(?:^|&)list=([a-zA-Z0-9_-]+)")
    private val BROWSE_VL_RE = Regex("""^/browse/VL([a-zA-Z0-9_-]+)""")
    private val BROWSE_UC_RE = Regex("""^/browse/(UC[a-zA-Z0-9_-]+)""")

    private const val MAX_UNWRAP = 3

    /** True if the string looks like a web URL worth attempting a network resolve for (custom domains). */
    fun looksLikeUrl(raw: String): Boolean {
        val uri = parse(raw) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host.contains('.') && !host.startsWith('.')
    }

    /** Back-compat: returns just the action (used by older call sites). */
    fun route(url: String): DeepLinkAction? = detect(url)?.action

    /** Classify [raw] into a [DetectedLink], or null if it isn't a compatible/openable link. */
    fun detect(raw: String): DetectedLink? {
        val uri = normalize(raw) ?: return null
        val host = uri.host?.lowercase() ?: return null
        return when {
            host in YOUTU_BE_HOSTS || host == YT_MUSIC_HOST || YT_HOST_RE.matches(host) ->
                routeYouTube(uri, host)

            BANDCAMP_HOST_RE.matches(host) -> routeBandcamp(uri, host)

            else -> null
        }
    }

    // --- normalization -----------------------------------------------------

    private fun parse(raw: String): URI? {
        val trimmed = raw.trim()
        // A URL has no internal whitespace; reject empty/multi-token input early.
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if (SCHEME_RE.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return try {
            URI(withScheme)
        } catch (_: Exception) {
            null
        }
    }

    /** Parse, validate scheme, and iteratively unwrap consent/google/attribution redirectors. */
    private fun normalize(raw: String, depth: Int = 0): URI? {
        val uri = parse(raw) ?: return null
        if (uri.scheme != null && uri.scheme != "http" && uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null

        if (depth < MAX_UNWRAP) {
            // consent.youtube.com/...?continue=<encoded>
            if (host == "consent.youtube.com") {
                queryParam(uri, "continue")?.let { return normalize(it, depth + 1) }
            }
            // *.google.<tld>/url?q=<encoded> (or url=) — links copied from search results
            if (GOOGLE_HOST_RE.matches(host) && uri.path == "/url") {
                (queryParam(uri, "q") ?: queryParam(uri, "url"))?.let { return normalize(it, depth + 1) }
            }
            // youtube.com/attribution_link?...&u=%2Fwatch%3Fv%3DID
            if (YT_HOST_RE.matches(host) && uri.path == "/attribution_link") {
                queryParam(uri, "u")?.let { return normalize("https://www.youtube.com$it", depth + 1) }
            }
        }
        return uri
    }

    /** Read a query parameter, URL-decoding the value. rawQuery has no leading '?'. */
    private fun queryParam(uri: URI, name: String): String? {
        val q = uri.rawQuery ?: return null
        for (pair in q.split('&')) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            if (pair.substring(0, idx) == name) {
                return try {
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(idx + 1)
                }
            }
        }
        return null
    }

    // --- YouTube -----------------------------------------------------------

    private fun routeYouTube(uri: URI, host: String): DetectedLink? {
        val path = uri.path ?: ""
        val query = uri.rawQuery ?: ""
        val isMusic = host == YT_MUSIC_HOST

        // 1) Video id wins over everything (v= beats list=).
        videoId(host, path, query)?.let { videoId ->
            return DetectedLink(
                provider = MusicProvider.YOUTUBE,
                type = if (isMusic) LinkResourceType.SONG else LinkResourceType.VIDEO,
                action = DeepLinkAction.PlayYouTubeVideo("https://www.youtube.com/watch?v=$videoId"),
            )
        }

        // 2) Playlist: /playlist?list=, bare list= (no v=), or browse/VL<playlistId>.
        playlistId(path, query)?.let { listId ->
            return DetectedLink(
                provider = MusicProvider.YOUTUBE,
                type = LinkResourceType.PLAYLIST,
                action = DeepLinkAction.Navigate(
                    NavDestination.CollectionDetail(
                        url = "https://www.youtube.com/playlist?list=$listId",
                        sourceId = "youtube",
                        name = "",
                    ),
                ),
            )
        }

        // 3) Channel / artist: /channel/UC.., /@handle, /c/.., /user/.., browse/UC..
        channelUrl(path)?.let { channelUrl ->
            return DetectedLink(
                provider = MusicProvider.YOUTUBE,
                type = LinkResourceType.ARTIST,
                action = DeepLinkAction.Navigate(
                    NavDestination.ArtistDetail(url = channelUrl, sourceId = "youtube"),
                ),
            )
        }
        return null
    }

    private fun videoId(host: String, path: String, query: String): String? {
        // youtu.be/<ID>
        if (host in YOUTU_BE_HOSTS) {
            return path.removePrefix("/").substringBefore('/')
                .takeIf { it.matches(VIDEO_ID_RE) }
        }
        // watch?v=<ID>
        V_PARAM_RE.find(query)?.groupValues?.getOrNull(1)?.let { return it }
        // /shorts/<ID>, /embed/<ID>, /live/<ID>, /v/<ID>, /e/<ID>, /watch/<ID>
        PATH_VIDEO_RE.find(path)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private fun playlistId(path: String, query: String): String? {
        // browse/VL<playlistId> (YT Music) — strip the VL wrapper.
        BROWSE_VL_RE.find(path)?.groupValues?.getOrNull(1)?.let { return it }
        if (path.startsWith("/playlist") || query.contains("list=")) {
            LIST_PARAM_RE.find(query)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun channelUrl(path: String): String? {
        // browse/UC.. (YT Music artist) -> canonical channel url
        BROWSE_UC_RE.find(path)?.groupValues?.getOrNull(1)?.let {
            return "https://www.youtube.com/channel/$it"
        }
        if (path.startsWith("/channel/") || path.startsWith("/@") ||
            path.startsWith("/c/") || path.startsWith("/user/")
        ) {
            return "https://www.youtube.com$path"
        }
        return null
    }

    // --- Bandcamp ----------------------------------------------------------

    private fun routeBandcamp(uri: URI, host: String): DetectedLink? {
        val path = uri.path?.trimEnd('/') ?: ""
        val segments = path.split('/').filter { it.isNotEmpty() }
        val canonical = "https://$host$path"

        return when {
            // Bare apex (bandcamp.com / www.bandcamp.com) is never a single resource, and the
            // editorial blog (daily.bandcamp.com) is not a playable artist/album.
            host == "bandcamp.com" || host == "www.bandcamp.com" || host == "daily.bandcamp.com" -> null

            segments.size >= 2 && segments[0] == "album" ->
                DetectedLink(MusicProvider.BANDCAMP, LinkResourceType.ALBUM, navAlbum(canonical))

            segments.size >= 2 && segments[0] == "track" ->
                DetectedLink(MusicProvider.BANDCAMP, LinkResourceType.TRACK, navAlbum(canonical))

            // artist.bandcamp.com root or /music = artist/label page.
            segments.isEmpty() || (segments.size == 1 && segments[0] == "music") ->
                DetectedLink(
                    MusicProvider.BANDCAMP,
                    LinkResourceType.ARTIST,
                    DeepLinkAction.Navigate(
                        NavDestination.ArtistDetail("https://$host", sourceId = "bandcamp"),
                    ),
                )

            else -> null
        }
    }

    private fun navAlbum(url: String): DeepLinkAction = DeepLinkAction.Navigate(NavDestination.AlbumDetail(url))
}
