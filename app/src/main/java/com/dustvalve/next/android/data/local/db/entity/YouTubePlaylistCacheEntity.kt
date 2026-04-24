package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent cache for YouTube playlist metadata + ordered videoIds. Per the
 * unified caching policy we always emit the cached snapshot first, then
 * silently re-fetch in the background if older than the TTL — playlists CAN
 * grow new entries (the user explicitly called this out).
 *
 * Individual video metadata lives in [YouTubeVideoCacheEntity]; this row
 * just records the playlist's identity + ordering, joined back at read time.
 */
@Entity(tableName = "youtube_playlists")
data class YouTubePlaylistCacheEntity(
    @PrimaryKey val playlistId: String,
    val title: String,
    /** JSON-encoded ordered list of videoIds. */
    val videoIdsJson: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
