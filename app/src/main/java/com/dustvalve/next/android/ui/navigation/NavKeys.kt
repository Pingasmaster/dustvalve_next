package com.dustvalve.next.android.ui.navigation

import com.dustvalve.next.android.domain.model.MusicProvider

sealed interface NavDestination {
    data object LocalHome : NavDestination
    data object BandcampHome : NavDestination
    data object YouTubeHome : NavDestination
    data object Library : NavDestination
    data object Settings : NavDestination
    data class AlbumDetail(val url: String) : NavDestination
    data class ArtistDetail(val url: String) : NavDestination
    data object AccountLogin : NavDestination
    data object YouTubeMusicLogin : NavDestination
    data class PlaylistDetail(val playlistId: String) : NavDestination
    data class YouTubePlaylistDetail(val url: String, val name: String) : NavDestination
    data class YouTubeArtistDetail(val url: String, val name: String, val imageUrl: String?) : NavDestination
}

enum class BottomNavItem(
    val label: String,
    val destination: NavDestination,
    val provider: MusicProvider? = null,
) {
    LOCAL("Local", NavDestination.LocalHome, MusicProvider.LOCAL),
    BANDCAMP("Bandcamp", NavDestination.BandcampHome, MusicProvider.BANDCAMP),
    YOUTUBE("YouTube", NavDestination.YouTubeHome, MusicProvider.YOUTUBE),
    LIBRARY("Library", NavDestination.Library),
    SETTINGS("Settings", NavDestination.Settings),
}
