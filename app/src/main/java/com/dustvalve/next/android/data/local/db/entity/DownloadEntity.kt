package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per persisted asset. There is no separate cache layer; this table
 * is the unified downloads pool.
 *
 * `pinned = true` (default) is an explicit user download — never evicted.
 * `pinned = false` is auto-cached content (e.g. on-play track caching) and
 * is subject to LRU eviction when the storage limit is exceeded.
 */
@Entity(
    tableName = "downloads",
    indices = [Index("albumId")]
)
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val albumId: String,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
    val format: String = "mp3-128",
    val pinned: Boolean = true,
    val lastAccessed: Long = downloadedAt,
)
