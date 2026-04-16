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
    data class ArtistDetail(val url: String) : NavDestination
    data object AccountLogin : NavDestination
    data object YouTubeMusicLogin : NavDestination
    data class PlaylistDetail(val playlistId: String) : NavDestination
    data class YouTubePlaylistDetail(val url: String, val name: String) : NavDestination
    data class YouTubeArtistDetail(val url: String, val name: String, val imageUrl: String?) : NavDestination
    data object SpotifyHome : NavDestination
    data class SpotifyAlbumDetail(val uri: String, val name: String, val imageUrl: String?) : NavDestination
    data class SpotifyArtistDetail(val uri: String, val name: String, val imageUrl: String?) : NavDestination
    data class SpotifyPlaylistDetail(val uri: String, val name: String) : NavDestination
    data object SpotifyLogin : NavDestination
}

enum class BottomNavItem(
    @param:StringRes val labelRes: Int,
    val destination: NavDestination,
    val provider: MusicProvider? = null,
) {
    LOCAL(R.string.nav_label_local, NavDestination.LocalHome, MusicProvider.LOCAL),
    BANDCAMP(R.string.nav_label_bandcamp, NavDestination.BandcampHome, MusicProvider.BANDCAMP),
    YOUTUBE(R.string.nav_label_youtube, NavDestination.YouTubeHome, MusicProvider.YOUTUBE),
    SPOTIFY(R.string.nav_label_spotify, NavDestination.SpotifyHome, MusicProvider.SPOTIFY),
    LIBRARY(R.string.nav_label_library, NavDestination.Library),
    SETTINGS(R.string.nav_label_settings, NavDestination.Settings),
}
