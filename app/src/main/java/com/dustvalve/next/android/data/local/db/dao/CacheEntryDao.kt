package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.CacheEntryEntity

@Dao
interface CacheEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CacheEntryEntity)

    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    suspend fun getByKey(key: String): CacheEntryEntity?

    @Query("SELECT * FROM cache_entries")
    suspend fun getAll(): List<CacheEntryEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cache_entries")
    suspend fun getTotalSize(): Long

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cache_entries WHERE isUserDownload = 0")
    suspend fun getNonDownloadTotalSize(): Long

    @Query("SELECT * FROM cache_entries WHERE type = :type")
    suspend fun getByType(type: String): List<CacheEntryEntity>

    @Query(
        """
        SELECT * FROM cache_entries
        WHERE isUserDownload = 0
        ORDER BY lastAccessed ASC
        """
    )
    suspend fun getEvictionCandidates(): List<CacheEntryEntity>

    @Query("DELETE FROM cache_entries WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM cache_entries WHERE isUserDownload = 0")
    suspend fun deleteNonDownloads()

    @Query("UPDATE cache_entries SET lastAccessed = :timestamp WHERE `key` = :key")
    suspend fun updateLastAccessed(key: String, timestamp: Long = System.currentTimeMillis())
}
