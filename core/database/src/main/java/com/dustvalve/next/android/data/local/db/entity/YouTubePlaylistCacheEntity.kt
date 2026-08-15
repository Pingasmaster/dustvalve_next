package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent cache for YouTube playlist metadata + ordered videoIds. We
 * always emit the cached snapshot first, then silently re-fetch in the
 * background on unmetered networks - playlists CAN grow new entries.
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
