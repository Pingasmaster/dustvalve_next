package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.YouTubeVideoCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeVideoCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: YouTubeVideoCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<YouTubeVideoCacheEntity>)

    @Query("SELECT * FROM youtube_videos WHERE videoId = :videoId")
    suspend fun getById(videoId: String): YouTubeVideoCacheEntity?

    @Query("SELECT * FROM youtube_videos WHERE videoId IN (:videoIds)")
    suspend fun getByIds(videoIds: List<String>): List<YouTubeVideoCacheEntity>

    @Query("SELECT * FROM youtube_videos")
    suspend fun getAllSync(): List<YouTubeVideoCacheEntity>

    @Query("SELECT * FROM youtube_videos")
    fun getAllFlow(): Flow<List<YouTubeVideoCacheEntity>>

    @Query("DELETE FROM youtube_videos")
    suspend fun deleteAll()
}
