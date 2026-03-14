package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_tracks",
    indices = [Index("playedAt")]
)
data class RecentTrackEntity(
    @PrimaryKey val trackId: String,
    val playedAt: Long = System.currentTimeMillis()
)
