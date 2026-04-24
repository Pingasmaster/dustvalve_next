package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.YouTubePlaylistCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubePlaylistCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: YouTubePlaylistCacheEntity)

    @Query("SELECT * FROM youtube_playlists WHERE playlistId = :playlistId")
    suspend fun getById(playlistId: String): YouTubePlaylistCacheEntity?

    @Query("SELECT * FROM youtube_playlists")
    suspend fun getAllSync(): List<YouTubePlaylistCacheEntity>

    @Query("SELECT * FROM youtube_playlists")
    fun getAllFlow(): Flow<List<YouTubePlaylistCacheEntity>>

    @Query("DELETE FROM youtube_playlists")
    suspend fun deleteAll()
}
