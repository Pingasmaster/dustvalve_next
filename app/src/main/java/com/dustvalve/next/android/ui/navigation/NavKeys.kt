package com.dustvalve.next.android.ui.navigation

import androidx.annotation.StringRes
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.MusicProvider

sealed interface NavDestination {
    data object LocalHome : NavDestination
    data object BandcampHome : NavDestination
    data object YouTubeHome : NavDestination
    data object Library : NavDestination
    data object Settings : NavDestination
    data class AlbumDetail(val url: String) : NavDestination
    /**
     * Source-agnostic artist detail. [sourceId] matches
     * [com.dustvalve.next.android.domain.repository.MusicSource.id] — e.g.
     * "bandcamp", "youtube". [name] and [imageUrl] are optional hints the
     * caller already knows (YouTube channels browse doesn't return those, so
     * plumbing them through the nav arg avoids a flash of empty header).
     */
    data class ArtistDetail(
        val url: String,
        val sourceId: String = "bandcamp",
        val name: String? = null,
        val imageUrl: String? = null,
    ) : NavDestination
    data object AccountLogin : NavDestination
    data object YouTubeMusicLogin : NavDestination
    data class PlaylistDetail(val playlistId: String) : NavDestination
    /** Source-agnostic remote collection (YouTube playlist, etc.). */
    data class CollectionDetail(
        val url: String,
        val sourceId: String = "youtube",
        val name: String = "",
    ) : NavDestination
}

enum class BottomNavItem(
    @param:StringRes val labelRes: Int,
    val destination: NavDestination,
    val provider: MusicProvider? = null,
) {
    LOCAL(R.string.nav_label_local, NavDestination.LocalHome, MusicProvider.LOCAL),
    BANDCAMP(R.string.nav_label_bandcamp, NavDestination.BandcampHome, MusicProvider.BANDCAMP),
    YOUTUBE(R.string.nav_label_youtube, NavDestination.YouTubeHome, MusicProvider.YOUTUBE),
    LIBRARY(R.string.nav_label_library, NavDestination.Library),
    SETTINGS(R.string.nav_label_settings, NavDestination.Settings),
}
