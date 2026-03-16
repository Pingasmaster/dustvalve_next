package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks", indices = [Index("albumId")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val title: String,
    val artist: String,
    val artistUrl: String = "",
    val trackNumber: Int,
    val duration: Float,
    val streamUrl: String?,
    val artUrl: String,
    val albumTitle: String,
    val source: String = "bandcamp",
) {
    val isLocal: Boolean get() = source == "local"
}
