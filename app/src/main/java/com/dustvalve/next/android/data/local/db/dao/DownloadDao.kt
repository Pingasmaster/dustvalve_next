package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: String): DownloadEntity?

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    suspend fun getAllSync(): List<DownloadEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads")
    suspend fun getTotalSize(): Long

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads WHERE pinned = 1")
    suspend fun getPinnedSize(): Long

    @Query("SELECT trackId FROM downloads")
    fun getAllTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM downloads WHERE albumId = :albumId")
    suspend fun getByAlbumId(albumId: String): List<DownloadEntity>

    @Query("SELECT DISTINCT albumId FROM downloads")
    fun getDownloadedAlbumIds(): Flow<List<String>>

    /**
     * Eviction candidates: unpinned (auto-cached) entries, oldest first by
     * lastAccessed. Pinned (explicit user) downloads are never returned.
     */
    @Query("SELECT * FROM downloads WHERE pinned = 0 ORDER BY lastAccessed ASC")
    suspend fun getEvictionCandidates(): List<DownloadEntity>

    @Query("UPDATE downloads SET lastAccessed = :timestamp WHERE trackId = :trackId")
    suspend fun updateLastAccessed(trackId: String, timestamp: Long = System.currentTimeMillis())
}
