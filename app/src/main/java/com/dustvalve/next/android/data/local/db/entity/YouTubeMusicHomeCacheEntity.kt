package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row snapshot cache for the YouTube Music home feed. The feed is
 * editorial (changes daily) so we always emit the cached copy first and
 * silently revalidate in the background once per hour.
 *
 * The serialized form is the JSON of the parsed home feed (see
 * [com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed]); we keep
 * the raw model JSON instead of re-rendering Innertube responses so cache
 * reads avoid re-running the parser.
 */
@Entity(tableName = "youtube_music_home")
data class YouTubeMusicHomeCacheEntity(
    @PrimaryKey val key: String, // "home" or a mood param
    val feedJson: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
