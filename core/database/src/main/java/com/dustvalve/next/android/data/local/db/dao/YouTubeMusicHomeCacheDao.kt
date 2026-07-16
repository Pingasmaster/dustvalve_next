package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.YouTubeMusicHomeCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeMusicHomeCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: YouTubeMusicHomeCacheEntity)

    @Query("SELECT * FROM youtube_music_home WHERE `key` = :key")
    suspend fun getByKey(key: String): YouTubeMusicHomeCacheEntity?

    @Query("SELECT * FROM youtube_music_home")
    suspend fun getAllSync(): List<YouTubeMusicHomeCacheEntity>

    @Query("SELECT * FROM youtube_music_home")
    fun getAllFlow(): Flow<List<YouTubeMusicHomeCacheEntity>>

    @Query("DELETE FROM youtube_music_home")
    suspend fun deleteAll()
}
