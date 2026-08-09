package com.dustvalve.next.android.ui.navigation

/**
 * Shared Library / artist-album routing so single-pane [AppNavigationDestinations]
 * and dual-pane [LibraryListDetailHost] cannot diverge on source sniffing or
 * AlbumDetail vs CollectionDetail.
 */
internal fun sourceIdFromLibraryUrl(url: String): String = when {
    url.contains("soundcloud.com", ignoreCase = true) -> "soundcloud"
    url.contains("youtube.com", ignoreCase = true) ||
        url.contains("youtu.be", ignoreCase = true) -> "youtube"
    else -> "bandcamp"
}

internal fun libraryArtistDestination(url: String): NavDestination =
    NavDestination.ArtistDetail(url = url, sourceId = sourceIdFromLibraryUrl(url))

internal fun libraryAlbumDestination(url: String): NavDestination {
    val sourceId = sourceIdFromLibraryUrl(url)
    return when (sourceId) {
        "youtube", "soundcloud" -> NavDestination.CollectionDetail(url = url, sourceId = sourceId)
        else -> NavDestination.AlbumDetail(url)
    }
}

/** Album/set tap from an artist detail screen, keyed by that artist's source. */
internal fun artistAlbumDestination(url: String, artistSourceId: String): NavDestination =
    when (artistSourceId) {
        "youtube", "soundcloud" -> NavDestination.CollectionDetail(url = url, sourceId = artistSourceId)
        else -> NavDestination.AlbumDetail(url)
    }

/** SoundCloud playlist/album permalinks include `/sets/`; track permalinks do not. */
internal fun isSoundCloudCollectionUrl(url: String): Boolean =
    url.contains("soundcloud.com", ignoreCase = true) && url.contains("/sets/")
