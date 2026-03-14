package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "albums", indices = [Index("url")])
data class AlbumEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val artistUrl: String,
    val artUrl: String,
    val releaseDate: String?,
    val about: String?,
    val tags: String,  // JSON-serialized list
    val cachedAt: Long = System.currentTimeMillis(),
    val autoDownload: Boolean = false,
    val saleItemId: Long? = null,
    val saleItemType: String? = null,
)
