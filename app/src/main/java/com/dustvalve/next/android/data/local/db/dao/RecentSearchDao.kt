package com.dustvalve.next.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recentSearch: RecentSearchEntity)

    @Query("SELECT * FROM recent_searches WHERE source = :source ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecent(source: String, limit: Int = 8): Flow<List<RecentSearchEntity>>

    @Query("DELETE FROM recent_searches WHERE `query` = :query AND source = :source")
    suspend fun delete(query: String, source: String)

    @Query("DELETE FROM recent_searches WHERE source = :source")
    suspend fun clearAll(source: String)

    @Query(
        """
        DELETE FROM recent_searches WHERE source = :source AND `query` NOT IN (
            SELECT `query` FROM recent_searches WHERE source = :source ORDER BY searchedAt DESC LIMIT :keepCount
        )
        """
    )
    suspend fun deleteOld(source: String, keepCount: Int = 20)
}
