package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favorites",
    primaryKeys = ["id", "type"],
    indices = [Index("type", "addedAt"), Index("isPinned")],
)
data class FavoriteEntity(
    val id: String,
    val type: String, // FavoriteType.key: track / album / artist / *_playlist / collection
    val addedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val shapeKey: String? = null,
)
