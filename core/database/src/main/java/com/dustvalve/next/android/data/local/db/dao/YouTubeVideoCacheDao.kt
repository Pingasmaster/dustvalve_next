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

    /**
     * Bulk insert that never overwrites existing rows. Playlist / mix
     * snapshots seed entities with default `albumUrl=""` /
     * `albumLookupDone=false`; a REPLACE there would clobber rows already
     * upgraded by the single-video path, breaking the "once attempted,
     * never re-lookup" invariant. Use [insert] / [insertAll] only when the
     * incoming row is known to be at least as fresh as the stored one.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(videos: List<YouTubeVideoCacheEntity>)

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
