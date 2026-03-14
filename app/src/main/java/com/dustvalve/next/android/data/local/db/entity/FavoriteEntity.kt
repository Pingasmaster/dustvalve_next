package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [Index("type", "addedAt"), Index("isPinned")]
)
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val type: String,    // "album", "track", or "artist"
    val addedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val shapeKey: String? = null,
)
