package com.dustvalve.next.android.domain.model

/**
 * The `type` discriminator stored on favorites rows. Each [key] is a
 * persisted schema string (FavoriteRepositoryImplTest locks the exact
 * values) - never rename a key.
 */
enum class FavoriteType(val key: String) {
    TRACK("track"),
    ALBUM("album"),
    ARTIST("artist"),
    YOUTUBE_PLAYLIST("youtube_playlist"),
    SOUNDCLOUD_PLAYLIST("soundcloud_playlist"),
    COLLECTION("collection"),
}
