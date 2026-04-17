package com.dustvalve.next.android.util

import com.dustvalve.next.android.ui.navigation.NavDestination
import java.net.URI

sealed interface DeepLinkAction {
    /** Navigate to a destination screen. */
    data class Navigate(val destination: NavDestination) : DeepLinkAction
    /** Fetch track info from YouTube and play it immediately. */
    data class PlayYouTubeVideo(val videoUrl: String) : DeepLinkAction
}

object DeepLinkRouter {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "music.youtube.com", "youtu.be", "www.youtu.be",
    )

    private val BANDCAMP_HOST_REGEX = Regex(
        """^(?:[\w-]+\.)?bandcamp\.com$""",
        RegexOption.IGNORE_CASE,
    )

    private val VIDEO_ID_REGEX = Regex("[?&]v=([a-zA-Z0-9_-]{11})")
    private val SHORTS_PATH_REGEX = Regex("^/shorts/([a-zA-Z0-9_-]{11})")
    // Matches "list=..." at the start of a query string OR after a "&".
    // Previously anchored with "[?&]list=" and then applied to uri.rawQuery, which has no
    // leading "?", so single-param URLs like "/playlist?list=PLxyz" never matched.
    private val PLAYLIST_ID_REGEX = Regex("(?:^|&)list=([a-zA-Z0-9_-]+)")

    fun route(url: String): DeepLinkAction? {
        val uri = try { URI(url) } catch (_: Exception) { return null }
        if (uri.scheme != "https" && uri.scheme != "http") return null
        val host = uri.host?.lowercase() ?: return null

        return when {
            host in YOUTUBE_HOSTS -> routeYouTube(uri, url, host)
            BANDCAMP_HOST_REGEX.matches(host) -> routeBandcamp(uri, url, host)
            else -> null
        }
    }

    private fun routeYouTube(uri: URI, url: String, host: String): DeepLinkAction? {
        val path = uri.path ?: ""

        // youtu.be/VIDEO_ID
        if (host == "youtu.be" || host == "www.youtu.be") {
            val videoId = path.removePrefix("/").takeIf { it.matches(Regex("[a-zA-Z0-9_-]{11}")) }
            if (videoId != null) {
                return DeepLinkAction.PlayYouTubeVideo("https://www.youtube.com/watch?v=$videoId")
            }
            return null
        }

        // /shorts/VIDEO_ID
        SHORTS_PATH_REGEX.find(path)?.groupValues?.getOrNull(1)?.let { videoId ->
            return DeepLinkAction.PlayYouTubeVideo("https://www.youtube.com/watch?v=$videoId")
        }

        // /watch?v=VIDEO_ID
        VIDEO_ID_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { videoId ->
            return DeepLinkAction.PlayYouTubeVideo("https://www.youtube.com/watch?v=$videoId")
        }

        // /playlist?list=PLAYLIST_ID
        if (path.startsWith("/playlist")) {
            val query = uri.rawQuery ?: ""
            PLAYLIST_ID_REGEX.find(query)?.groupValues?.getOrNull(1)?.let { listId ->
                return DeepLinkAction.Navigate(
                    NavDestination.YouTubePlaylistDetail(
                        url = "https://www.youtube.com/playlist?list=$listId",
                        name = "",
                    ),
                )
            }
        }

        // /channel/CHANNEL_ID or /@username
        if (path.startsWith("/channel/") || path.startsWith("/@")) {
            return DeepLinkAction.Navigate(
                NavDestination.YouTubeArtistDetail(url = url, name = "", imageUrl = null),
            )
        }

        return null
    }

    private fun routeBandcamp(uri: URI, url: String, host: String): DeepLinkAction? {
        val path = uri.path?.trimEnd('/') ?: ""
        val segments = path.split('/').filter { it.isNotEmpty() }

        return when {
            // artist.bandcamp.com/album/album-name
            segments.size >= 2 && segments[0] == "album" ->
                DeepLinkAction.Navigate(NavDestination.AlbumDetail(url))

            // artist.bandcamp.com/track/track-name
            segments.size >= 2 && segments[0] == "track" ->
                DeepLinkAction.Navigate(NavDestination.AlbumDetail(url))

            // artist.bandcamp.com (root = artist page, but not bare bandcamp.com)
            segments.isEmpty() && host != "bandcamp.com" && host != "www.bandcamp.com" ->
                DeepLinkAction.Navigate(NavDestination.ArtistDetail(url))

            else -> null
        }
    }
}
