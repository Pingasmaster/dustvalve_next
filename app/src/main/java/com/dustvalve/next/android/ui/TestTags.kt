package com.dustvalve.next.android.ui

/**
 * Stable semantics test tags for UI tests (Robolectric Compose + on-device
 * instrumentation). Referenced from app/src/test and app/src/androidTest;
 * production behavior is unaffected. MainActivity sets
 * `testTagsAsResourceId = true` on the root so UiAutomator can address these
 * via By.res() as well.
 */
object TestTags {
    const val BOTTOM_NAV = "bottom_nav"
    fun bottomNavItem(name: String) = "bottom_nav_item_$name"

    const val MINI_PLAYER = "mini_player"
    const val MINI_PLAYER_PLAY_PAUSE = "mini_player_play_pause"
    const val MINI_PLAYER_TITLE = "mini_player_title"

    const val FULL_PLAYER = "full_player"
    const val PLAYER_PLAY_PAUSE = "player_play_pause"
    const val PLAYER_POSITION = "player_position"
    const val PLAYER_DURATION = "player_duration"
    const val PLAYER_NEXT = "player_next"
    const val PLAYER_PREVIOUS = "player_previous"
    const val PLAYER_QUEUE_BUTTON = "player_queue_button"
    const val QUEUE_SHEET = "queue_sheet"

    fun trackRow(index: Int) = "track_row_$index"

    const val SEARCH_FIELD = "search_field"
    const val LIBRARY_FAB = "library_fab"
    const val ADD_TO_PLAYLIST_SHEET = "add_to_playlist_sheet"
    const val ADD_TO_PLAYLIST_NEW = "add_to_playlist_new"
    fun playlistRow(name: String) = "playlist_row_$name"

    const val LOCAL_ENABLE_BUTTON = "local_enable_button"
    const val LOCAL_TRACK_LIST = "local_track_list"
}
