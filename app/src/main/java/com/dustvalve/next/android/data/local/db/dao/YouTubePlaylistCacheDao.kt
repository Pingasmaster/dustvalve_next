package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.YouTubePlaylistCacheEntity

@Dao
interface YouTubePlaylistCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: YouTubePlaylistCacheEntity)

    @Query("SELECT * FROM youtube_playlists WHERE playlistId = :playlistId")
    suspend fun getById(playlistId: String): YouTubePlaylistCacheEntity?
}
