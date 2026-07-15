package com.dustvalve.next.android.ui.navigation

import androidx.annotation.StringRes
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.MusicProvider

enum class BottomNavItem(@param:StringRes val labelRes: Int, val destination: NavDestination, val provider: MusicProvider? = null) {
    LOCAL(R.string.nav_label_local, NavDestination.LocalHome, MusicProvider.LOCAL),
    BANDCAMP(R.string.nav_label_bandcamp, NavDestination.BandcampHome, MusicProvider.BANDCAMP),
    YOUTUBE(R.string.nav_label_youtube, NavDestination.YouTubeHome, MusicProvider.YOUTUBE),
    LIBRARY(R.string.nav_label_library, NavDestination.Library),
    SETTINGS(R.string.nav_label_settings, NavDestination.Settings),
}
