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
    ]
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
)
