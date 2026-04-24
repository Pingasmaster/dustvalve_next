package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent cache for YouTube video metadata (the result of
 * `YouTubeRepository.getTrackInfo`). Stream URLs are NOT cached here — they
 * expire and must be re-resolved live.
 *
 * Per the unified caching policy, once we've fetched a video's metadata we
 * never refetch it: title/duration/uploader don't change after publish.
 */
@Entity(tableName = "youtube_videos")
data class YouTubeVideoCacheEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val artistUrl: String,
    val durationSec: Float,
    val artUrl: String,
    /**
     * Resolved YTM album playlist URL (`youtube.com/playlist?list=OLAK5uy_…`),
     * or empty when the video has no YTM album.
     */
    val albumUrl: String = "",
    /**
     * True once the YTM album lookup has been attempted for this video;
     * prevents re-lookup on subsequent plays regardless of hit or miss.
     */
    val albumLookupDone: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
)
