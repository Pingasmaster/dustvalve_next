package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row snapshot cache for the YouTube Music home feed. The feed is
 * editorial (changes daily) so we always emit the cached copy first and
 * silently revalidate in the background on unmetered networks.
 *
 * The serialized form is the raw Innertube JSON; cache reads re-parse it.
 */
@Entity(tableName = "youtube_music_home")
data class YouTubeMusicHomeCacheEntity(
    @PrimaryKey val key: String, // "home" or a mood param
    val feedJson: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
