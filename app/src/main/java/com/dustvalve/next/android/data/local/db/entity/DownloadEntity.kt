package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
)
