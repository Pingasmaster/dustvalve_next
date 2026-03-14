package com.dustvalve.next.android.ui.navigation

sealed interface NavDestination {
    data object Home : NavDestination
    data object Library : NavDestination
    data object Settings : NavDestination
    data class AlbumDetail(val url: String) : NavDestination
    data class ArtistDetail(val url: String) : NavDestination
    data object AccountLogin : NavDestination
    data class PlaylistDetail(val playlistId: String) : NavDestination
}

enum class BottomNavItem(val label: String, val destination: NavDestination) {
    HOME("Home", NavDestination.Home),
    LIBRARY("Library", NavDestination.Library),
    SETTINGS("Settings", NavDestination.Settings),
}
