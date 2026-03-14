package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cache_entries",
    indices = [
        Index("type"),
        Index("isUserDownload", "lastAccessed"),
    ]
)
data class CacheEntryEntity(
    @PrimaryKey val key: String,
    val type: String,     // "audio", "image", "metadata"
    val sizeBytes: Long,
    val lastAccessed: Long,
    val isUserDownload: Boolean = false,
    val filePath: String?
)
