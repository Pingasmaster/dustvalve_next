package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.RecentTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recentTrack: RecentTrackEntity)

    @Query("SELECT * FROM recent_tracks ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<RecentTrackEntity>

    @Query(
        """
        DELETE FROM recent_tracks WHERE trackId NOT IN (
            SELECT trackId FROM recent_tracks ORDER BY playedAt DESC LIMIT :keepCount
        )
        """
    )
    suspend fun deleteOld(keepCount: Int)

    @Query("SELECT * FROM recent_tracks")
    suspend fun getAllSync(): List<RecentTrackEntity>

    @Query("SELECT * FROM recent_tracks")
    fun getAllFlow(): Flow<List<RecentTrackEntity>>

    @Query("DELETE FROM recent_tracks")
    suspend fun deleteAll()
}
