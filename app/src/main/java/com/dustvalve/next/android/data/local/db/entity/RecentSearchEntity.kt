package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_searches",
    primaryKeys = ["query", "source"],
    indices = [Index("searchedAt")]
)
data class RecentSearchEntity(
    val query: String,
    val source: String,
    val searchedAt: Long = System.currentTimeMillis(),
)
