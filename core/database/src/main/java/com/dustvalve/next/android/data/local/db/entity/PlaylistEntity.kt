package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [
        Index("isSystem"),
        Index("isPinned"),
        Index("sortOrder"),
        // One imported playlist per remote collection URL. SQLite UNIQUE
        // treats NULLs as distinct, so user-created playlists (sourceUrl null)
        // are unaffected.
        Index(value = ["sourceUrl"], unique = true),
    ],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconUrl: String? = null,
    val shapeKey: String? = null,
    val isSystem: Boolean = false,
    val systemType: String? = null, // "downloads", "recent", "collection", "favorites"
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val trackCount: Int = 0,
    val autoDownload: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Remote collection URL this playlist was imported from (YouTube /
     * SoundCloud / generic collection). Null for user-created and system
     * playlists. Used for durable isImported / unfavorite-delete targeting.
     */
    val sourceUrl: String? = null,
)
